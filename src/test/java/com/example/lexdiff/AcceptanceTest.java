package com.example.lexdiff;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.parse.FileDocumentLoader;
import com.example.lexdiff.parse.RegexDocumentParser;
import com.example.lexdiff.parse.SegmentationProfile;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.ChangeOperation;
import com.example.lexdiff.report.ChangeType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Acceptance tests covering the core comparison scenarios using real fixture files.
class AcceptanceTest {

    private static final SegmentationProfile PROFILE =
            new SegmentationProfile("^Article\\s+\\d+", null, null);

    private final FileDocumentLoader  loader = new FileDocumentLoader();
    private final RegexDocumentParser parser = new RegexDocumentParser();

    // Scenario 1: wholly new article in v4 should be ADD_PROVISION, not a spurious move match.
    @Test
    void insertedArticleIsReportedAsAddProvision() {
        AmendmentReport report = compare("statute_v3", "statute_v4");

        boolean hasAdd = report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.ADD_PROVISION);
        assertTrue(hasAdd, "Expected ADD_PROVISION for newly inserted Article 6");
    }

    // Scenario 2: same-text article renumbered (v3→v4) should be RENUMBER_PROVISION, not DELETE+ADD.
    @Test
    void renumberedArticleIsNotReportedAsDeletePlusAdd() {
        AmendmentReport report = compare("statute_v3", "statute_v4");

        boolean hasRenumber = report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.RENUMBER_PROVISION);
        boolean hasSpuriousDelete = report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.DELETE_PROVISION
                        && op.evidenceSnippets().stream()
                                .anyMatch(s -> s.contains("judge may dismiss")));

        assertTrue(hasRenumber, "Expected RENUMBER_PROVISION for same-text article with new number");
        assertFalse(hasSpuriousDelete, "Renumbered article must not appear as a spurious deletion");
    }

    @Test
    void renumberedArticleCarriesEvidenceSnippets() {
        AmendmentReport report = compare("statute_v3", "statute_v4");

        ChangeOperation op = report.operations().stream()
                .filter(o -> o.type() == ChangeType.RENUMBER_PROVISION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No RENUMBER_PROVISION found"));

        assertFalse(op.evidenceSnippets().isEmpty(),
                "RENUMBER_PROVISION should carry before/after evidence snippets");
        assertTrue(op.confidence() >= 0.0 && op.confidence() <= 1.0,
                "Confidence must be in [0, 1]");
    }

    // Scenario 3: relocated provisions (statute_move_v1→v2) should appear as MOVE or RENUMBER, not DELETE+ADD.
    @Test
    void movedAndEditedProvisionIsReportedAsMoveNotDeletePlusAdd() {
        AmendmentReport report = compare("statute_move_v1", "statute_move_v2");

        boolean hasMove = report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.MOVE_PROVISION
                             || op.type() == ChangeType.RENUMBER_PROVISION);
        assertTrue(hasMove, "Expected at least one MOVE or RENUMBER operation for relocated provisions");
    }

    @Test
    void moveOperationConfidenceIsInValidRange() {
        AmendmentReport report = compare("statute_move_v1", "statute_move_v2");

        report.operations().stream()
                .filter(op -> op.type() == ChangeType.MOVE_PROVISION
                           || op.type() == ChangeType.RENUMBER_PROVISION)
                .forEach(op -> assertTrue(op.confidence() >= 0.0 && op.confidence() <= 1.0,
                        "Confidence out of range: " + op.confidence()));
    }

    @Test
    void moveOperationIncludesEvidenceSnippets() {
        AmendmentReport report = compare("statute_move_v1", "statute_move_v2");

        List<ChangeOperation> moveOps = report.operations().stream()
                .filter(op -> op.type() == ChangeType.MOVE_PROVISION
                           || op.type() == ChangeType.RENUMBER_PROVISION)
                .toList();

        assertFalse(moveOps.isEmpty(), "Expected at least one move/renumber operation");
        moveOps.forEach(op -> assertFalse(op.evidenceSnippets().isEmpty(),
                "Move/renumber operation should carry before/after evidence snippets"));
    }

    // Scenario 4: in-place text change (v1→v2: "fourteen" → "twenty-one") should produce MODIFY_PROVISION with evidence.
    @Test
    void modifiedProvisionIsReportedWithTokenLevelEvidence() {
        AmendmentReport report = compare("statute_v1", "statute_v2");

        ChangeOperation modify = report.operations().stream()
                .filter(op -> op.type() == ChangeType.MODIFY_PROVISION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No MODIFY_PROVISION found"));

        assertFalse(modify.evidenceSnippets().isEmpty(),
                "MODIFY_PROVISION must include token-level evidence snippets");
    }

    // Scenario 5: wrong segmentation profile should throw a clear exception, not silently return an empty report.
    @Test
    void missingSegmentationProfileThrowsWithClearMessage() {
        SegmentationProfile wrongProfile = new SegmentationProfile("^§\\s+\\d+", null, null);
        String raw = loader.load("src/test/resources/fixtures/statute_v1.txt");
        DocumentMetadata meta = new DocumentMetadata("Test", "TEST", "v1", LocalDate.now());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(raw, meta, wrongProfile));

        assertTrue(ex.getMessage().contains("No provisions found"),
                "Exception message should identify the problem clearly");
    }

    // Multi-version: chaining three versions should produce two distinct, non-empty pairwise reports.
    @Test
    void threeVersionChainProducesTwoDistinctReports() {
        LegalDocument docV1 = load("statute_v1", "v1");
        LegalDocument docV2 = load("statute_v2", "v2");
        LegalDocument docV3 = load("statute_v3", "v3");  // different statute, used as stand-in for v3

        DocumentComparator comparator = new DocumentComparator();
        AmendmentReport r1 = comparator.compare(docV1, docV2);
        AmendmentReport r2 = comparator.compare(docV2, docV3);

        assertFalse(r1.operations().isEmpty(), "First pairwise report must have operations");
        // r2 compares documents from different statutes — all provisions differ
        assertFalse(r2.operations().isEmpty(), "Second pairwise report must have operations");
        assertNotEquals(r1.fromVersion(), r2.fromVersion(),
                "Each pairwise report must reference its own source version");
    }

    private AmendmentReport compare(String fixtureA, String fixtureB) {
        return new DocumentComparator().compare(load(fixtureA, "vA"), load(fixtureB, "vB"));
    }

    private LegalDocument load(String fixture, String version) {
        String path = "src/test/resources/fixtures/" + fixture + ".txt";
        String raw  = loader.load(path);
        DocumentMetadata meta = new DocumentMetadata(fixture, "TEST", version, LocalDate.now());
        return parser.parse(raw, meta, PROFILE);
    }
}
