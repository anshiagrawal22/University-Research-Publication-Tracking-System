package edu.university.portal.controller;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PageResponse;
import edu.university.portal.dto.PublicationFiltersResponse;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.dto.SecurityGateCheck;
import edu.university.portal.dto.SecurityGateReport;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.GlobalExceptionHandler;
import edu.university.portal.exception.SecurityGateException;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.model.SecurityGateStatus;
import edu.university.portal.service.PublicationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicationController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class PublicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicationService publicationService;

    @Test
    void createReturnsTheStoredPublication() throws Exception {
        when(publicationService.create(org.mockito.ArgumentMatchers.any())).thenReturn(sample());

        mockMvc.perform(post("/api/publications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "River Sensors and Campus Flood Models",
                                  "authors": ["Mina Rahman"],
                                  "doi": "10.5555/example.2024.001",
                                  "year": 2024,
                                  "department": "Biology",
                                  "journal": "Journal of Civic Hydrology"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("River Sensors and Campus Flood Models"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.doi").value("10.5555/example.2024.001"));
    }

    @Test
    void duplicateDoiReturnsConflictJson() throws Exception {
        when(publicationService.create(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateDoiException("10.5555/example.2024.001"));

        mockMvc.perform(post("/api/publications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "River Sensors and Campus Flood Models",
                                  "authors": ["Mina Rahman"],
                                  "doi": "10.5555/example.2024.001",
                                  "year": 2024,
                                  "department": "Biology"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("doi"));
    }

    @Test
    void validationErrorsUseTheSharedJsonShape() throws Exception {
        when(publicationService.create(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ValidationException(List.of(new FieldErrorItem("title", "Title is required"))));

        mockMvc.perform(post("/api/publications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"authors":["Mina Rahman"],"year":2024,"department":"Biology"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.path").value("/api/publications"));
    }

    @Test
    void listForwardsSearchAndFilters() throws Exception {
        when(publicationService.list(
                eq("quantum"),
                eq("Physics"),
                isNull(),
                eq(2022),
                eq("Nature"),
                eq(PublicationStatus.VERIFIED),
                eq(1),
                eq(5)
        )).thenReturn(new PageResponse<>(List.of(sample()), 1, 5, 1, 1));

        mockMvc.perform(get("/api/publications")
                        .param("search", "quantum")
                        .param("department", "Physics")
                        .param("year", "2022")
                        .param("journal", "Nature")
                        .param("status", "VERIFIED")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].department").value("Biology"));
    }

    @Test
    void filtersUpdateAndDeleteEndpointsWork() throws Exception {
        when(publicationService.filters()).thenReturn(
                new PublicationFiltersResponse(List.of("Biology"), List.of(2024), List.of("Mina Rahman"))
        );
        when(publicationService.update(eq(7L), org.mockito.ArgumentMatchers.any())).thenReturn(sample());

        mockMvc.perform(get("/api/publications/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0]").value("Biology"))
                .andExpect(jsonPath("$.years[0]").value(2024))
                .andExpect(jsonPath("$.faculties[0]").value("Mina Rahman"));

        mockMvc.perform(put("/api/publications/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "River Sensors and Campus Flood Models",
                                  "authors": ["Mina Rahman"],
                                  "doi": "10.5555/example.2024.001",
                                  "year": 2024,
                                  "department": "Biology",
                                  "journal": "Journal of Civic Hydrology",
                                  "status": "VERIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        mockMvc.perform(delete("/api/publications/7"))
                .andExpect(status().isNoContent());
        verify(publicationService).delete(7L);
    }

    @Test
    void securityGateAndPublishEndpointsWork() throws Exception {
        SecurityGateReport report = new SecurityGateReport(
                SecurityGateStatus.PASSED,
                true,
                Instant.parse("2026-03-01T00:00:00Z"),
                "All 4 security gates passed",
                List.of(new SecurityGateCheck("INPUT_SECURITY", "Input & XSS Sanitization", SecurityGateStatus.PASSED, "Clean"))
        );
        when(publicationService.evaluateSecurityGate(7L)).thenReturn(report);
        when(publicationService.publish(7L)).thenReturn(sample());
        when(publicationService.publish(8L)).thenThrow(new SecurityGateException(
                "Security gate blocked publication from going live",
                new SecurityGateReport(SecurityGateStatus.FAILED, false, Instant.now(), "Blocked", List.of()),
                List.of(new FieldErrorItem("status", "Blocked"))
        ));

        mockMvc.perform(get("/api/publications/7/security-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("PASSED"))
                .andExpect(jsonPath("$.publishAllowed").value(true));

        mockMvc.perform(post("/api/publications/7/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        mockMvc.perform(post("/api/publications/8/publish"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
    }

    private PublicationResponse sample() {
        return new PublicationResponse(
                7L,
                "River Sensors and Campus Flood Models",
                List.of("Mina Rahman"),
                "10.5555/example.2024.001",
                2024,
                "Biology",
                "Journal of Civic Hydrology",
                null,
                PublicationSource.CROSSREF,
                PublicationStatus.VERIFIED,
                Instant.parse("2026-03-01T00:00:00Z")
        );
    }
}
