package edu.university.portal.web;

import edu.university.portal.dto.PageResponse;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.service.PublicationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@RequestBody PublicationRequest request) {
        return publicationService.create(request);
    }

    @GetMapping
    public PageResponse<PublicationResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String journal,
            @RequestParam(required = false) PublicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return publicationService.list(search, department, year, journal, status, page, size);
    }

    @GetMapping("/{id}")
    public PublicationResponse get(@PathVariable Long id) {
        return publicationService.get(id);
    }
}
