package edu.university.portal.service;

import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.dto.PublicationResponse;
import edu.university.portal.exception.SecurityGateException;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock
    private PublicationRepository publications;

    private PublicationService publicationService;

    @BeforeEach
    void setUp() {
        ValidationService validationService = new ValidationService(publications);
        SecurityGateService securityGateService = new SecurityGateService(publications);
        publicationService = new PublicationService(publications, validationService, securityGateService);
    }

    @Test
    void createStagesCleanSubmissionInReviewBeforeGoingLive() {
        when(publications.existsByDoiIgnoreCase("10.5555/uni.cs.2025.101")).thenReturn(false);
        when(publications.existsByTitleIgnoreCaseAndYear("Distributed Verification of Academic Records", 2025)).thenReturn(false);
        when(publications.save(any(Publication.class))).thenAnswer(inv -> {
            Publication saved = inv.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        PublicationResponse created = publicationService.create(new PublicationRequest(
                "Distributed Verification of Academic Records",
                List.of("Anika Kapoor"),
                "10.5555/uni.cs.2025.101",
                2025,
                "Computer Science",
                "IEEE Access",
                "0000-0002-1825-0097",
                PublicationSource.CROSSREF
        ));

        assertThat(created.status()).isEqualTo(PublicationStatus.IN_REVIEW);
        assertThat(created.gateStatus()).isEqualTo(SecurityGateStatus.PASSED);
    }

    @Test
    void createBlocksXssInputImmediately() {
        PublicationRequest xssRequest = new PublicationRequest(
                "Clean Title <img src=x onerror=alert(1)>",
                List.of("Ada Lovelace"),
                null,
                2025,
                "Computer Science",
                "ACM Computing Surveys",
                null,
                PublicationSource.FORM
        );

        assertThatThrownBy(() -> publicationService.create(xssRequest))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void publishPromotesPassingPaperAndBlocksFailingPaper() {
        Publication passing = new Publication();
        passing.setId(5L);
        passing.setTitle("Trusted Provenance in Research Archives");
        passing.setAuthors(List.of("Samir Rahman"));
        passing.setDoi("10.5555/uni.cs.2025.200");
        passing.setYear(2025);
        passing.setDepartment("Computer Science");
        passing.setJournal("ACM Transactions on Information Systems");
        passing.setSource(PublicationSource.CROSSREF);
        passing.setStatus(PublicationStatus.IN_REVIEW);
        passing.setCreatedAt(Instant.now());

        when(publications.findById(5L)).thenReturn(Optional.of(passing));
        when(publications.existsByTitleIgnoreCaseAndYearAndIdNot(passing.getTitle(), 2025, 5L)).thenReturn(false);
        when(publications.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        PublicationResponse live = publicationService.publish(5L);
        assertThat(live.status()).isEqualTo(PublicationStatus.VERIFIED);
        assertThat(live.gateStatus()).isEqualTo(SecurityGateStatus.PASSED);

        Publication failing = new Publication();
        failing.setId(6L);
        failing.setTitle("Cold Fusion Apparatus Without Review");
        failing.setAuthors(List.of("Blake Quinn"));
        failing.setYear(2025);
        failing.setDepartment("Physics");
        failing.setJournal("Unreviewed Preprint Series");
        failing.setSource(PublicationSource.FORM);
        failing.setStatus(PublicationStatus.IN_REVIEW);
        failing.setCreatedAt(Instant.now());

        when(publications.findById(6L)).thenReturn(Optional.of(failing));
        when(publications.existsByTitleIgnoreCaseAndYearAndIdNot(failing.getTitle(), 2025, 6L)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.publish(6L))
                .isInstanceOf(SecurityGateException.class)
                .hasMessageContaining("blocked");
        assertThat(failing.getStatus()).isEqualTo(PublicationStatus.REJECTED);
        assertThat(failing.getGateStatus()).isEqualTo(SecurityGateStatus.FAILED);
    }
}
