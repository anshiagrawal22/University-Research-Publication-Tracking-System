package edu.university.portal.service;

import edu.university.portal.dto.DashboardSummary;
import edu.university.portal.dto.DepartmentCount;
import edu.university.portal.dto.FacultyCount;
import edu.university.portal.model.Publication;
import edu.university.portal.model.PublicationSource;
import edu.university.portal.model.PublicationStatus;
import edu.university.portal.repository.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DashboardService.class)
@ActiveProfiles("test")
class DashboardServiceTest {

    @Autowired
    private PublicationRepository publications;

    @Autowired
    private DashboardService dashboardService;

    private int currentYear;

    @BeforeEach
    void seed() {
        currentYear = Year.now(ZoneOffset.UTC).getValue();
        save("Verified biology", PublicationStatus.VERIFIED, "Biology", currentYear - 1,
                Instant.parse("2026-01-02T00:00:00Z"), "Ada Lovelace", "Grace Hopper");
        save("Verified physics", PublicationStatus.VERIFIED, "Physics", currentYear,
                Instant.parse("2026-06-01T00:00:00Z"), "Ada Lovelace");
        save("Rejected biology", PublicationStatus.REJECTED, "Biology", currentYear,
                Instant.parse("2026-07-01T00:00:00Z"), "Rejected Author");
        save("In review economics", PublicationStatus.IN_REVIEW, "Economics", currentYear,
                Instant.parse("2026-08-01T00:00:00Z"), "Pending Reviewer");
    }

    @Test
    void summaryCountsOnlyVerifiedRecords() {
        DashboardSummary summary = dashboardService.summary(null, null, null, null);

        assertThat(summary.totalPublications()).isEqualTo(2);
        assertThat(summary.departments()).isEqualTo(2);
        assertThat(summary.authors()).isEqualTo(2);
        assertThat(summary.thisYear()).isEqualTo(1);
    }

    @Test
    void chartsOmitRejectedAndInReviewRecords() {
        assertThat(dashboardService.byDepartment(null, null, null, null))
                .extracting(DepartmentCount::department)
                .containsExactly("Biology", "Physics");

        assertThat(dashboardService.byFaculty(null, null, null, null))
                .extracting(FacultyCount::faculty, FacultyCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ada Lovelace", 2L),
                        org.assertj.core.groups.Tuple.tuple("Grace Hopper", 1L)
                );

        assertThat(dashboardService.byYear(null, null, null, null))
                .extracting(row -> row.year())
                .containsExactly(currentYear - 1, currentYear);

        assertThat(dashboardService.recent(null, null, null, null))
                .extracting(row -> row.title())
                .containsExactly("Verified physics", "Verified biology");
    }

    @Test
    void departmentFilterNarrowsEveryAggregate() {
        DashboardSummary summary = dashboardService.summary("Biology", null, null, null);

        assertThat(summary.totalPublications()).isEqualTo(1);
        assertThat(summary.thisYear()).isZero();
        assertThat(dashboardService.byFaculty("Biology", null, null, null))
                .extracting(FacultyCount::faculty)
                .containsExactly("Ada Lovelace", "Grace Hopper");
        assertThat(dashboardService.recent("biology", null, currentYear - 1, currentYear))
                .extracting(row -> row.department())
                .containsExactly("Biology");
    }

    @Test
    void facultyFilterMatchesTheWholeNameNotPartOfIt() {
        // "A. Pai" is contained in "S. A. Pai" - the filter must not mix the two people up.
        save("Paper by A Pai", PublicationStatus.VERIFIED, "Physics", currentYear, Instant.parse("2026-09-01T00:00:00Z"), "A. Pai");
        save("Paper by S A Pai", PublicationStatus.VERIFIED, "Physics", currentYear, Instant.parse("2026-09-02T00:00:00Z"), "S. A. Pai");

        assertThat(dashboardService.summary(null, "A. Pai", null, null).totalPublications()).isEqualTo(1);
        assertThat(dashboardService.summary(null, "a. pai", null, null).totalPublications()).isEqualTo(1);
        assertThat(dashboardService.recent(null, "A. Pai", null, null))
                .extracting(row -> row.title())
                .containsExactly("Paper by A Pai");
        assertThat(publications.search(false, null, false, null, true, "A. Pai", false, 0, false, null, false, null,
                        org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(Publication::getTitle)
                .containsExactly("Paper by A Pai");
    }

    private void save(String title, PublicationStatus status, String department, int year, Instant createdAt, String... authors) {
        Publication publication = new Publication();
        publication.setTitle(title);
        publication.setAuthors(new ArrayList<>(List.of(authors)));
        publication.setDoi("10.5555/" + title.toLowerCase().replace(' ', '.'));
        publication.setYear(year);
        publication.setDepartment(department);
        publication.setJournal("Test Journal");
        publication.setSource(PublicationSource.FORM);
        publication.setStatus(status);
        publication.setCreatedAt(createdAt);
        publications.save(publication);
    }
}
