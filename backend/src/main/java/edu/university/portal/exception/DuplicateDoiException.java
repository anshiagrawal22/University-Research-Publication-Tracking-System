package edu.university.portal.exception;

public class DuplicateDoiException extends RuntimeException {

    private final String doi;

    public DuplicateDoiException(String doi) {
        super("A publication with this DOI already exists");
        this.doi = doi;
    }

    public String getDoi() {
        return doi;
    }
}
