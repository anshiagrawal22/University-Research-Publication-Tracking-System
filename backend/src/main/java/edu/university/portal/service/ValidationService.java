package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.repository.PublicationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ValidationService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;

    private final PublicationRepository publications;

    public ValidationService(PublicationRepository publications) {
        this.publications = publications;
    }

    public NormalizedPublication validateCreate(PublicationRequest request) {
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

        String doi = DoiNormalizer.clean(incoming.doi());
        if (doi != null && !DoiNormalizer.isValid(doi)) {
            errors.add(new FieldErrorItem("doi", "DOI must look like 10.xxxx/suffix"));
        }

        String journal = trimToNull(incoming.journal());
        if (journal != null && journal.length() > 300) {
            errors.add(new FieldErrorItem("journal", "Journal must be 300 characters or fewer"));
        }

        String orcidId = normalizeOrcid(incoming.orcidId());
        if (orcidId != null && !orcidId.matches("\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]")) {
            errors.add(new FieldErrorItem("orcidId", "ORCID iD must look like 0000-0000-0000-0000"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        if (doi != null && publications.existsByDoiIgnoreCase(doi)) {
            throw new DuplicateDoiException(doi);
        }

        PublicationSource source = incoming.source() == null ? PublicationSource.FORM : incoming.source();
        return new NormalizedPublication(title, authors, doi, year, department, journal, orcidId, source);
    }

    private List<String> normalizeAuthors(List<String> authors) {
        if (authors == null) {
            return List.of();
        }
        return authors.stream()
                .map(this::trimToNull)
                .filter(name -> name != null)
                .toList();
    }

    private String normalizeOrcid(String orcidId) {
        String value = trimToNull(orcidId);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
