package edu.university.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.dto.OrcidWork;
import edu.university.portal.exception.ExternalApiException;
import edu.university.portal.repository.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrcidService {

    private static final Logger log = LoggerFactory.getLogger(OrcidService.class);
    private static final String USER_AGENT = "UniversityPublicationPortal/0.2 (mailto:research-office@university.edu)";

    private final RestTemplate restTemplate;
    private final CrossrefService crossrefService;
    private final PublicationRepository publications;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final int maxCrossrefEnrichments;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public OrcidService(
            RestTemplate orcidRestTemplate,
            CrossrefService crossrefService,
            PublicationRepository publications,
            ObjectMapper objectMapper,
            @Value("${app.orcid.base-url:https://pub.orcid.org/v3.0}") String baseUrl,
            @Value("${app.orcid.token-url:https://orcid.org/oauth/token}") String tokenUrl,
            @Value("${app.orcid.client-id:}") String clientId,
            @Value("${app.orcid.client-secret:}") String clientSecret,
            @Value("${app.orcid.max-crossref-enrichments:20}") int maxCrossrefEnrichments
    ) {
        this.restTemplate = orcidRestTemplate;
        this.crossrefService = crossrefService;
        this.publications = publications;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.maxCrossrefEnrichments = Math.max(0, maxCrossrefEnrichments);
    }

    // Fetch public works for an ORCID iD and enrich DOI entries with Crossref metadata.
    public List<OrcidWork> fetchWorks(String orcidId) {
        HttpHeaders headers = buildHeaders();
        String worksJson = fetchJson(baseUrl + "/{orcidId}/works", orcidId, headers);
        List<OrcidSummary> summaries = parseWorks(worksJson);
        if (summaries.isEmpty()) {
            return List.of();
        }

        // Fall back to the researcher's ORCID name when Crossref metadata is not available.
        String researcherName = fetchResearcherName(orcidId, headers);
        List<String> fallbackAuthors = researcherName == null ? List.of() : List.of(researcherName);

        List<OrcidWork> results = new ArrayList<>(summaries.size());
        int enrichRemaining = maxCrossrefEnrichments;

        for (OrcidSummary item : summaries) {
            CrossrefWork crossref = null;
            if (item.doi() != null && enrichRemaining > 0) {
                enrichRemaining--;
                try {
                    crossref = crossrefService.fetchByDoi(item.doi());
                } catch (Exception ex) {
                    log.debug("Crossref enrichment skipped for DOI {}: {}", item.doi(), ex.getMessage());
                }
            }

            String doi = crossref != null && crossref.doi() != null ? crossref.doi() : item.doi();
            String title = crossref != null && crossref.title() != null ? crossref.title() : item.title();
            String journal = crossref != null && crossref.journal() != null ? crossref.journal() : item.journal();
            Integer year = crossref != null && crossref.year() != null ? crossref.year() : item.year();
            List<String> authors = crossref != null && !crossref.authors().isEmpty()
                    ? crossref.authors()
                    : fallbackAuthors;

            // Flag DOIs already stored in the database so duplicates are easy to spot.
            boolean alreadyExists = doi != null && publications.existsByDoiIgnoreCase(doi);
            results.add(new OrcidWork(title, authors, journal, year, doi, orcidId, crossref != null, alreadyExists));
        }

        return results;
    }

    private String fetchJson(String url, String orcidId, HttpHeaders headers) {
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class, orcidId).getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ExternalApiException("No ORCID record was found for " + orcidId, 404);
            }
            throw new ExternalApiException("ORCID returned HTTP " + ex.getStatusCode().value(), 502);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException("ORCID did not respond in time", 504);
        }
    }

    private String fetchResearcherName(String orcidId, HttpHeaders headers) {
        try {
            String personJson = restTemplate.exchange(
                    baseUrl + "/{orcidId}/person", HttpMethod.GET, new HttpEntity<>(headers), String.class, orcidId
            ).getBody();
            if (personJson == null || personJson.isBlank()) {
                return null;
            }
            JsonNode nameNode = objectMapper.readTree(personJson).path("name");
            String credit = cleanText(nameNode.path("credit-name").path("value").asText(null));
            if (credit != null) {
                return credit;
            }
            String given = cleanText(nameNode.path("given-names").path("value").asText(null));
            String family = cleanText(nameNode.path("family-name").path("value").asText(null));
            if (given != null && family != null) {
                return given + " " + family;
            }
            return given != null ? given : family;
        } catch (Exception ex) {
            return null;
        }
    }

    private List<OrcidSummary> parseWorks(String json) {
        if (json == null || json.isBlank()) {
            throw new ExternalApiException("ORCID returned an empty response", 502);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ExternalApiException("ORCID returned an unreadable response", 502);
        }

        JsonNode groups = root.path("group");
        if (!groups.isArray() || groups.isEmpty()) {
            return List.of();
        }

        Map<String, OrcidSummary> unique = new LinkedHashMap<>();
        for (JsonNode group : groups) {
            JsonNode summaries = group.path("work-summary");
            if (!summaries.isArray() || summaries.isEmpty()) {
                continue;
            }
            JsonNode primary = summaries.get(0);
            for (JsonNode candidate : summaries) {
                if ("0".equals(candidate.path("display-index").asText())) {
                    primary = candidate;
                    break;
                }
            }

            String title = cleanText(primary.path("title").path("title").path("value").asText(null));
            String journal = cleanText(primary.path("journal-title").path("value").asText(null));
            Integer year = parseYear(primary.path("publication-date").path("year").path("value").asText(null));
            String doi = extractDoi(group, summaries);

            if (title == null && doi == null) {
                continue;
            }
            String key = doi != null
                    ? "doi:" + doi
                    : "title:" + (title == null ? "" : title.toLowerCase(Locale.ROOT)) + ":" + (year == null ? "" : year);
            unique.putIfAbsent(key, new OrcidSummary(title, journal, year, doi));
        }
        return List.copyOf(unique.values());
    }

    private String extractDoi(JsonNode group, JsonNode summaries) {
        List<JsonNode> idLists = new ArrayList<>();
        idLists.add(group.path("external-ids").path("external-id"));
        for (JsonNode summary : summaries) {
            idLists.add(summary.path("external-ids").path("external-id"));
        }
        String fallback = null;
        for (JsonNode ids : idLists) {
            if (!ids.isArray()) {
                continue;
            }
            for (JsonNode id : ids) {
                if (!"doi".equalsIgnoreCase(id.path("external-id-type").asText())) {
                    continue;
                }
                String raw = id.path("external-id-normalized").path("value").asText(null);
                if (raw == null || raw.isBlank()) {
                    raw = id.path("external-id-value").asText(null);
                }
                String cleaned = ValidationService.cleanDoi(raw);
                if (!ValidationService.isValidDoi(cleaned)) {
                    continue;
                }
                if ("self".equalsIgnoreCase(id.path("external-id-relationship").asText())) {
                    return cleaned;
                }
                if (fallback == null) {
                    fallback = cleaned;
                }
            }
        }
        return fallback;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String token = resolveAccessToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String resolveAccessToken() {
        // Use public unauthenticated reads when ORCID OAuth credentials are not configured.
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return null;
        }
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
                return cachedToken;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("grant_type", "client_credentials");
            form.add("scope", "/read-public");

            try {
                String body = restTemplate.postForObject(tokenUrl, new HttpEntity<>(form, headers), String.class);
                JsonNode root = objectMapper.readTree(body);
                String token = root.path("access_token").asText(null);
                long expiresIn = root.path("expires_in").asLong(3600);
                if (token == null || token.isBlank()) {
                    throw new ExternalApiException("ORCID authentication did not return a token", 502);
                }
                cachedToken = token;
                tokenExpiresAt = Instant.now().plusSeconds(Math.max(120, expiresIn));
                return cachedToken;
            } catch (ExternalApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExternalApiException("ORCID authentication failed", 502);
            }
        }
    }

    private Integer parseYear(String raw) {
        String trimmed = cleanText(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private record OrcidSummary(String title, String journal, Integer year, String doi) {
    }
}
