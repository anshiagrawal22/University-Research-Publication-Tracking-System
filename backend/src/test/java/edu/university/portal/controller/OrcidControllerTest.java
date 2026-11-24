package edu.university.portal.controller;

import edu.university.portal.dto.OrcidWork;
import edu.university.portal.exception.ExternalApiException;
import edu.university.portal.exception.GlobalExceptionHandler;
import edu.university.portal.service.OrcidService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrcidController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class OrcidControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrcidService orcidService;

    @Test
    void rejectsMalformedOrcidId() throws Exception {
        mockMvc.perform(get("/api/orcid/invalid-orcid/works"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("orcidId"));
    }

    @Test
    void returnsWorksForValidOrcidId() throws Exception {
        when(orcidService.fetchWorks("0000-0002-1825-0097")).thenReturn(List.of(
                new OrcidWork(
                        "Sample Study",
                        List.of("Josiah Carberry"),
                        "Journal of Psychoceramics",
                        2020,
                        "10.5555/12345680",
                        "0000-0002-1825-0097",
                        true,
                        false
                )
        ));

        mockMvc.perform(get("/api/orcid/0000-0002-1825-0097/works"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sample Study"))
                .andExpect(jsonPath("$[0].enrichedFromCrossref").value(true));
    }

    @Test
    void returnsNotFoundWhenOrcidProfileMissing() throws Exception {
        when(orcidService.fetchWorks("0000-0000-0000-0000"))
                .thenThrow(new ExternalApiException("No ORCID record was found for 0000-0000-0000-0000", 404));

        mockMvc.perform(get("/api/orcid/0000-0000-0000-0000/works"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
