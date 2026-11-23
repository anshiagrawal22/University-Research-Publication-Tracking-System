package edu.university.portal.exception;

public class CrossrefNotFoundException extends RuntimeException {

    public CrossrefNotFoundException(String doi) {
        super("No Crossref record was found for DOI " + doi);
    }
}
