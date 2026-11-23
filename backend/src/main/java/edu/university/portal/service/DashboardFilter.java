package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.exception.ValidationException;

import java.util.List;

final class DashboardFilter {

    private final boolean applyDepartment;
    private final String department;
    private final boolean applyFrom;
    private final int fromYear;
    private final boolean applyTo;
    private final int toYear;

    private DashboardFilter(
            boolean applyDepartment,
            String department,
            boolean applyFrom,
            int fromYear,
            boolean applyTo,
            int toYear
    ) {
        this.applyDepartment = applyDepartment;
        this.department = department;
        this.applyFrom = applyFrom;
        this.fromYear = fromYear;
        this.applyTo = applyTo;
        this.toYear = toYear;
    }

    static DashboardFilter of(String department, Integer fromYear, Integer toYear) {
        if (fromYear != null && toYear != null && fromYear > toYear) {
            throw new ValidationException(List.of(
                    new FieldErrorItem("fromYear", "From year cannot be later than to year")
            ));
        }
        boolean applyDepartment = department != null && !department.isBlank();
        return new DashboardFilter(
                applyDepartment,
                applyDepartment ? department.trim() : "",
                fromYear != null,
                fromYear == null ? 0 : fromYear,
                toYear != null,
                toYear == null ? 0 : toYear
        );
    }

    boolean applyDepartment() {
        return applyDepartment;
    }

    String department() {
        return department;
    }

    boolean applyFrom() {
        return applyFrom;
    }

    int fromYear() {
        return fromYear;
    }

    boolean applyTo() {
        return applyTo;
    }

    int toYear() {
        return toYear;
    }

    boolean includes(int year) {
        if (applyFrom && year < fromYear) {
            return false;
        }
        return !applyTo || year <= toYear;
    }
}
