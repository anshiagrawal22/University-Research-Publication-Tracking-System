package edu.university.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.exception.CrossrefUnavailableException;

import java.util.ArrayList;
import java.util.List;

public class CrossrefMapper {

    private final ObjectMapper objectMapper;

    public CrossrefMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CrossrefWork map(String json, String requestedDoi) {
        if (json == null || json.isBlank()) {
            throw new CrossrefUnavailableException("Crossref returned an empty response", 502);
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new CrossrefUnavailableException("Crossref returned an unreadable response", 502);
        }
        JsonNode message = root.path("message");
        if (message.isMissingNode() || message.isNull()) {
            return new CrossrefWork(null, List.of(), null, null, requestedDoi);
        }

        String title = textValue(message.get("title"));
        String journal = textValue(message.get("container-title"));
        if (journal == null) {
            journal = textValue(message.get("short-container-title"));
        }
        Integer year = yearOf(message);
        String doi = textOrNull(message.get("DOI"));
        if (doi == null) {
            doi = requestedDoi;
        } else {
            doi = DoiNormalizer.clean(doi);
        }
        return new CrossrefWork(title, authorsOf(message.get("author")), journal, year, doi);
    }

    private List<String> authorsOf(JsonNode authors) {
        if (authors == null || !authors.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode author : authors) {
            String name = authorName(author);
            if (name != null) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private String authorName(JsonNode author) {
        if (author == null || author.isNull()) {
            return null;
        }
        String literal = textOrNull(author.get("name"));
        if (literal != null) {
            return literal;
        }
        String given = textOrNull(author.get("given"));
        String family = textOrNull(author.get("family"));
        if (given == null && family == null) {
            return null;
        }
        if (given == null) {
            return family;
        }
        if (family == null) {
            return given;
        }
        return (given + " " + family).trim();
    }

    private Integer yearOf(JsonNode message) {
        Integer issued = dateYear(message.get("issued"));
        if (issued != null) {
            return issued;
        }
        Integer printYear = dateYear(message.get("published-print"));
        if (printYear != null) {
            return printYear;
        }
        return dateYear(message.get("published-online"));
    }

    private Integer dateYear(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        JsonNode parts = node.get("date-parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            return null;
        }
        JsonNode first = parts.get(0);
        if (first == null || !first.isArray() || first.isEmpty()) {
            return null;
        }
        JsonNode year = first.get(0);
        if (year == null || year.isNull()) {
            return null;
        }
        if (year.isNumber()) {
            return year.asInt();
        }
        if (year.isTextual()) {
            try {
                return Integer.valueOf(year.asText());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = textOrNull(item);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return blankToNull(node.asText());
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
