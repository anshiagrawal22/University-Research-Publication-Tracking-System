package edu.university.portal.exception;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.SecurityGateReport;

import java.util.List;

public class SecurityGateException extends RuntimeException {

    private final SecurityGateReport report;
    private final List<FieldErrorItem> fieldErrors;

    public SecurityGateException(String message, SecurityGateReport report, List<FieldErrorItem> fieldErrors) {
        super(message);
        this.report = report;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public SecurityGateReport getReport() {
        return report;
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }
}
