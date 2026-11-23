package edu.university.portal.service;

import edu.university.portal.dto.DashboardSummary;
import edu.university.portal.dto.DepartmentCount;
import edu.university.portal.dto.FacultyCount;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.YearCount;
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

    @Transactional(readOnly = true)
    public DashboardSummary summary(String department, Integer fromYear, Integer toYear) {
        DashboardFilter filter = DashboardFilter.of(department, fromYear, toYear);
        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        long thisYear = filter.includes(currentYear)
                ? publications.countVerified(filter.applyDepartment(), filter.department(), true, currentYear, true, currentYear)
                : 0L;
        return new DashboardSummary(
                publications.countVerified(filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear()),
                publications.countVerifiedDepartments(filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear()),
                publications.countVerifiedAuthors(filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear()),
                thisYear
        );
    }

    @Transactional(readOnly = true)
    public List<DepartmentCount> byDepartment(String department, Integer fromYear, Integer toYear) {
        DashboardFilter filter = DashboardFilter.of(department, fromYear, toYear);
        return publications.countVerifiedByDepartment(
                        filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear())
                .stream()
                .map(row -> new DepartmentCount((String) row[0], asLong(row[1])))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<YearCount> byYear(String department, Integer fromYear, Integer toYear) {
        DashboardFilter filter = DashboardFilter.of(department, fromYear, toYear);
        return publications.countVerifiedByYear(
                        filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear())
                .stream()
                .map(row -> new YearCount(((Number) row[0]).intValue(), asLong(row[1])))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FacultyCount> byFaculty(String department, Integer fromYear, Integer toYear) {
        DashboardFilter filter = DashboardFilter.of(department, fromYear, toYear);
        return publications.countVerifiedByAuthor(
                        filter.applyDepartment(), filter.department(), filter.applyFrom(), filter.fromYear(), filter.applyTo(), filter.toYear())
                .stream()
                .limit(TOP_FACULTY)
                .map(row -> new FacultyCount((String) row[0], asLong(row[1])))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicationResponse> recent(String department, Integer fromYear, Integer toYear) {
        DashboardFilter filter = DashboardFilter.of(department, fromYear, toYear);
        return publications.findRecentVerified(
                        filter.applyDepartment(),
                        filter.department(),
                        filter.applyFrom(),
                        filter.fromYear(),
                        filter.applyTo(),
                        filter.toYear(),
                        PageRequest.of(0, RECENT_LIMIT))
                .stream()
                .map(PublicationResponse::from)
                .toList();
    }

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }
}
