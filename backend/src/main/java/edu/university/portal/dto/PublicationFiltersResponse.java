package edu.university.portal.dto;

import java.util.List;

public record PublicationFiltersResponse(
        List<String> departments,
        List<Integer> years,
        List<String> faculties
) {
}
