package edu.university.portal.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "publications", indexes = {
        @Index(name = "idx_publications_status", columnList = "status"),
        @Index(name = "idx_publications_department", columnList = "department"),
        @Index(name = "idx_publications_year", columnList = "pub_year")
})
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @ElementCollection
    @CollectionTable(name = "publication_authors", joinColumns = @JoinColumn(name = "publication_id"))
    @Column(name = "author_name", nullable = false, length = 200)
    @OrderColumn(name = "author_order")
    private List<String> authors = new ArrayList<>();

    @Column(length = 255, unique = true)
    private String doi;

    @Column(name = "pub_year", nullable = false)
    private Integer year;

    @Column(nullable = false, length = 160)
    private String department;

    @Column(length = 300)
    private String journal;

    @Column(name = "orcid_id", length = 19)
    private String orcidId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_status", length = 20)
    private SecurityGateStatus gateStatus;

    @Column(name = "gate_summary", length = 1000)
    private String gateSummary;

    @Column(name = "gate_checked_at")
    private Instant gateCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getJournal() {
        return journal;
    }

    public void setJournal(String journal) {
        this.journal = journal;
    }

    public String getOrcidId() {
        return orcidId;
    }

    public void setOrcidId(String orcidId) {
        this.orcidId = orcidId;
    }

    public PublicationSource getSource() {
        return source;
    }

    public void setSource(PublicationSource source) {
        this.source = source;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public SecurityGateStatus getGateStatus() {
        return gateStatus;
    }

    public void setGateStatus(SecurityGateStatus gateStatus) {
        this.gateStatus = gateStatus;
    }

    public String getGateSummary() {
        return gateSummary;
    }

    public void setGateSummary(String gateSummary) {
        this.gateSummary = gateSummary;
    }

    public Instant getGateCheckedAt() {
        return gateCheckedAt;
    }

    public void setGateCheckedAt(Instant gateCheckedAt) {
        this.gateCheckedAt = gateCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
