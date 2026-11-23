package edu.university.portal.dto;

import edu.university.portal.model.PublicationSource;

import java.util.List;

public record PublicationRequest(
        String title,
        List<String> authors,
        String doi,
        Integer year,
        String department,
        String journal,
        String orcidId,
        PublicationSource source
) {
}
