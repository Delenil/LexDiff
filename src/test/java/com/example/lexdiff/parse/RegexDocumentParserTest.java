package com.example.lexdiff.parse;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RegexDocumentParserTest {

    private RegexDocumentParser parser;
    private DocumentMetadata    metadata;

    // A profile that recognises "Article N" headings only
    private static final SegmentationProfile ARTICLE_ONLY =
            new SegmentationProfile("^Article\\s+\\d+", null, null);

    // A profile that also recognises "Section N" and "(N)" paragraph markers
    private static final SegmentationProfile FULL =
            new SegmentationProfile("^Article\\s+\\d+", "^Section\\s+\\d+", "^\\(\\d+\\)");

    @BeforeEach
    void setUp() {
        parser   = new RegexDocumentParser();
        metadata = new DocumentMetadata("Test Statute", "TEST", "v1", LocalDate.of(2026, 1, 10));
    }

    // basic parsing

    @Test
    void parsesCorrectNumberOfProvisions() {
        String raw = """
                Preamble line — should be ignored.

                Article 1
                First article body.

                Article 2
                Second article body.

                Article 3
                Third article body.
                """;

        LegalDocument doc = parser.parse(raw, metadata, ARTICLE_ONLY);

        assertThat(doc.provisions()).hasSize(3);
    }

    @Test
    void preservesVisibleLabelsExactly() {
        String raw = """
                Article 1
                Body of article one.

                Article 2
                Body of article two.
                """;

        List<Provision> provisions = parser.parse(raw, metadata, ARTICLE_ONLY).provisions();

        assertThat(provisions.get(0).label()).isEqualTo("Article 1");
        assertThat(provisions.get(1).label()).isEqualTo("Article 2");
    }

    @Test
    void extractsBodyTextJoiningLines() {
        String raw = """
                Article 1
                Line one of the body.
                Line two of the body.
                Line three of the body.
                """;

        Provision p = parser.parse(raw, metadata, ARTICLE_ONLY).provisions().get(0);

        assertThat(p.text())
                .isEqualTo("Line one of the body. Line two of the body. Line three of the body.");
    }

    @Test
    void assignsArticleNodeType() {
        String raw = """
                Article 1
                Body text.
                """;

        Provision p = parser.parse(raw, metadata, ARTICLE_ONLY).provisions().get(0);

        assertThat(p.type()).isEqualTo(NodeType.ARTICLE);
    }

    // canonical IDs

    @Test
    void assignsCanonicalIdsWithTypePrefix() {
        String raw = """
                Article 1
                First.

                Article 2
                Second.
                """;

        List<Provision> provisions = parser.parse(raw, metadata, ARTICLE_ONLY).provisions();

        assertThat(provisions.get(0).id()).isEqualTo("article-0000");
        assertThat(provisions.get(1).id()).isEqualTo("article-0001");
    }

    @Test
    void canonicalIdIsIndependentOfVisibleLabel() {
        // Even if Article 5 appears first in the file, its id is article-0000, not article-0005.
        String raw = """
                Article 5
                First in file.

                Article 7
                Second in file.
                """;

        List<Provision> provisions = parser.parse(raw, metadata, ARTICLE_ONLY).provisions();

        assertThat(provisions.get(0).id()).isEqualTo("article-0000");
        assertThat(provisions.get(1).id()).isEqualTo("article-0001");
    }

    // preamble and blank lines

    @Test
    void ignoresLinesBeforeFirstHeading() {
        String raw = """
                This is a preamble.
                It should be completely ignored.

                Article 1
                Actual body.
                """;

        LegalDocument doc = parser.parse(raw, metadata, ARTICLE_ONLY);

        assertThat(doc.provisions()).hasSize(1);
        assertThat(doc.provisions().get(0).label()).isEqualTo("Article 1");
    }

    @Test
    void skipsBlankLinesWithinProvisionBody() {
        String raw = """
                Article 1

                First sentence.

                Second sentence.

                """;

        Provision p = parser.parse(raw, metadata, ARTICLE_ONLY).provisions().get(0);

        assertThat(p.text()).isEqualTo("First sentence. Second sentence.");
    }

    @Test
    void emptyBodyProducesEmptyText() {
        String raw = """
                Article 1

                Article 2
                Has body.
                """;

        Provision first = parser.parse(raw, metadata, ARTICLE_ONLY).provisions().get(0);

        assertThat(first.text()).isEmpty();
    }

    // mixed node types

    @Test
    void assignsCorrectNodeTypesForMixedProfile() {
        String raw = """
                Section 1
                Section body.

                Article 1
                Article body.

                (1)
                Paragraph body.
                """;

        List<Provision> provisions = parser.parse(raw, metadata, FULL).provisions();

        assertThat(provisions).extracting(Provision::type)
                .containsExactly(NodeType.SECTION, NodeType.ARTICLE, NodeType.PARAGRAPH);
    }

    @Test
    void mixedTypeIdsAreIndependent() {
        String raw = """
                Section 1
                Body.

                Article 1
                Body.
                """;

        List<Provision> provisions = parser.parse(raw, metadata, FULL).provisions();

        assertThat(provisions.get(0).id()).isEqualTo("section-0000");
        assertThat(provisions.get(1).id()).isEqualTo("article-0001");
    }

    // profile edge cases

    @Test
    void nullAndBlankRegexesAreIgnored() {
        // Only articleRegex is active; section and paragraph fields are null/blank
        SegmentationProfile partial = new SegmentationProfile("^Article\\s+\\d+", "", null);

        String raw = """
                Article 1
                Body.
                """;

        assertThatNoException().isThrownBy(() -> parser.parse(raw, metadata, partial));
    }

    @Test
    void throwsWhenNoProvisionsFound() {
        String raw = """
                This document has no recognisable headings at all.
                """;

        assertThatIllegalArgumentException()
                .isThrownBy(() -> parser.parse(raw, metadata, ARTICLE_ONLY))
                .withMessageContaining("No provisions found");
    }

    // metadata passthrough

    @Test
    void metadataIsPreservedInDocument() {
        String raw = """
                Article 1
                Body.
                """;

        LegalDocument doc = parser.parse(raw, metadata, ARTICLE_ONLY);

        assertThat(doc.metadata()).isEqualTo(metadata);
    }

    // fixture files

    @Test
    void parsesV1FixtureCorrectly() {
        String path = "src/test/resources/fixtures/statute_v1.txt";
        String raw  = new FileDocumentLoader().load(path);
        DocumentMetadata meta = new DocumentMetadata(
                "Statute on Public Information Access", "TEST", "v1", LocalDate.of(2026, 1, 10));

        LegalDocument doc = parser.parse(raw, meta, ARTICLE_ONLY);

        assertThat(doc.provisions()).hasSize(5);
        assertThat(doc.provisions()).extracting(Provision::type)
                .containsOnly(NodeType.ARTICLE);
        assertThat(doc.provisions().get(0).label()).isEqualTo("Article 1");
        assertThat(doc.provisions().get(4).label()).isEqualTo("Article 5");
    }

    @Test
    void parsesV2FixtureCorrectly() {
        String path = "src/test/resources/fixtures/statute_v2.txt";
        String raw  = new FileDocumentLoader().load(path);
        DocumentMetadata meta = new DocumentMetadata(
                "Statute on Public Information Access", "TEST", "v2", LocalDate.of(2026, 2, 14));

        LegalDocument doc = parser.parse(raw, meta, ARTICLE_ONLY);

        assertThat(doc.provisions()).hasSize(5);
        // Article 3 in v2 has the same text as Article 4 in v1 (renumbered)
        assertThat(doc.provisions().get(2).text())
                .contains("written form");
        // Article 4 in v2 has modified deadline vs Article 5 in v1
        assertThat(doc.provisions().get(3).text())
                .contains("twenty-one days");
    }
}
