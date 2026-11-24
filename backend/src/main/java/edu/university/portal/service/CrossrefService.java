package edu.university.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.exception.ExternalApiException;
import edu.university.portal.exception.ValidationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class CrossrefService {

    static final String USER_AGENT = "UniversityPublicationPortal/0.2 (mailto:research-office@university.edu)";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CrossrefService(RestTemplate crossrefRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = crossrefRestTemplate;
        this.objectMapper = objectMapper;
    }

    // Fetch publication details from Crossref using the normalized DOI.
    public CrossrefWork fetchByDoi(String rawDoi) {
        String doi = ValidationService.cleanDoi(rawDoi);
        if (!ValidationService.isValidDoi(doi)) {
            throw new ValidationException(List.of(new FieldErrorItem("doi", "DOI must look like 10.xxxx/suffix")));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            String body = restTemplate.exchange(
                    "https://api.crossref.org/works/{doi}",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class,
                    doi
            ).getBody();
            return parseWork(body, doi);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ExternalApiException("No Crossref record was found for DOI " + doi, 404);
            }
            throw new ExternalApiException("Crossref returned HTTP " + ex.getStatusCode().value(), 502);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException("Crossref did not respond in time", 504);
        }
    }

    private CrossrefWork parseWork(String json, String requestedDoi) {
        if (json == null || json.isBlank()) {
            throw new ExternalApiException("Crossref returned an empty response", 502);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ExternalApiException("Crossref returned an unreadable response", 502);
        }

        JsonNode message = root.path("message");
        if (message.isMissingNode() || message.isNull()) {
            return new CrossrefWork(null, List.of(), null, null, requestedDoi);
        }

        String title = cleanText(firstArrayOrText(message.get("title")));
        String journal = cleanText(firstArrayOrText(message.get("container-title")));
        if (journal == null) {
            journal = cleanText(firstArrayOrText(message.get("short-container-title")));
        }
        Integer year = extractYear(message);
        String doi = textOrNull(message.get("DOI"));
        doi = doi == null ? requestedDoi : ValidationService.cleanDoi(doi);

        return new CrossrefWork(title, extractAuthors(message.get("author")), journal, year, doi);
    }

    private List<String> extractAuthors(JsonNode authors) {
        if (authors == null || !authors.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode author : authors) {
            String literal = textOrNull(author.get("name"));
            if (literal != null) {
                names.add(literal);
                continue;
            }
            String given = textOrNull(author.get("given"));
            String family = textOrNull(author.get("family"));
            if (given != null && family != null) {
                names.add((given + " " + family).trim());
            } else if (family != null) {
                names.add(family);
            } else if (given != null) {
                names.add(given);
            }
        }
        return List.copyOf(names);
    }

    private Integer extractYear(JsonNode message) {
        for (String key : List.of("issued", "published-print", "published-online", "published", "created")) {
            JsonNode parts = message.path(key).path("date-parts");
            if (parts.isArray() && !parts.isEmpty() && parts.get(0).isArray() && !parts.get(0).isEmpty()) {
                JsonNode yearNode = parts.get(0).get(0);
                if (yearNode.isNumber()) {
                    return yearNode.asInt();
                }
                if (yearNode.isTextual()) {
                    try {
                        return Integer.valueOf(yearNode.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // Try next date field
                    }
                }
            }
        }
        return null;
    }

    private String firstArrayOrText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return textOrNull(node);
        }
        if (node.isArray() && !node.isEmpty()) {
            return textOrNull(node.get(0));
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String trimmed = node.asText().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
