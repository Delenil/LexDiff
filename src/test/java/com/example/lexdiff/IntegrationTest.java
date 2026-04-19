package com.example.lexdiff;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.parse.FileDocumentLoader;
import com.example.lexdiff.parse.RegexDocumentParser;
import com.example.lexdiff.parse.SegmentationProfile;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.ChangeOperation;
import com.example.lexdiff.report.ChangeType;
import com.example.lexdiff.report.JsonReportRenderer;
import com.example.lexdiff.report.TextReportRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private static final SegmentationProfile PROFILE =
            new SegmentationProfile("^Article\\s+\\d+", null, null);

    private LegalDocument docV1;
    private LegalDocument docV2;
    private AmendmentReport report;

    @BeforeEach
    void setUp() {
        FileDocumentLoader loader = new FileDocumentLoader();
        RegexDocumentParser parser = new RegexDocumentParser();

        DocumentMetadata metaV1 = new DocumentMetadata(
                "Statute on Public Information Access", "TEST", "v1", LocalDate.of(2026, 1, 10));
        DocumentMetadata metaV2 = new DocumentMetadata(
                "Statute on Public Information Access", "TEST", "v2", LocalDate.of(2026, 2, 14));

        docV1 = parser.parse(loader.load("src/test/resources/fixtures/statute_v1.txt"), metaV1, PROFILE);
        docV2 = parser.parse(loader.load("src/test/resources/fixtures/statute_v2.txt"), metaV2, PROFILE);

        report = new DocumentComparator().compare(docV1, docV2);
    }

    @Test
    void reportMetadataIsCorrect() {
        assertEquals("Statute on Public Information Access", report.title());
        assertEquals("v1", report.fromVersion());
        assertEquals("v2", report.toVersion());
    }

    @Test
    void reportContainsAtLeastOneOperation() {
        assertFalse(report.operations().isEmpty());
    }

    @Test
    void modifiedProvisionIsDetected() {
        boolean hasModify = report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.MODIFY_PROVISION);
        assertTrue(hasModify, "Expected at least one MODIFY_PROVISION operation");
    }

    @Test
    void modifiedProvisionHasEvidenceSnippets() {
        report.operations().stream()
                .filter(op -> op.type() == ChangeType.MODIFY_PROVISION)
                .forEach(op -> assertFalse(op.evidenceSnippets().isEmpty(),
                        "MODIFY_PROVISION should have evidence snippets"));
    }

    @Test
    void allOperationsHaveValidIds() {
        for (ChangeOperation op : report.operations()) {
            if (op.type() == ChangeType.ADD_PROVISION) {
                assertNull(op.beforeId());
                assertNotNull(op.afterId());
            } else if (op.type() == ChangeType.DELETE_PROVISION) {
                assertNotNull(op.beforeId());
                assertNull(op.afterId());
            } else {
                assertNotNull(op.beforeId());
                assertNotNull(op.afterId());
            }
        }
    }

    @Test
    void textRendererProducesNonEmptyOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(report, out);
        String text = out.toString();

        assertTrue(text.contains("Amendment Report"));
        assertTrue(text.contains("v1"));
        assertTrue(text.contains("v2"));
    }

    @Test
    void jsonRendererProducesWellFormedOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JsonReportRenderer().render(report, out);
        String json = out.toString();

        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"operations\""));
        assertTrue(json.contains("\"type\""));
    }

    @Test
    void moveOrRenumberConfidenceIsInValidRange() {
        report.operations().stream()
                .filter(op -> op.type() == ChangeType.MOVE_PROVISION
                           || op.type() == ChangeType.RENUMBER_PROVISION)
                .forEach(op -> {
                    assertTrue(op.confidence() >= 0.0 && op.confidence() <= 1.0,
                            "Confidence out of range: " + op.confidence());
                });
    }
}
