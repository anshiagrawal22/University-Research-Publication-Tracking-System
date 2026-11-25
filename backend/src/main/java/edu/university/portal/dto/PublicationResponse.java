package edu.university.portal.dto;

import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.model.SecurityGateStatus;

import java.time.Instant;
import java.util.List;

public record PublicationResponse(
        Long id,
        String title,
        List<String> authors,
        String doi,
        Integer year,
        String department,
        String journal,
        String orcidId,
        PublicationSource source,
        PublicationStatus status,
        SecurityGateStatus gateStatus,
        String gateSummary,
        Instant gateCheckedAt,
        Instant createdAt
) {
    public PublicationResponse(
            Long id,
            String title,
            List<String> authors,
            String doi,
            Integer year,
            String department,
            String journal,
            String orcidId,
            PublicationSource source,
            PublicationStatus status,
            Instant createdAt
    ) {
        this(id, title, authors, doi, year, department, journal, orcidId, source, status, null, null, null, createdAt);
    }

    public static PublicationResponse from(Publication publication) {
        return new PublicationResponse(
                publication.getId(),
                publication.getTitle(),
                List.copyOf(publication.getAuthors()),
                publication.getDoi(),
                publication.getYear(),
                publication.getDepartment(),
                publication.getJournal(),
                publication.getOrcidId(),
                publication.getSource(),
                publication.getStatus(),
                publication.getGateStatus(),
                publication.getGateSummary(),
                publication.getGateCheckedAt(),
                publication.getCreatedAt()
        );
    }
}
