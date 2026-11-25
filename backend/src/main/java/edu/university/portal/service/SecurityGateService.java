package edu.university.portal.service;

import edu.university.portal.dto.FieldErrorItem;
import edu.university.portal.dto.SecurityGateCheck;
import edu.university.portal.dto.SecurityGateReport;
import edu.university.portal.exception.ValidationException;
import edu.university.portal.model.Publication;
import edu.university.portal.model.SecurityGateStatus;
import edu.university.portal.repository.PublicationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SecurityGateService {

    private static final int MIN_TITLE_LENGTH = 8;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>|&lt;\\s*script", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_URI_PATTERN = Pattern.compile("(javascript|vbscript)\\s*:|data\\s*:\\s*text/html", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("\\bon[a-z]{2,20}\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern INJECTION_PATTERN = Pattern.compile("\\$\\{jndi:|\\bunion\\s+select\\b|;\\s*drop\\s+table\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern URL_IN_TEXT_PATTERN = Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER_TITLE_PATTERN = Pattern.compile(
            "^(test(\\s+paper|\\s+publication|\\s+title|\\s*\\d*)?|asdf+|qwerty+|lorem\\s+ipsum.*|dummy.*|untitled.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> BLOCKED_VENUE_TERMS = List.of(
            "unreviewed preprint",
            "predatory",
            "paper mill",
            "retracted",
            "fake journal"
    );
    private static final List<String> SUSPICIOUS_DOI_DOMAINS = List.of(
            "bit.ly",
            "tinyurl.com",
            "localhost",
            "127.0.0.1",
            ".."
    );

    private final PublicationRepository publications;

    public SecurityGateService(PublicationRepository publications) {
        this.publications = publications;
    }

    /**
     * Evaluates all pre-publish security and integrity gates for a publication
     * before it is allowed to go live on the website.
     */
    public SecurityGateReport evaluate(Publication publication, Long excludeId) {
        List<SecurityGateCheck> checks = new ArrayList<>();
        checks.add(checkInputSecurity(publication));
        checks.add(checkIdentifierIntegrity(publication));
        checks.add(checkDuplicateAndMetadata(publication, excludeId));
        checks.add(checkPublicationPolicy(publication));

        SecurityGateStatus overall = SecurityGateStatus.PASSED;
        for (SecurityGateCheck check : checks) {
            if (check.status() == SecurityGateStatus.FAILED) {
                overall = SecurityGateStatus.FAILED;
                break;
            }
            if (check.status() == SecurityGateStatus.WARN) {
                overall = SecurityGateStatus.WARN;
            }
        }

        boolean publishAllowed = overall != SecurityGateStatus.FAILED;
        Instant checkedAt = Instant.now();
        String summary = buildSummary(overall, checks);
        return new SecurityGateReport(overall, publishAllowed, checkedAt, summary, List.copyOf(checks));
    }

    /**
     * Rejects active XSS, script, or injection payloads immediately on submission or edit.
     */
    public void assertInputSafe(Publication publication) {
        SecurityGateCheck inputCheck = checkInputSecurity(publication);
        if (inputCheck.status() == SecurityGateStatus.FAILED) {
            throw new ValidationException(List.of(new FieldErrorItem("title", inputCheck.message())));
        }
    }

    public void applyReport(Publication publication, SecurityGateReport report) {
        publication.setGateStatus(report.overallStatus());
        publication.setGateSummary(report.summary());
        publication.setGateCheckedAt(report.checkedAt());
    }

    private SecurityGateCheck checkInputSecurity(Publication publication) {
        List<String> fields = new ArrayList<>();
        if (publication.getTitle() != null) fields.add(publication.getTitle());
        if (publication.getAuthors() != null) fields.addAll(publication.getAuthors());
        if (publication.getDepartment() != null) fields.add(publication.getDepartment());
        if (publication.getJournal() != null) fields.add(publication.getJournal());
        if (publication.getDoi() != null) fields.add(publication.getDoi());
        if (publication.getOrcidId() != null) fields.add(publication.getOrcidId());

        for (String value : fields) {
            if (containsDangerousPayload(value)) {
                return new SecurityGateCheck(
                        "INPUT_SECURITY",
                        "Input & XSS Sanitization",
                        SecurityGateStatus.FAILED,
                        "Blocked unsafe markup, script, control characters, or injection syntax in submission fields"
                );
            }
        }
        return new SecurityGateCheck(
                "INPUT_SECURITY",
                "Input & XSS Sanitization",
                SecurityGateStatus.PASSED,
                "All metadata fields passed XSS, markup, and injection sanitization"
        );
    }

    private SecurityGateCheck checkIdentifierIntegrity(Publication publication) {
        String doi = publication.getDoi();
        String orcidId = publication.getOrcidId();

        if (doi != null) {
            String lowerDoi = doi.toLowerCase(Locale.ROOT);
            for (String blocked : SUSPICIOUS_DOI_DOMAINS) {
                if (lowerDoi.contains(blocked)) {
                    return new SecurityGateCheck(
                            "IDENTIFIER_INTEGRITY",
                            "DOI & ORCID Verification",
                            SecurityGateStatus.FAILED,
                            "DOI contains a disallowed domain, path traversal, or shortener pattern"
                    );
                }
            }
        }

        if (orcidId != null && !hasValidOrcidChecksum(orcidId)) {
            return new SecurityGateCheck(
                    "IDENTIFIER_INTEGRITY",
                    "DOI & ORCID Verification",
                    SecurityGateStatus.WARN,
                    "ORCID iD checksum did not match ISO 7064 Mod 11-2; verify researcher identity before publishing"
            );
        }

        if (doi == null && orcidId == null) {
            return new SecurityGateCheck(
                    "IDENTIFIER_INTEGRITY",
                    "DOI & ORCID Verification",
                    SecurityGateStatus.WARN,
                    "No DOI or ORCID iD provided; manual metadata verification is recommended"
            );
        }

        return new SecurityGateCheck(
                "IDENTIFIER_INTEGRITY",
                "DOI & ORCID Verification",
                SecurityGateStatus.PASSED,
                "Persistent scholarly identifier verified"
        );
    }

    private SecurityGateCheck checkDuplicateAndMetadata(Publication publication, Long excludeId) {
        String title = publication.getTitle() == null ? "" : publication.getTitle().trim();
        Integer year = publication.getYear();

        if (title.length() < MIN_TITLE_LENGTH || PLACEHOLDER_TITLE_PATTERN.matcher(title).matches()) {
            return new SecurityGateCheck(
                    "DUPLICATE_AND_METADATA",
                    "Duplicate & Metadata Integrity",
                    SecurityGateStatus.FAILED,
                    "Title is too short or appears to be placeholder/test text"
            );
        }

        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        if (year != null && year > currentYear + 1) {
            return new SecurityGateCheck(
                    "DUPLICATE_AND_METADATA",
                    "Duplicate & Metadata Integrity",
                    SecurityGateStatus.FAILED,
                    "Publication year (" + year + ") exceeds the allowed window (max " + (currentYear + 1) + ")"
            );
        }

        if (!title.isEmpty() && year != null) {
            boolean duplicateTitle = excludeId == null
                    ? publications.existsByTitleIgnoreCaseAndYear(title, year)
                    : publications.existsByTitleIgnoreCaseAndYearAndIdNot(title, year, excludeId);
            if (duplicateTitle) {
                return new SecurityGateCheck(
                        "DUPLICATE_AND_METADATA",
                        "Duplicate & Metadata Integrity",
                        SecurityGateStatus.FAILED,
                        "A publication with the same title and year already exists in the register"
                );
            }
        }

        return new SecurityGateCheck(
                "DUPLICATE_AND_METADATA",
                "Duplicate & Metadata Integrity",
                SecurityGateStatus.PASSED,
                "No duplicate title/year collision found and metadata bounds are valid"
        );
    }

    private SecurityGateCheck checkPublicationPolicy(Publication publication) {
        String title = publication.getTitle() == null ? "" : publication.getTitle();
        String journal = publication.getJournal() == null ? "" : publication.getJournal();

        if (URL_IN_TEXT_PATTERN.matcher(title).find() || URL_IN_TEXT_PATTERN.matcher(journal).find()) {
            return new SecurityGateCheck(
                    "PUBLICATION_POLICY",
                    "Venue & Release Policy",
                    SecurityGateStatus.FAILED,
                    "External URLs are not permitted inside publication title or journal fields"
            );
        }

        String combinedLower = (journal + " " + title).toLowerCase(Locale.ROOT);
        for (String term : BLOCKED_VENUE_TERMS) {
            if (combinedLower.contains(term)) {
                return new SecurityGateCheck(
                        "PUBLICATION_POLICY",
                        "Venue & Release Policy",
                        SecurityGateStatus.FAILED,
                        "Blocked by publication policy: matched restricted venue/status term '" + term + "'"
                );
            }
        }

        if (journal.isBlank()) {
            return new SecurityGateCheck(
                    "PUBLICATION_POLICY",
                    "Venue & Release Policy",
                    SecurityGateStatus.WARN,
                    "Journal or conference venue is missing; confirm venue before publishing live"
            );
        }

        return new SecurityGateCheck(
                "PUBLICATION_POLICY",
                "Venue & Release Policy",
                SecurityGateStatus.PASSED,
                "Venue and publication policy checks passed"
        );
    }

    static boolean containsDangerousPayload(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return HTML_TAG_PATTERN.matcher(value).find()
                || DANGEROUS_URI_PATTERN.matcher(value).find()
                || EVENT_HANDLER_PATTERN.matcher(value).find()
                || INJECTION_PATTERN.matcher(value).find()
                || CONTROL_CHAR_PATTERN.matcher(value).find();
    }

    static boolean hasValidOrcidChecksum(String orcidId) {
        String digits = orcidId.replace("-", "").toUpperCase(Locale.ROOT);
        if (digits.length() != 16) {
            return false;
        }
        int total = 0;
        for (int i = 0; i < 15; i++) {
            char ch = digits.charAt(i);
            if (!Character.isDigit(ch)) {
                return false;
            }
            total = (total + (ch - '0')) * 2;
        }
        int remainder = total % 11;
        int result = (12 - remainder) % 11;
        char expected = result == 10 ? 'X' : (char) ('0' + result);
        return digits.charAt(15) == expected;
    }

    private String buildSummary(SecurityGateStatus overall, List<SecurityGateCheck> checks) {
        List<String> issues = checks.stream()
                .filter(check -> check.status() != SecurityGateStatus.PASSED)
                .map(check -> check.label() + ": " + check.message())
                .toList();
        if (issues.isEmpty()) {
            return "All 4 security gates passed";
        }
        String joined = String.join("; ", issues);
        if (joined.length() > 980) {
            joined = joined.substring(0, 977) + "...";
        }
        return overall == SecurityGateStatus.FAILED ? "Blocked — " + joined : "Warnings — " + joined;
    }
}
