package edu.university.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.exception.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CrossrefServiceTest {

    private MockRestServiceServer server;
    private CrossrefService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new CrossrefService(restTemplate, new ObjectMapper());
    }

    @Test
    void mapsSavedSampleJsonWithoutCallingCrossref() throws IOException {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withSuccess(fixture("/crossref/work-ok.json"), MediaType.APPLICATION_JSON));

        CrossrefWork work = service.fetchByDoi("https://doi.org/10.5555/example.2024.001");

        assertThat(work.title()).isEqualTo("River Sensors and Campus Flood Models");
        assertThat(work.authors()).containsExactly("Mina Rahman", "Owen Clarke", "Campus Hydrology Group");
        assertThat(work.journal()).isEqualTo("Journal of Civic Hydrology");
        assertThat(work.year()).isEqualTo(2024);
        assertThat(work.doi()).isEqualTo("10.5555/example.2024.001");
        server.verify();
    }

    @Test
    void missingFieldsBecomeNullInsteadOfFailing() throws IOException {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withSuccess(fixture("/crossref/work-missing-fields.json"), MediaType.APPLICATION_JSON));

        CrossrefWork work = service.fetchByDoi("10.5555/partial");

        assertThat(work.title()).isNull();
        assertThat(work.journal()).isNull();
        assertThat(work.year()).isNull();
        assertThat(work.authors()).containsExactly("Okeke");
        assertThat(work.doi()).isEqualTo("10.5555/partial");
    }

    @Test
    void notFoundIsReportedCleanly() {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.fetchByDoi("10.5555/missing.work"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("10.5555/missing.work");
    }

    @Test
    void timeoutIsReportedCleanly() {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.fetchByDoi("10.5555/slow.work"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("did not respond");
    }

    @Test
    void tooManyRequestsIsRetriedThenSucceeds() throws IOException {
        // Crossref answers 429 when a client has too many requests open; one retry should be enough.
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withSuccess(fixture("/crossref/work-ok.json"), MediaType.APPLICATION_JSON));

        CrossrefWork work = service.fetchByDoi("10.5555/example.2024.001");

        assertThat(work.title()).isEqualTo("River Sensors and Campus Flood Models");
        server.verify();
    }

    @Test
    void connectionFailureIsNotReportedAsATimeout() {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withException(new java.net.ConnectException("Connection refused")));

        assertThatThrownBy(() -> service.fetchByDoi("10.5555/offline.work"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("Could not connect to Crossref")
                .satisfies(ex -> assertThat(((ExternalApiException) ex).getStatus()).isEqualTo(503));
    }

    private String fixture(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
