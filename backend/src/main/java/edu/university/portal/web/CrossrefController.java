package edu.university.portal.web;

import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.service.CrossrefClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crossref")
public class CrossrefController {

    private final CrossrefClient crossrefClient;

    public CrossrefController(CrossrefClient crossrefClient) {
        this.crossrefClient = crossrefClient;
    }

    @GetMapping("/{*doi}")
    public CrossrefWork lookup(@PathVariable String doi) {
        if (doi.startsWith("/")) {
            doi = doi.substring(1);
        }
        return crossrefClient.lookup(doi);
    }
}
