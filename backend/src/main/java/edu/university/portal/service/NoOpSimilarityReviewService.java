package edu.university.portal.service;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NoOpSimilarityReviewService implements SimilarityReviewService {

    @Override
    public Optional<SimilarityMatch> findSimilar(String title, Integer year, String doi) {
        return Optional.empty();
    }
}
