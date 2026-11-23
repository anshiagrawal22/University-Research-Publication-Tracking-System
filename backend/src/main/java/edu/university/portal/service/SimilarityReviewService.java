package edu.university.portal.service;

import java.util.Optional;

/**
 * Extension point for Phase 3. An implementation can compare title and year
 * with existing records and hand near-duplicates to the review queue.
 * Exact DOI collisions are rejected earlier by {@link ValidationService}.
 */
public interface SimilarityReviewService {

    Optional<SimilarityMatch> findSimilar(String title, Integer year, String doi);
}
