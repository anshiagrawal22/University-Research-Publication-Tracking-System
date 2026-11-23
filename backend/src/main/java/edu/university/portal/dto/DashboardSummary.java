package edu.university.portal.dto;

public record DashboardSummary(
        long totalPublications,
        long departments,
        long authors,
        long thisYear
) {
}
