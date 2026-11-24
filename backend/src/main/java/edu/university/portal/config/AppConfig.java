package edu.university.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public AppConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Bean
    public RestTemplate crossrefRestTemplate(
            @Value("${app.crossref.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.crossref.read-timeout:5s}") Duration readTimeout
    ) {
        return buildRestTemplate(connectTimeout, readTimeout);
    }

    @Bean
    public RestTemplate orcidRestTemplate(
            @Value("${app.orcid.connect-timeout:4s}") Duration connectTimeout,
            @Value("${app.orcid.read-timeout:8s}") Duration readTimeout
    ) {
        return buildRestTemplate(connectTimeout, readTimeout);
    }

    private RestTemplate buildRestTemplate(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
