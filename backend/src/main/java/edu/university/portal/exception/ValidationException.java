package edu.university.portal.exception;

import edu.university.portal.dto.FieldErrorItem;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<FieldErrorItem> fieldErrors;

    public ValidationException(List<FieldErrorItem> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }
}
