package com.example.lexdiff.similarity;

import java.util.Set;

public record ShingleSet(String provisionId, Set<String> shingles) implements Fingerprint {

    public ShingleSet {
        shingles = Set.copyOf(shingles);
    }
}
