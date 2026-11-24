package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.PublicationRequest;
import edu.university.portal.exception.DuplicateDoiException;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.repository.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private PublicationRepository publications;

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(publications);
    }

    @Test
    void missingRequiredFieldsAreReportedTogether() {
        PublicationRequest request = new PublicationRequest("  ", List.of("  "), null, null, " ", null, null, null);
        Publication target = new Publication();

        assertThatThrownBy(() -> validationService.validateAndApply(request, target, null))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFieldErrors().stream().map(FieldErrorItem::field).toList())
                .asList()
                .contains("title", "authors", "year", "department");
    }

    @Test
    void rejectsMalformedDoi() {
        Publication target = new Publication();
        assertThatThrownBy(() -> validationService.validateAndApply(valid("10.12/too-short"), target, null))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFieldErrors().get(0).field())
                .isEqualTo("doi");
    }

    @Test
    void duplicateDoiConflicts() {
        when(publications.existsByDoiIgnoreCase("10.5555/abc")).thenReturn(true);
        Publication target = new Publication();

        assertThatThrownBy(() -> validationService.validateAndApply(valid("https://doi.org/10.5555/ABC"), target, null))
                .isInstanceOf(DuplicateDoiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void cleansDoiBeforeStorage() {
        when(publications.existsByDoiIgnoreCase("10.5555/abc")).thenReturn(false);
        Publication target = new Publication();

        validationService.validateAndApply(valid("  HTTPS://DOI.ORG/10.5555/AbC  "), target, null);

        assertThat(target.getDoi()).isEqualTo("10.5555/abc");
        assertThat(target.getSource()).isEqualTo(PublicationSource.FORM);
        assertThat(target.getAuthors()).containsExactly("Ada Lovelace");
    }

    @Test
    void stripsDxDoiPrefixAndBlankDoiIsOptional() {
        assertThat(ValidationService.cleanDoi("http://dx.doi.org/10.12345/XYZ/")).isEqualTo("10.12345/xyz");
        assertThat(ValidationService.cleanDoi("   ")).isNull();
        assertThat(ValidationService.isValidDoi("10.123456789/ok")).isTrue();
        assertThat(ValidationService.isValidDoi("10.1234567890/no")).isFalse();

        Publication target = new Publication();
        validationService.validateAndApply(valid(null), target, null);
        assertThat(target.getDoi()).isNull();
    }

    private PublicationRequest valid(String doi) {
        return new PublicationRequest(
                "A study of campus wetlands",
                List.of(" Ada Lovelace "),
                doi,
                2024,
                "Biology",
                "Plant Physiology",
                null,
                null
        );
    }
}
