package edu.university.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.portal.service.CrossrefMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class CrossrefConfig {

    @Bean
    public RestTemplate crossrefRestTemplate(
            @Value("${app.crossref.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.crossref.read-timeout:5s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    @Bean
    public CrossrefMapper crossrefMapper(ObjectMapper objectMapper) {
        return new CrossrefMapper(objectMapper);
    }
}
