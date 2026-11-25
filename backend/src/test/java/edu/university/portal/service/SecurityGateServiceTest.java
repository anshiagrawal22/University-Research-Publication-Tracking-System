package edu.university.portal.service;

import edu.university.portal.dto.SecurityGateReport;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.model.SecurityGateStatus;
import edu.university.portal.repository.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityGateServiceTest {

    @Mock
    private PublicationRepository publications;

    private SecurityGateService securityGateService;

    @BeforeEach
    void setUp() {
        securityGateService = new SecurityGateService(publications);
    }

    @Test
    void cleanPaperWithValidDoiAndOrcidPassesAllGates() {
        Publication pub = validPublication();
        when(publications.existsByTitleIgnoreCaseAndYear(pub.getTitle(), pub.getYear())).thenReturn(false);

        SecurityGateReport report = securityGateService.evaluate(pub, null);

        assertThat(report.overallStatus()).isEqualTo(SecurityGateStatus.PASSED);
        assertThat(report.publishAllowed()).isTrue();
        assertThat(report.checks()).hasSize(4);
        assertThat(report.checks()).allMatch(c -> c.status() == SecurityGateStatus.PASSED);
    }

    @Test
    void xssPayloadFailsInputSecurityAndThrowsOnAssert() {
        Publication pub = validPublication();
        pub.setTitle("<script>alert('xss')</script> Quantum Sensors");

        assertThatThrownBy(() -> securityGateService.assertInputSafe(pub))
                .isInstanceOf(ValidationException.class);

        SecurityGateReport report = securityGateService.evaluate(pub, null);
        assertThat(report.overallStatus()).isEqualTo(SecurityGateStatus.FAILED);
        assertThat(report.publishAllowed()).isFalse();
    }

    @Test
    void untrustedPreprintVenueAndExternalUrlsAreBlocked() {
        Publication preprint = validPublication();
        preprint.setJournal("Unreviewed Preprint Series");

        SecurityGateReport preprintReport = securityGateService.evaluate(preprint, null);
        assertThat(preprintReport.overallStatus()).isEqualTo(SecurityGateStatus.FAILED);
        assertThat(preprintReport.publishAllowed()).isFalse();

        Publication urlTitle = validPublication();
        urlTitle.setTitle("Click https://evil.example/paper for details");

        SecurityGateReport urlReport = securityGateService.evaluate(urlTitle, null);
        assertThat(urlReport.overallStatus()).isEqualTo(SecurityGateStatus.FAILED);
        assertThat(urlReport.publishAllowed()).isFalse();
    }

    @Test
    void duplicateTitleAndYearOrPlaceholderOrFutureYearFailsGate() {
        Publication duplicate = validPublication();
        when(publications.existsByTitleIgnoreCaseAndYear(duplicate.getTitle(), duplicate.getYear())).thenReturn(true);
        assertThat(securityGateService.evaluate(duplicate, null).publishAllowed()).isFalse();

        Publication placeholder = validPublication();
        placeholder.setTitle("Test paper");
        assertThat(securityGateService.evaluate(placeholder, null).publishAllowed()).isFalse();

        Publication future = validPublication();
        future.setYear(Year.now(ZoneOffset.UTC).getValue() + 5);
        assertThat(securityGateService.evaluate(future, null).publishAllowed()).isFalse();
    }

    @Test
    void missingPersistentIdentifiersProducesWarningButAllowsReviewerPublish() {
        Publication manual = validPublication();
        manual.setDoi(null);
        manual.setOrcidId(null);
        when(publications.existsByTitleIgnoreCaseAndYear(manual.getTitle(), manual.getYear())).thenReturn(false);

        SecurityGateReport report = securityGateService.evaluate(manual, null);

        assertThat(report.overallStatus()).isEqualTo(SecurityGateStatus.WARN);
        assertThat(report.publishAllowed()).isTrue();
    }

    private Publication validPublication() {
        Publication pub = new Publication();
        pub.setTitle("Privacy-Preserving Telemetry for Campus Networks");
        pub.setAuthors(List.of("Ada Lovelace"));
        pub.setDoi("10.5555/uni.cs.2025.099");
        pub.setOrcidId("0000-0002-1825-0097");
        pub.setYear(2025);
        pub.setDepartment("Computer Science");
        pub.setJournal("IEEE Security & Privacy");
        pub.setSource(PublicationSource.CROSSREF);
        pub.setStatus(PublicationStatus.IN_REVIEW);
        return pub;
    }
}
