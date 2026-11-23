package edu.university.portal.service;

import edu.university.portal.dto.CrossrefWork;
import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.exception.CrossrefNotFoundException;
import edu.university.portal.exception.CrossrefUnavailableException;
import edu.university.portal.exception.ValidationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class CrossrefClient {

    static final String USER_AGENT = "UniversityPublicationPortal/0.2 (mailto:research-office@university.edu)";

    private final RestTemplate restTemplate;
    private final CrossrefMapper mapper;

    public CrossrefClient(RestTemplate crossrefRestTemplate, CrossrefMapper mapper) {
        this.restTemplate = crossrefRestTemplate;
        this.mapper = mapper;
    }

    public CrossrefWork lookup(String rawDoi) {
        String doi = DoiNormalizer.clean(rawDoi);
        if (!DoiNormalizer.isValid(doi)) {
            throw new ValidationException(List.of(new FieldErrorItem("doi", "DOI must look like 10.xxxx/suffix")));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        try {
            String body = restTemplate.exchange(
                    "https://api.crossref.org/works/{doi}",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class,
                    doi
            ).getBody();
            return mapper.map(body, doi);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new CrossrefNotFoundException(doi);
            }
            throw new CrossrefUnavailableException("Crossref returned HTTP " + ex.getStatusCode().value(), 502);
        } catch (ResourceAccessException ex) {
            throw new CrossrefUnavailableException("Crossref did not respond in time", 504);
        }
    }
}
