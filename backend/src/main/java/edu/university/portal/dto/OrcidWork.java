package edu.university.portal.dto;

import java.util.List;

public record OrcidWork(
        String title,
        List<String> authors,
        String journal,
        Integer year,
        String doi,
        String orcidId,
        boolean enrichedFromCrossref,
        boolean alreadyExists
) {
}
