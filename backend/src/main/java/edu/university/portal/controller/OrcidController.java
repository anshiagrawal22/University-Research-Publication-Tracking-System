package edu.university.portal.controller;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.OrcidWork;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.service.OrcidService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/orcid")
public class OrcidController {

    private static final String ORCID_PATTERN = "\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]";

    private final OrcidService orcidService;

    public OrcidController(OrcidService orcidService) {
        this.orcidService = orcidService;
    }

    @GetMapping("/{orcidId}/works")
    public List<OrcidWork> works(@PathVariable String orcidId) {
        String normalized = orcidId.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches(ORCID_PATTERN)) {
            throw new ValidationException(List.of(
                    new FieldErrorItem("orcidId", "ORCID iD must look like 0000-0000-0000-0000")
            ));
        }
        return orcidService.fetchWorks(normalized);
    }
}
