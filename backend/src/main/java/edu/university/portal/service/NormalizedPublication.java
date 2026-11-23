package edu.university.portal.service;

import edu.university.portal.model.PublicationSource;

import java.util.List;

public record NormalizedPublication(
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
