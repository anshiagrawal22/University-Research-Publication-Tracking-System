package edu.university.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.dto.OrcidWork;
import edu.university.portal.exception.ExternalApiException;
import edu.university.portal.repository.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OrcidServiceTest {

    @Mock
    private CrossrefService crossrefService;

    @Mock
    private PublicationRepository publications;

    private MockRestServiceServer server;
    private OrcidService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        service = new OrcidService(
                restTemplate,
                crossrefService,
                publications,
                new ObjectMapper(),
                "https://pub.orcid.org/v3.0",
                "https://orcid.org/oauth/token",
                "",
                "",
                20
        );
    }

    @Test
    void parsesWorksAndEnrichesFromCrossrefWhenDoiExists() {
        String worksJson = """
                {
                  "group": [
                    {
                      "work-summary": [
                        {
                          "display-index": "0",
                          "title": { "title": { "value": "ORCID Draft Title" } },
                          "journal-title": { "value": "ORCID Journal" },
                          "publication-date": { "year": { "value": "2023" } },
                          "external-ids": {
                            "external-id": [
                              {
                                "external-id-type": "doi",
                                "external-id-value": "10.1038/nature12373",
                                "external-id-relationship": "self"
                              }
                            ]
                          }
                        }
                      ]
                    },
                    {
                      "work-summary": [
                        {
                          "display-index": "0",
                          "title": { "title": { "value": "Work Without DOI" } },
                          "journal-title": { "value": "Campus Review" },
                          "publication-date": { "year": { "value": "2022" } },
                          "external-ids": { "external-id": [] }
                        }
                      ]
                    }
                  ]
                }
                """;
        String personJson = """
                {
                  "name": {
                    "given-names": { "value": "Josiah" },
                    "family-name": { "value": "Carberry" }
                  }
                }
                """;

        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/works"))
                .andRespond(withSuccess(worksJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/person"))
                .andRespond(withSuccess(personJson, MediaType.APPLICATION_JSON));

        when(crossrefService.fetchByDoi("10.1038/nature12373")).thenReturn(
                new CrossrefWork(
                        "Nanometre-scale thermometry in a living cell",
                        List.of("G. Kucsko", "M. D. Lukin"),
                        "Nature",
                        2013,
                        "10.1038/nature12373"
                )
        );
        when(publications.existsByDoiIgnoreCase("10.1038/nature12373")).thenReturn(true);

        List<OrcidWork> works = service.fetchWorks("0000-0002-1825-0097");

        assertThat(works).hasSize(2);
        OrcidWork first = works.get(0);
        assertThat(first.title()).isEqualTo("Nanometre-scale thermometry in a living cell");
        assertThat(first.authors()).containsExactly("G. Kucsko", "M. D. Lukin");
        assertThat(first.journal()).isEqualTo("Nature");
        assertThat(first.year()).isEqualTo(2013);
        assertThat(first.enrichedFromCrossref()).isTrue();
        assertThat(first.alreadyExists()).isTrue();

        OrcidWork second = works.get(1);
        assertThat(second.title()).isEqualTo("Work Without DOI");
        assertThat(second.authors()).containsExactly("Josiah Carberry");
        assertThat(second.enrichedFromCrossref()).isFalse();
        assertThat(second.alreadyExists()).isFalse();
        server.verify();
    }

    @Test
    void fallsBackToOrcidMetadataWhenCrossrefMissesDoi() {
        String worksJson = """
                {
                  "group": [
                    {
                      "work-summary": [
                        {
                          "display-index": "0",
                          "title": { "title": { "value": "Fallback Work" } },
                          "journal-title": { "value": "Proceedings" },
                          "publication-date": { "year": { "value": "2021" } },
                          "external-ids": {
                            "external-id": [
                              {
                                "external-id-type": "doi",
                                "external-id-value": "10.5555/missing",
                                "external-id-relationship": "self"
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/works"))
                .andRespond(withSuccess(worksJson, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0002-1825-0097/person"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        when(crossrefService.fetchByDoi("10.5555/missing"))
                .thenThrow(new ExternalApiException("No Crossref record was found for DOI 10.5555/missing", 404));
        when(publications.existsByDoiIgnoreCase("10.5555/missing")).thenReturn(false);

        List<OrcidWork> works = service.fetchWorks("0000-0002-1825-0097");

        assertThat(works).hasSize(1);
        assertThat(works.get(0).title()).isEqualTo("Fallback Work");
        assertThat(works.get(0).enrichedFromCrossref()).isFalse();
        assertThat(works.get(0).doi()).isEqualTo("10.5555/missing");
    }

    @Test
    void notFoundOrcidThrowsCleanException() {
        server.expect(requestTo("https://pub.orcid.org/v3.0/0000-0000-0000-0000/works"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.fetchWorks("0000-0000-0000-0000"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("0000-0000-0000-0000");
    }
}
