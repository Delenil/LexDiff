package com.example.lexdiff.similarity;

import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimilarityTest {

    private final ShingleFingerprinter fingerprinter = new ShingleFingerprinter();
    private final JaccardSimilarityModel jaccard = new JaccardSimilarityModel();

    @Test
    void fingerprintProducesTrigramsFromProvisionText() {
        Provision p = provision("p-0001", "The contracting party shall pay the invoice.");
        ShingleSet ss = fingerprinter.fingerprint(p);

        assertTrue(ss.shingles().contains("the contracting party"));
        assertTrue(ss.shingles().contains("contracting party shall"));
        assertTrue(ss.shingles().contains("party shall pay"));
        assertEquals("p-0001", ss.provisionId());
    }

    @Test
    void fingerprintIsLowerCased() {
        Provision p = provision("p-0002", "ARTICLE ONE The Rights");
        ShingleSet ss = fingerprinter.fingerprint(p);

        assertTrue(ss.shingles().contains("article one the"));
        assertFalse(ss.shingles().contains("ARTICLE ONE The"));
    }

    @Test
    void fingerprintFallsBackToUnigramsForShortText() {
        Provision p = provision("p-0003", "Short text");
        ShingleSet ss = fingerprinter.fingerprint(p);

        assertFalse(ss.shingles().isEmpty());
    }

    @Test
    void fingerprintEmptyTextReturnsEmptySet() {
        Provision p = provision("p-0004", "");
        ShingleSet ss = fingerprinter.fingerprint(p);

        assertTrue(ss.shingles().isEmpty());
    }

    @Test
    void customKProducesCorrectNgramSize() {
        ShingleFingerprinter bigram = new ShingleFingerprinter(2);
        Provision p = provision("p-0005", "The party shall pay.");
        ShingleSet ss = bigram.fingerprint(p);

        assertTrue(ss.shingles().contains("the party"));
        assertTrue(ss.shingles().contains("party shall"));
        assertFalse(ss.shingles().contains("the party shall"));
    }

    @Test
    void invalidKThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ShingleFingerprinter(0));
    }

    @Test
    void identicalSetsScoreOne() {
        ShingleSet a = shingleSet("a", "alpha beta gamma", "beta gamma delta");
        ShingleSet b = shingleSet("b", "alpha beta gamma", "beta gamma delta");

        assertEquals(1.0, jaccard.similarity(a, b), 1e-9);
    }

    @Test
    void disjointSetsScoreZero() {
        ShingleSet a = shingleSet("a", "alpha beta gamma");
        ShingleSet b = shingleSet("b", "delta epsilon zeta");

        assertEquals(0.0, jaccard.similarity(a, b), 1e-9);
    }

    @Test
    void partialOverlapScoresCorrectly() {
        ShingleSet a = shingleSet("a", "x", "y", "z");
        ShingleSet b = shingleSet("b", "y", "z", "w");

        assertEquals(0.5, jaccard.similarity(a, b), 1e-9);
    }

    @Test
    void twoEmptySetsScoreOne() {
        ShingleSet a = new ShingleSet("a", Set.of());
        ShingleSet b = new ShingleSet("b", Set.of());

        assertEquals(1.0, jaccard.similarity(a, b), 1e-9);
    }

    @Test
    void oneEmptySetScoresZero() {
        ShingleSet a = shingleSet("a", "alpha beta gamma");
        ShingleSet b = new ShingleSet("b", Set.of());

        assertEquals(0.0, jaccard.similarity(a, b), 1e-9);
        assertEquals(0.0, jaccard.similarity(b, a), 1e-9);
    }

    @Test
    void nearDuplicateProvisionScoresHigh() {
        Provision original = provision("p-0010",
                "The contracting party shall pay the invoice within thirty days.");
        Provision moved = provision("p-0011",
                "The contracting party shall pay the invoice within fourteen days.");

        ShingleSet ssA = fingerprinter.fingerprint(original);
        ShingleSet ssB = fingerprinter.fingerprint(moved);

        double score = jaccard.similarity(ssA, ssB);
        assertTrue(score > 0.5, "Expected high similarity for near-duplicate, got: " + score);
    }

    @Test
    void unrelatedProvisionsScoreLow() {
        Provision p1 = provision("p-0020",
                "The minister shall publish an annual report on budget expenditures.");
        Provision p2 = provision("p-0021",
                "Criminal penalties apply to unauthorized disclosure of classified information.");

        ShingleSet ssA = fingerprinter.fingerprint(p1);
        ShingleSet ssB = fingerprinter.fingerprint(p2);

        double score = jaccard.similarity(ssA, ssB);
        assertTrue(score < 0.2, "Expected low similarity for unrelated provisions, got: " + score);
    }

    private Provision provision(String id, String text) {
        return new Provision(id, "Label " + id, NodeType.ARTICLE, text);
    }

    private ShingleSet shingleSet(String id, String... shingles) {
        return new ShingleSet(id, Set.of(shingles));
    }
}
