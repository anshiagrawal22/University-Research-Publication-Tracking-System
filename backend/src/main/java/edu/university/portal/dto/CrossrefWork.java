package edu.university.portal.dto;

import java.util.List;

public record CrossrefWork(
        String title,
        List<String> authors,
        String journal,
        Integer year,
        String doi
) {
}
