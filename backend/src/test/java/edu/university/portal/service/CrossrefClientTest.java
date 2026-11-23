package edu.university.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.exception.CrossrefNotFoundException;
import edu.university.portal.exception.CrossrefUnavailableException;
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

class CrossrefClientTest {

    private MockRestServiceServer server;
    private CrossrefClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new CrossrefClient(restTemplate, new CrossrefMapper(new ObjectMapper()));
    }

    @Test
    void mapsSavedSampleJsonWithoutCallingCrossref() throws IOException {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withSuccess(fixture("/crossref/work-ok.json"), MediaType.APPLICATION_JSON));

        CrossrefWork work = client.lookup("https://doi.org/10.5555/example.2024.001");

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

        CrossrefWork work = client.lookup("10.5555/partial");

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

        assertThatThrownBy(() -> client.lookup("10.5555/missing.work"))
                .isInstanceOf(CrossrefNotFoundException.class)
                .hasMessageContaining("10.5555/missing.work");
    }

    @Test
    void timeoutIsReportedCleanly() {
        server.expect(requestTo(containsString("api.crossref.org/works/")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.lookup("10.5555/slow.work"))
                .isInstanceOf(CrossrefUnavailableException.class)
                .hasMessageContaining("did not respond");
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
