package com.example.lexdiff.move;

import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoveDetectorTest {

    private FingerprintMoveDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FingerprintMoveDetector();
    }

    @Test
    void emptyListsProduceNoMatches() {
        List<MoveMatch> matches = detector.detect(List.of(), List.of());
        assertTrue(matches.isEmpty());
    }

    @Test
    void noDeletesProducesNoMatches() {
        Provision inserted = provision("i-0000", "Article 1", "The minister shall act.");
        List<MoveMatch> matches = detector.detect(List.of(), List.of(inserted));
        assertTrue(matches.isEmpty());
    }

    @Test
    void nearIdenticalProvisionIsDetectedAsMove() {
        Provision deleted  = provision("d-0000", "Article 3",
                "The contracting party shall pay the invoice within thirty days of delivery.");
        Provision inserted = provision("i-0000", "Article 3",
                "The contracting party shall pay the invoice within thirty days of delivery.");

        List<MoveMatch> matches = detector.detect(List.of(deleted), List.of(inserted));

        assertEquals(1, matches.size());
        MoveMatch m = matches.get(0);
        assertEquals("d-0000", m.oldId());
        assertEquals("i-0000", m.newId());
        assertEquals(MoveType.MOVE, m.type());
        assertTrue(m.score() > 0.9);
    }

    @Test
    void sameTextDifferentLabelIsClassifiedAsRenumber() {
        Provision deleted  = provision("d-0000", "Article 3",
                "Appeals shall be filed within fourteen days of notification.");
        Provision inserted = provision("i-0000", "Article 5",
                "Appeals shall be filed within fourteen days of notification.");

        List<MoveMatch> matches = detector.detect(List.of(deleted), List.of(inserted));

        assertEquals(1, matches.size());
        assertEquals(MoveType.RENUMBER, matches.get(0).type());
    }

    @Test
    void unrelatedProvisionsProduceNoMatch() {
        Provision deleted  = provision("d-0000", "Article 1",
                "The minister shall publish an annual budget report.");
        Provision inserted = provision("i-0000", "Article 2",
                "Criminal penalties apply for unauthorized disclosure of state secrets.");

        List<MoveMatch> matches = detector.detect(List.of(deleted), List.of(inserted));

        assertTrue(matches.isEmpty());
    }

    @Test
    void eachInsertedIsUsedAtMostOnce() {
        Provision del1 = provision("d-0000", "Article 1",
                "The contracting party shall pay the invoice within thirty days.");
        Provision del2 = provision("d-0001", "Article 2",
                "The contracting party shall pay the invoice within thirty days.");
        Provision ins  = provision("i-0000", "Article 1",
                "The contracting party shall pay the invoice within thirty days.");

        List<MoveMatch> matches = detector.detect(List.of(del1, del2), List.of(ins));

        assertEquals(1, matches.size());
    }

    // convenience builder for test provisions
    private Provision provision(String id, String label, String text) {
        return new Provision(id, label, NodeType.ARTICLE, text);
    }
}
