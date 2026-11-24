package edu.university.portal.service;

import edu.university.portal.dto.DashboardSummary;
import edu.university.portal.dto.DepartmentCount;
import edu.university.portal.dto.FacultyCount;
import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.YearCount;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.repository.PublicationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardService {

    private static final int TOP_FACULTY = 10;
    private static final int RECENT_LIMIT = 10;

    private final PublicationRepository publications;

    public DashboardService(PublicationRepository publications) {
        this.publications = publications;
    }

    // Retrieve verified publications and summary totals for the dashboard.
    @Transactional(readOnly = true)
    public DashboardSummary summary(String department, String faculty, Integer fromYear, Integer toYear) {
        validateYearRange(fromYear, toYear);
        String dept = trimOrEmpty(department);
        String fac = trimOrEmpty(faculty);
        int from = fromYear == null ? 0 : fromYear;
        int to = toYear == null ? 0 : toYear;

        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        boolean includesCurrentYear = (fromYear == null || currentYear >= fromYear) && (toYear == null || currentYear <= toYear);
        long thisYear = includesCurrentYear
                ? publications.countVerified(!dept.isEmpty(), dept, !fac.isEmpty(), fac, true, currentYear, true, currentYear)
                : 0L;

        return new DashboardSummary(
                publications.countVerified(!dept.isEmpty(), dept, !fac.isEmpty(), fac, fromYear != null, from, toYear != null, to),
                publications.countVerifiedDepartments(!dept.isEmpty(), dept, !fac.isEmpty(), fac, fromYear != null, from, toYear != null, to),
                publications.countVerifiedAuthors(!dept.isEmpty(), dept, !fac.isEmpty(), fac, fromYear != null, from, toYear != null, to),
                thisYear
        );
    }

    @Transactional(readOnly = true)
    public List<DepartmentCount> byDepartment(String department, String faculty, Integer fromYear, Integer toYear) {
        validateYearRange(fromYear, toYear);
        String dept = trimOrEmpty(department);
        String fac = trimOrEmpty(faculty);
        return publications.countVerifiedByDepartment(
                        !dept.isEmpty(), dept, !fac.isEmpty(), fac,
                        fromYear != null, fromYear == null ? 0 : fromYear,
                        toYear != null, toYear == null ? 0 : toYear)
                .stream()
                .map(row -> new DepartmentCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<YearCount> byYear(String department, String faculty, Integer fromYear, Integer toYear) {
        validateYearRange(fromYear, toYear);
        String dept = trimOrEmpty(department);
        String fac = trimOrEmpty(faculty);
        return publications.countVerifiedByYear(
                        !dept.isEmpty(), dept, !fac.isEmpty(), fac,
                        fromYear != null, fromYear == null ? 0 : fromYear,
                        toYear != null, toYear == null ? 0 : toYear)
                .stream()
                .map(row -> new YearCount(((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FacultyCount> byFaculty(String department, String faculty, Integer fromYear, Integer toYear) {
        validateYearRange(fromYear, toYear);
        String dept = trimOrEmpty(department);
        String fac = trimOrEmpty(faculty);
        return publications.countVerifiedByAuthor(
                        !dept.isEmpty(), dept, !fac.isEmpty(), fac,
                        fromYear != null, fromYear == null ? 0 : fromYear,
                        toYear != null, toYear == null ? 0 : toYear)
                .stream()
                .limit(TOP_FACULTY)
                .map(row -> new FacultyCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicationResponse> recent(String department, String faculty, Integer fromYear, Integer toYear) {
        validateYearRange(fromYear, toYear);
        String dept = trimOrEmpty(department);
        String fac = trimOrEmpty(faculty);
        return publications.findRecentVerified(
                        !dept.isEmpty(), dept, !fac.isEmpty(), fac,
                        fromYear != null, fromYear == null ? 0 : fromYear,
                        toYear != null, toYear == null ? 0 : toYear,
                        PageRequest.of(0, RECENT_LIMIT))
                .stream()
                .map(PublicationResponse::from)
                .toList();
    }

    private void validateYearRange(Integer fromYear, Integer toYear) {
        if (fromYear != null && toYear != null && fromYear > toYear) {
            throw new ValidationException(List.of(
                    new FieldErrorItem("fromYear", "From year cannot be later than to year")
            ));
        }
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
