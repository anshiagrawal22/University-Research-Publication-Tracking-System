package edu.university.portal.dto;

import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;

import java.util.List;

public record PublicationRequest(
        String title,
        List<String> authors,
        String doi,
        Integer year,
        String department,
        String journal,
        String orcidId,
        PublicationSource source,
        PublicationStatus status
) {
    public PublicationRequest(
            String title,
            List<String> authors,
            String doi,
            Integer year,
            String department,
            String journal,
            String orcidId,
            PublicationSource source
    ) {
        this(title, authors, doi, year, department, journal, orcidId, source, null);
    }
}
