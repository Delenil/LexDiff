package com.example.lexdiff.similarity;

import com.example.lexdiff.diff.WordTokenizer;
import com.example.lexdiff.domain.Provision;
import com.example.lexdiff.domain.Token;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ShingleFingerprinter implements Fingerprinter<Provision> {

    public static final int DEFAULT_K = 3;

    private static final Pattern WORD_TOKEN = Pattern.compile("[\\w-]+");

    private final WordTokenizer tokenizer = new WordTokenizer();
    private final int k;

    public ShingleFingerprinter() {
        this(DEFAULT_K);
    }

    public ShingleFingerprinter(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("Shingle size k must be >= 1, got: " + k);
        }
        this.k = k;
    }

    // Produces a set of k-word shingles from the provision's word tokens (punctuation stripped, lowercased).
    @Override
    public ShingleSet fingerprint(Provision provision) {
        List<String> tokens = tokenizer.tokenize(provision.text())
                .stream()
                .map(Token::text)
                .filter(t -> WORD_TOKEN.matcher(t).matches())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            return new ShingleSet(provision.id(), Set.of());
        }

        int effectiveK = Math.min(k, tokens.size());

        Set<String> shingles = new LinkedHashSet<>();
        for (int i = 0; i <= tokens.size() - effectiveK; i++) {
            shingles.add(String.join(" ", tokens.subList(i, i + effectiveK)));
        }
        return new ShingleSet(provision.id(), shingles);
    }
}
