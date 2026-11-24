package edu.university.portal.controller;

import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.service.CrossrefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crossref")
public class CrossrefController {

    private final CrossrefService crossrefService;

    public CrossrefController(CrossrefService crossrefService) {
        this.crossrefService = crossrefService;
    }

    @GetMapping("/{*doi}")
    public CrossrefWork lookup(@PathVariable String doi) {
        if (doi.startsWith("/")) {
            doi = doi.substring(1);
        }
        return crossrefService.fetchByDoi(doi);
    }
}
