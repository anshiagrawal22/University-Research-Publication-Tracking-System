package edu.university.portal.controller;

import edu.university.portal.dto.DashboardSummary;
import edu.university.portal.dto.DepartmentCount;
import edu.university.portal.dto.FacultyCount;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.YearCount;
import edu.university.portal.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear
    ) {
        return dashboardService.summary(department, faculty, fromYear, toYear);
    }

    @GetMapping("/by-department")
    public List<DepartmentCount> byDepartment(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear
    ) {
        return dashboardService.byDepartment(department, faculty, fromYear, toYear);
    }

    @GetMapping("/by-year")
    public List<YearCount> byYear(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear
    ) {
        return dashboardService.byYear(department, faculty, fromYear, toYear);
    }

    @GetMapping("/by-faculty")
    public List<FacultyCount> byFaculty(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear
    ) {
        return dashboardService.byFaculty(department, faculty, fromYear, toYear);
    }

    @GetMapping("/recent")
    public List<PublicationResponse> recent(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear
    ) {
        return dashboardService.recent(department, faculty, fromYear, toYear);
    }
}
