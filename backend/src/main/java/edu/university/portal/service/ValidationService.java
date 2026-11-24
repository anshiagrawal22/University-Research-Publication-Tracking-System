package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.repository.PublicationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;
    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern ORCID_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]$");
    private static final String[] DOI_PREFIXES = {
            "https://dx.doi.org/",
            "http://dx.doi.org/",
            "https://doi.org/",
            "http://doi.org/",
            "doi:"
    };

    private final PublicationRepository publications;

    public ValidationService(PublicationRepository publications) {
        this.publications = publications;
    }

    // Validate required publication fields, DOI format, and duplicate DOIs before saving.
    public void validateAndApply(PublicationRequest request, Publication target, Long excludeId) {
        List<FieldErrorItem> errors = new ArrayList<>();
        PublicationRequest incoming = request == null
                ? new PublicationRequest(null, null, null, null, null, null, null, null)
                : request;

        String title = trimToNull(incoming.title());
        if (title == null) {
            errors.add(new FieldErrorItem("title", "Title is required"));
        } else if (title.length() > 500) {
            errors.add(new FieldErrorItem("title", "Title must be 500 characters or fewer"));
        }

        List<String> authors = normalizeAuthors(incoming.authors());
        if (authors.isEmpty()) {
            errors.add(new FieldErrorItem("authors", "At least one author is required"));
        } else if (authors.stream().anyMatch(author -> author.length() > 200)) {
            errors.add(new FieldErrorItem("authors", "Each author name must be 200 characters or fewer"));
        }

        Integer year = incoming.year();
        if (year == null) {
            errors.add(new FieldErrorItem("year", "Year is required"));
        } else if (year < MIN_YEAR || year > MAX_YEAR) {
            errors.add(new FieldErrorItem("year", "Year must be between 1900 and 2100"));
        }

        String department = trimToNull(incoming.department());
        if (department == null) {
            errors.add(new FieldErrorItem("department", "Department is required"));
        } else if (department.length() > 160) {
            errors.add(new FieldErrorItem("department", "Department must be 160 characters or fewer"));
        }

        String doi = cleanDoi(incoming.doi());
        if (doi != null && !isValidDoi(doi)) {
            errors.add(new FieldErrorItem("doi", "DOI must look like 10.xxxx/suffix"));
        }

        String journal = trimToNull(incoming.journal());
        if (journal != null && journal.length() > 300) {
            errors.add(new FieldErrorItem("journal", "Journal must be 300 characters or fewer"));
        }

        String orcidId = trimToNull(incoming.orcidId());
        if (orcidId != null) {
            orcidId = orcidId.toUpperCase(Locale.ROOT);
            if (!ORCID_PATTERN.matcher(orcidId).matches()) {
                errors.add(new FieldErrorItem("orcidId", "ORCID iD must look like 0000-0000-0000-0000"));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // Prevent duplicate publications using normalized DOI.
        if (doi != null) {
            boolean duplicate = excludeId == null
                    ? publications.existsByDoiIgnoreCase(doi)
                    : publications.existsByDoiIgnoreCaseAndIdNot(doi, excludeId);
            if (duplicate) {
                throw new DuplicateDoiException(doi);
            }
        }

        target.setTitle(title);
        target.setAuthors(new ArrayList<>(authors));
        target.setDoi(doi);
        target.setYear(year);
        target.setDepartment(department);
        target.setJournal(journal);
        target.setOrcidId(orcidId);
        if (target.getSource() == null) {
            target.setSource(incoming.source() == null ? PublicationSource.FORM : incoming.source());
        }
    }

    public static String cleanDoi(String raw) {
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
            for (String prefix : DOI_PREFIXES) {
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

    public static boolean isValidDoi(String cleanedDoi) {
        return cleanedDoi != null && DOI_PATTERN.matcher(cleanedDoi).matches();
    }

    private List<String> normalizeAuthors(List<String> authors) {
        if (authors == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : authors) {
            String trimmed = trimToNull(name);
            if (trimmed != null) {
                cleaned.add(trimmed);
            }
        }
        return cleaned;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
