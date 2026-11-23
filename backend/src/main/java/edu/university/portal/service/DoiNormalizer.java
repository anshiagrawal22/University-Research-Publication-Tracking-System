package edu.university.portal.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DoiNormalizer {

    public static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$");

    private static final String[] PREFIXES = {
            "https://dx.doi.org/",
            "http://dx.doi.org/",
            "https://doi.org/",
            "http://doi.org/",
            "doi:"
    };

    private DoiNormalizer() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String doi = raw.trim().toLowerCase(Locale.ROOT);
        if (doi.isEmpty()) {
            return null;
        }
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String prefix : PREFIXES) {
                if (doi.startsWith(prefix)) {
                    doi = doi.substring(prefix.length()).trim();
                    stripped = true;
                    break;
                }
            }
        }
        while (doi.endsWith("/")) {
            doi = doi.substring(0, doi.length() - 1).trim();
        }
        return doi.isEmpty() ? null : doi;
    }

    public static boolean isValid(String cleanedDoi) {
        return cleanedDoi != null && DOI_PATTERN.matcher(cleanedDoi).matches();
    }
}
