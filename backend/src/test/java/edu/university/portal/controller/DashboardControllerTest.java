package edu.university.portal.controller;

import edu.university.portal.dto.DashboardSummary;
import edu.university.portal.dto.DepartmentCount;
import edu.university.portal.dto.FacultyCount;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.YearCount;
import edu.university.portal.exception.GlobalExceptionHandler;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void summaryReturnsTheFourCounts() throws Exception {
        when(dashboardService.summary(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new DashboardSummary(12, 4, 9, 3));

        mockMvc.perform(get("/api/dashboard/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPublications").value(12))
                .andExpect(jsonPath("$.departments").value(4))
                .andExpect(jsonPath("$.authors").value(9))
                .andExpect(jsonPath("$.thisYear").value(3));
    }

    @Test
    void chartsAcceptTheSharedFilters() throws Exception {
        when(dashboardService.byDepartment(eq("Physics"), isNull(), eq(2020), eq(2024)))
                .thenReturn(List.of(new DepartmentCount("Physics", 2)));
        when(dashboardService.byYear(eq("Physics"), isNull(), eq(2020), eq(2024)))
                .thenReturn(List.of(new YearCount(2022, 2)));
        when(dashboardService.byFaculty(eq("Physics"), isNull(), eq(2020), eq(2024)))
                .thenReturn(List.of(new FacultyCount("Ada Lovelace", 2)));
        when(dashboardService.recent(eq("Physics"), isNull(), eq(2020), eq(2024)))
                .thenReturn(List.of(new PublicationResponse(
                        3L,
                        "Low-Cost Interferometry",
                        List.of("Ada Lovelace"),
                        "10.5555/uni.phy.2021.004",
                        2021,
                        "Physics",
                        "American Journal of Physics",
                        null,
                        PublicationSource.FORM,
                        PublicationStatus.VERIFIED,
                        Instant.parse("2026-02-01T00:00:00Z")
                )));

        mockMvc.perform(get("/api/dashboard/by-department")
                        .param("department", "Physics")
                        .param("fromYear", "2020")
                        .param("toYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].department").value("Physics"))
                .andExpect(jsonPath("$[0].count").value(2));

        mockMvc.perform(get("/api/dashboard/by-year")
                        .param("department", "Physics")
                        .param("fromYear", "2020")
                        .param("toYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2022));

        mockMvc.perform(get("/api/dashboard/by-faculty")
                        .param("department", "Physics")
                        .param("fromYear", "2020")
                        .param("toYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faculty").value("Ada Lovelace"));

        mockMvc.perform(get("/api/dashboard/recent")
                        .param("department", "Physics")
                        .param("fromYear", "2020")
                        .param("toYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Low-Cost Interferometry"))
                .andExpect(jsonPath("$[0].status").value("VERIFIED"));
    }
}
