package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PageResponse;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.ResourceNotFoundException;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.repository.PublicationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publications;
    private final ValidationService validationService;
    private final SimilarityReviewService similarityReviewService;

    public PublicationService(
            PublicationRepository publications,
            ValidationService validationService,
            SimilarityReviewService similarityReviewService
    ) {
        this.publications = publications;
        this.validationService = validationService;
        this.similarityReviewService = similarityReviewService;
    }

    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        NormalizedPublication normalized = validationService.validateCreate(request);
        PublicationStatus status = similarityReviewService
                .findSimilar(normalized.title(), normalized.year(), normalized.doi())
                .map(match -> PublicationStatus.IN_REVIEW)
                .orElse(PublicationStatus.VERIFIED);

        Publication publication = new Publication();
        publication.setTitle(normalized.title());
        publication.setAuthors(new ArrayList<>(normalized.authors()));
        publication.setDoi(normalized.doi());
        publication.setYear(normalized.year());
        publication.setDepartment(normalized.department());
        publication.setJournal(normalized.journal());
        publication.setOrcidId(normalized.orcidId());
        publication.setSource(normalized.source());
        publication.setStatus(status);
        publication.setCreatedAt(Instant.now());

        try {
            return PublicationResponse.from(publications.save(publication));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDoiException(normalized.doi());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> list(
            String search,
            String department,
            Integer year,
            String journal,
            PublicationStatus status,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ValidationException(List.of(new FieldErrorItem("page", "Page must be zero or greater")));
        }
        if (size < 1 || size > 100) {
            throw new ValidationException(List.of(new FieldErrorItem("size", "Size must be between 1 and 100")));
        }

        String searchTerm = blankToEmpty(search);
        String departmentTerm = blankToEmpty(department);
        String journalTerm = blankToEmpty(journal);
        Page<Publication> result = publications.search(
                status != null,
                status == null ? PublicationStatus.VERIFIED : status,
                !departmentTerm.isEmpty(),
                departmentTerm,
                year != null,
                year == null ? 0 : year,
                !journalTerm.isEmpty(),
                journalTerm,
                !searchTerm.isEmpty(),
                searchTerm,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<PublicationResponse> content = result.getContent().stream().map(PublicationResponse::from).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PublicationResponse get(Long id) {
        return publications.findById(id)
                .map(PublicationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Publication " + id + " was not found"));
    }

    private String blankToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
