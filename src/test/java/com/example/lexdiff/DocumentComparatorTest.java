package com.example.lexdiff;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.ChangeOperation;
import com.example.lexdiff.report.ChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentComparatorTest {

    private DocumentComparator comparator;
    private DocumentMetadata   metaA;
    private DocumentMetadata   metaB;

    @BeforeEach
    void setUp() {
        comparator = new DocumentComparator();
        metaA = new DocumentMetadata("Test", "TEST", "v1", LocalDate.of(2026, 1, 1));
        metaB = new DocumentMetadata("Test", "TEST", "v2", LocalDate.of(2026, 2, 1));
    }

    @Test
    void emptyDocumentsProduceEmptyReport() {
        LegalDocument a = new LegalDocument(metaA, List.of());
        LegalDocument b = new LegalDocument(metaB, List.of());

        AmendmentReport report = comparator.compare(a, b);

        assertTrue(report.operations().isEmpty());
    }

    @Test
    void identicalDocumentsProduceNoOperations() {
        Provision p = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "Citizens shall have the right to access public information.");
        LegalDocument a = new LegalDocument(metaA, List.of(p));
        LegalDocument b = new LegalDocument(metaB, List.of(p));

        AmendmentReport report = comparator.compare(a, b);

        assertTrue(report.operations().isEmpty());
    }

    @Test
    void provisionOnlyInAProducesDeleteOperation() {
        Provision p = new Provision("article-0000", "Article 1", NodeType.ARTICLE, "Some text.");
        LegalDocument a = new LegalDocument(metaA, List.of(p));
        LegalDocument b = new LegalDocument(metaB, List.of());

        AmendmentReport report = comparator.compare(a, b);

        assertEquals(1, report.operations().size());
        assertEquals(ChangeType.DELETE_PROVISION, report.operations().get(0).type());
        assertEquals("article-0000", report.operations().get(0).beforeId());
        assertNull(report.operations().get(0).afterId());
    }

    @Test
    void provisionOnlyInBProducesAddOperation() {
        Provision p = new Provision("article-0000", "Article 1", NodeType.ARTICLE, "New article text.");
        LegalDocument a = new LegalDocument(metaA, List.of());
        LegalDocument b = new LegalDocument(metaB, List.of(p));

        AmendmentReport report = comparator.compare(a, b);

        assertEquals(1, report.operations().size());
        assertEquals(ChangeType.ADD_PROVISION, report.operations().get(0).type());
        assertNull(report.operations().get(0).beforeId());
        assertEquals("article-0000", report.operations().get(0).afterId());
    }

    @Test
    void modifiedProvisionProducesModifyOperationWithEvidence() {
        Provision pA = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "Appeals shall be filed within fourteen days.");
        Provision pB = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "Appeals shall be filed within twenty-one days.");
        LegalDocument a = new LegalDocument(metaA, List.of(pA));
        LegalDocument b = new LegalDocument(metaB, List.of(pB));

        AmendmentReport report = comparator.compare(a, b);

        assertEquals(1, report.operations().size());
        ChangeOperation op = report.operations().get(0);
        assertEquals(ChangeType.MODIFY_PROVISION, op.type());
        assertFalse(op.evidenceSnippets().isEmpty());
        assertTrue(op.evidenceSnippets().stream().anyMatch(s -> s.contains("fourteen")));
        assertTrue(op.evidenceSnippets().stream().anyMatch(s -> s.contains("twenty-one")));
    }

    @Test
    void sameLabelSameTextProducesNoOperations() {
        String text = "The contracting party shall pay the invoice within thirty days of delivery.";
        Provision pA = new Provision("article-0000", "Article 3", NodeType.ARTICLE, text);
        Provision pB = new Provision("article-0001", "Article 3", NodeType.ARTICLE, text);
        LegalDocument a = new LegalDocument(metaA, List.of(pA));
        LegalDocument b = new LegalDocument(metaB, List.of(pB));

        AmendmentReport report = comparator.compare(a, b);

        assertTrue(report.operations().isEmpty(),
                "Provisions with same label and identical text should produce no operations");
    }

    @Test
    void renumberedProvisionWithDifferentLabelProducesRenumberOperation() {
        String text = "The minister shall publish an annual report on expenditures within the budget.";
        Provision pA = new Provision("article-0000", "Article 3", NodeType.ARTICLE, text);
        Provision pB = new Provision("article-0001", "Article 5", NodeType.ARTICLE, text);
        LegalDocument a = new LegalDocument(metaA, List.of(pA));
        LegalDocument b = new LegalDocument(metaB, List.of(pB));

        AmendmentReport report = comparator.compare(a, b);

        assertTrue(report.operations().stream()
                .anyMatch(op -> op.type() == ChangeType.RENUMBER_PROVISION));
    }

    @Test
    void reportMetadataReflectsSourceDocuments() {
        LegalDocument a = new LegalDocument(metaA, List.of());
        LegalDocument b = new LegalDocument(metaB, List.of());

        AmendmentReport report = comparator.compare(a, b);

        assertEquals("Test",  report.title());
        assertEquals("v1", report.fromVersion());
        assertEquals("v2", report.toVersion());
    }

    @Test
    void multipleChangesInSingleComparisonAreAllReported() {
        Provision unchanged = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "This provision remains unchanged across versions.");
        Provision modified  = new Provision("article-0001", "Article 2", NodeType.ARTICLE,
                "The deadline is fourteen days.");
        Provision deleted   = new Provision("article-0002", "Article 3", NodeType.ARTICLE,
                "This provision will be removed.");
        Provision added     = new Provision("article-0003", "Article 4", NodeType.ARTICLE,
                "This provision is entirely new.");

        Provision modifiedNew = new Provision("article-0001", "Article 2", NodeType.ARTICLE,
                "The deadline is twenty-one days.");

        LegalDocument a = new LegalDocument(metaA, List.of(unchanged, modified, deleted));
        LegalDocument b = new LegalDocument(metaB, List.of(unchanged, modifiedNew, added));

        AmendmentReport report = comparator.compare(a, b);

        assertTrue(report.operations().stream().anyMatch(op -> op.type() == ChangeType.MODIFY_PROVISION));
        assertTrue(report.operations().stream().anyMatch(op -> op.type() == ChangeType.DELETE_PROVISION));
        assertTrue(report.operations().stream().anyMatch(op -> op.type() == ChangeType.ADD_PROVISION));
    }

    @Test
    void deleteOperationIncludesOriginalTextAsEvidence() {
        Provision p = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "This provision is being removed from the statute.");
        LegalDocument a = new LegalDocument(metaA, List.of(p));
        LegalDocument b = new LegalDocument(metaB, List.of());

        AmendmentReport report = comparator.compare(a, b);

        ChangeOperation op = report.operations().get(0);
        assertFalse(op.evidenceSnippets().isEmpty());
    }

    @Test
    void addOperationIncludesNewTextAsEvidence() {
        Provision p = new Provision("article-0000", "Article 1", NodeType.ARTICLE,
                "This is a newly inserted provision.");
        LegalDocument a = new LegalDocument(metaA, List.of());
        LegalDocument b = new LegalDocument(metaB, List.of(p));

        AmendmentReport report = comparator.compare(a, b);

        ChangeOperation op = report.operations().get(0);
        assertFalse(op.evidenceSnippets().isEmpty());
    }
}
