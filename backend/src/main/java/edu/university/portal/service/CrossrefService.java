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

        for (int attempt = 1; ; attempt++) {
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
                // Crossref allows only a few requests at once per client and answers 429 when we go over.
                // That's temporary, so wait a moment and try again before giving up.
                if (ex.getStatusCode().value() == 429 && attempt < MAX_ATTEMPTS) {
                    pauseBeforeRetry(ex);
                    continue;
                }
                throw toApiException(ex, doi);
            } catch (ResourceAccessException ex) {
                throw toApiException(ex);
            }
        }
    }

    private static final int MAX_ATTEMPTS = 3;

    private void pauseBeforeRetry(HttpStatusCodeException ex) {
        long waitMs = 700;
        String retryAfter = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                waitMs = Math.min(2000, Math.max(200, Long.parseLong(retryAfter.trim()) * 1000));
            } catch (NumberFormatException ignored) {
                // Retry-After can also be a date; the default pause is fine then.
            }
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("Crossref lookup was cancelled.", 503);
        }
    }

    private ExternalApiException toApiException(HttpStatusCodeException ex, String doi) {
        int status = ex.getStatusCode().value();
        if (status == 404) {
            return new ExternalApiException("No Crossref record was found for DOI " + doi, 404);
        }
        if (status == 429) {
            return new ExternalApiException("Crossref is busy right now. Please wait a moment and try again.", 503);
        }
        return new ExternalApiException("Crossref is having problems right now (error " + status
                + "). Try again in a few minutes, or enter the details by hand.", 502);
    }

    private ExternalApiException toApiException(ResourceAccessException ex) {
        if (isTimeout(ex)) {
            return new ExternalApiException("Crossref did not respond in time. Try again, or enter the details by hand.", 504);
        }
        return new ExternalApiException("Could not connect to Crossref. Check that the server has internet access, then try again.", 503);
    }

    // A read/connect timeout means Crossref is slow; anything else (refused, DNS, proxy) means we never reached it.
    static boolean isTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private CrossrefWork parseWork(String json, String requestedDoi) {
        if (json == null || json.isBlank()) {
            throw new ExternalApiException("Crossref sent back an empty response. Please try again later.", 502);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ExternalApiException("Crossref sent back a response we couldn't read. Please try again later.", 502);
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
