package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PageResponse;
import edu.university.portal.dto.PublicationFiltersResponse;
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
import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publications;
    private final ValidationService validationService;

    public PublicationService(PublicationRepository publications, ValidationService validationService) {
        this.publications = publications;
        this.validationService = validationService;
    }

    // Save the publication after validating required fields and checking for duplicate DOIs.
    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        Publication publication = new Publication();
        validationService.validateAndApply(request, publication, null);
        publication.setStatus(PublicationStatus.VERIFIED);
        publication.setCreatedAt(Instant.now());

        try {
            return PublicationResponse.from(publications.save(publication));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDoiException(publication.getDoi());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicationResponse> list(
            String search,
            String department,
            String faculty,
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

        String searchTerm = trimOrEmpty(search);
        String departmentTerm = trimOrEmpty(department);
        String facultyTerm = trimOrEmpty(faculty);
        String journalTerm = trimOrEmpty(journal);

        Page<Publication> result = publications.search(
                status != null,
                status == null ? PublicationStatus.VERIFIED : status,
                !departmentTerm.isEmpty(),
                departmentTerm,
                !facultyTerm.isEmpty(),
                facultyTerm,
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

    @Transactional
    public PublicationResponse update(Long id, PublicationRequest request) {
        Publication existing = publications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication " + id + " was not found"));

        validationService.validateAndApply(request, existing, id);
        if (request != null && request.status() != null) {
            existing.setStatus(request.status());
        }

        try {
            return PublicationResponse.from(publications.save(existing));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDoiException(existing.getDoi());
        }
    }

    @Transactional
    public void delete(Long id) {
        Publication existing = publications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication " + id + " was not found"));
        publications.delete(existing);
    }

    @Transactional(readOnly = true)
    public PublicationFiltersResponse filters() {
        return new PublicationFiltersResponse(
                publications.findDistinctDepartments(),
                publications.findDistinctYears(),
                publications.findDistinctAuthors()
        );
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
