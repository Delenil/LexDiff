package com.example.lexdiff.similarity;

import java.util.HashSet;
import java.util.Set;

public class JaccardSimilarityModel implements SimilarityModel<ShingleSet> {

    // Returns |A ∩ B| / |A ∪ B|; both-empty scores 1.0, one-empty scores 0.0.
    @Override
    public double similarity(ShingleSet a, ShingleSet b) {
        if (a.shingles().isEmpty() && b.shingles().isEmpty()) {
            return 1.0;
        }
        if (a.shingles().isEmpty() || b.shingles().isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(a.shingles());
        intersection.retainAll(b.shingles());

        Set<String> union = new HashSet<>(a.shingles());
        union.addAll(b.shingles());

        return (double) intersection.size() / union.size();
    }
}
