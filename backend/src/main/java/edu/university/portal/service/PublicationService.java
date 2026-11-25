package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PageResponse;
import edu.university.portal.dto.PublicationFiltersResponse;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.SecurityGateReport;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.ResourceNotFoundException;
import edu.university.portal.exception.SecurityGateException;
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
    private final SecurityGateService securityGateService;

    public PublicationService(
            PublicationRepository publications,
            ValidationService validationService,
            SecurityGateService securityGateService
    ) {
        this.publications = publications;
        this.validationService = validationService;
        this.securityGateService = securityGateService;
    }

    // Validate fields, run security gates, and stage new submissions before they go live.
    @Transactional
    public PublicationResponse create(PublicationRequest request) {
        Publication publication = new Publication();
        validationService.validateAndApply(request, publication, null);
        securityGateService.assertInputSafe(publication);

        SecurityGateReport report = securityGateService.evaluate(publication, null);
        securityGateService.applyReport(publication, report);
        publication.setStatus(report.publishAllowed() ? PublicationStatus.IN_REVIEW : PublicationStatus.REJECTED);
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
        securityGateService.assertInputSafe(existing);

        SecurityGateReport report = securityGateService.evaluate(existing, id);
        securityGateService.applyReport(existing, report);

        if (request != null && request.status() != null) {
            if (request.status() == PublicationStatus.VERIFIED && !report.publishAllowed()) {
                throw new SecurityGateException(
                        "Security gate blocked publication from going live: " + report.summary(),
                        report,
                        List.of(new FieldErrorItem("status", report.summary()))
                );
            }
            existing.setStatus(request.status());
        } else if (!report.publishAllowed()) {
            existing.setStatus(PublicationStatus.REJECTED);
        }

        try {
            return PublicationResponse.from(publications.save(existing));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateDoiException(existing.getDoi());
        }
    }

    // Run all security gates on an existing record and return the detailed inspection report.
    @Transactional
    public SecurityGateReport evaluateSecurityGate(Long id) {
        Publication existing = publications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication " + id + " was not found"));
        SecurityGateReport report = securityGateService.evaluate(existing, id);
        securityGateService.applyReport(existing, report);
        publications.save(existing);
        return report;
    }

    // Gate-protected publish transition: only papers passing all security gates can go live.
    @Transactional(noRollbackFor = SecurityGateException.class)
    public PublicationResponse publish(Long id) {
        Publication existing = publications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Publication " + id + " was not found"));

        SecurityGateReport report = securityGateService.evaluate(existing, id);
        securityGateService.applyReport(existing, report);

        if (!report.publishAllowed()) {
            existing.setStatus(PublicationStatus.REJECTED);
            publications.save(existing);
            throw new SecurityGateException(
                    "Security gate blocked publication from going live: " + report.summary(),
                    report,
                    List.of(new FieldErrorItem("status", report.summary()))
            );
        }

        existing.setStatus(PublicationStatus.VERIFIED);
        return PublicationResponse.from(publications.save(existing));
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
