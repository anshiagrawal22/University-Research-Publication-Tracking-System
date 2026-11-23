package edu.university.portal.dto;

import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;

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
        Instant createdAt
) {
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
                publication.getCreatedAt()
        );
    }
}
