package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MyersDiffAlgorithmTest {

    private MyersDiffAlgorithm diff;
    private WordTokenizer       tokenizer;

    @BeforeEach
    void setUp() {
        diff      = new MyersDiffAlgorithm();
        tokenizer = new WordTokenizer();
    }

    // --- helpers -----------------------------------------------------------

    private List<Token> tok(String text) {
        return tokenizer.tokenize(text);
    }

    private long countType(EditScript script, EditType type) {
        return script.edits().stream().filter(e -> e.type() == type).count();
    }

    // --- trivial cases -----------------------------------------------------

    @Test
    void identicalSequencesProduceAllEquals() {
        List<Token> a = tok("Citizens shall have the right");
        EditScript  s = diff.diff(a, a);

        assertThat(s.edits()).hasSize(5);
        assertThat(countType(s, EditType.EQUAL)).isEqualTo(5);
        assertThat(countType(s, EditType.INSERT)).isZero();
        assertThat(countType(s, EditType.DELETE)).isZero();
    }

    @Test
    void emptyVsEmptyProducesEmptyScript() {
        EditScript s = diff.diff(List.of(), List.of());

        assertThat(s.edits()).isEmpty();
    }

    @Test
    void emptyAProducesAllInserts() {
        List<Token> b = tok("three new words");
        EditScript  s = diff.diff(List.of(), b);

        assertThat(s.edits()).hasSize(3);
        assertThat(countType(s, EditType.INSERT)).isEqualTo(3);
        assertThat(countType(s, EditType.DELETE)).isZero();
        assertThat(countType(s, EditType.EQUAL)).isZero();
    }

    @Test
    void emptyBProducesAllDeletes() {
        List<Token> a = tok("three old words");
        EditScript  s = diff.diff(a, List.of());

        assertThat(s.edits()).hasSize(3);
        assertThat(countType(s, EditType.DELETE)).isEqualTo(3);
        assertThat(countType(s, EditType.INSERT)).isZero();
        assertThat(countType(s, EditType.EQUAL)).isZero();
    }

    // --- single-token changes ----------------------------------------------

    @Test
    void singleWordInsertedAtEnd() {
        List<Token> a = tok("Citizens shall have the right");
        List<Token> b = tok("Citizens shall have the right today");
        EditScript  s = diff.diff(a, b);

        assertThat(countType(s, EditType.INSERT)).isEqualTo(1);
        assertThat(countType(s, EditType.DELETE)).isZero();
        assertThat(s.edits().get(s.edits().size() - 1).type()).isEqualTo(EditType.INSERT);
        assertThat(s.edits().get(s.edits().size() - 1).bToken().text()).isEqualTo("today");
    }

    @Test
    void singleWordDeletedAtStart() {
        List<Token> a = tok("All Citizens shall have the right");
        List<Token> b = tok("Citizens shall have the right");
        EditScript  s = diff.diff(a, b);

        assertThat(countType(s, EditType.DELETE)).isEqualTo(1);
        assertThat(countType(s, EditType.INSERT)).isZero();
        assertThat(s.edits().get(0).type()).isEqualTo(EditType.DELETE);
        assertThat(s.edits().get(0).aToken().text()).isEqualTo("All");
    }

    @Test
    void singleWordSubstitutedInMiddle() {
        // "thirty" → "twenty-one": one delete + one insert
        List<Token> a = tok("within thirty days of request");
        List<Token> b = tok("within twenty-one days of request");
        EditScript  s = diff.diff(a, b);

        assertThat(countType(s, EditType.DELETE)).isEqualTo(1);
        assertThat(countType(s, EditType.INSERT)).isEqualTo(1);
        assertThat(countType(s, EditType.EQUAL)).isEqualTo(4); // within, days, of, request
    }

    // --- edit-script ordering and content ----------------------------------

    @Test
    void editScriptIsInForwardOrder() {
        // a: A B C D
        // b: A X C D  → delete B, insert X between A and C
        List<Token> a = tok("A B C D");
        List<Token> b = tok("A X C D");
        EditScript  s = diff.diff(a, b);

        List<Edit> edits = s.edits();

        assertThat(edits.get(0).type()).isEqualTo(EditType.EQUAL);   // A
        assertThat(edits.get(0).aToken().text()).isEqualTo("A");

        // DELETE B and INSERT X can appear in either order between the EQUALs
        long deletes = countType(s, EditType.DELETE);
        long inserts = countType(s, EditType.INSERT);
        assertThat(deletes).isEqualTo(1);
        assertThat(inserts).isEqualTo(1);

        assertThat(edits.get(edits.size() - 2).type()).isEqualTo(EditType.EQUAL); // C
        assertThat(edits.get(edits.size() - 1).type()).isEqualTo(EditType.EQUAL); // D
    }

    @Test
    void reconstructedOutputMatchesB() {
        // Applying the edit script to a should yield b.
        String textA = "The purpose of this statute is to establish general rules";
        String textB = "The purpose of this act is to establish clear and general rules";
        List<Token> a = tok(textA);
        List<Token> b = tok(textB);
        EditScript  s = diff.diff(a, b);

        // Reconstruct b from the edit script
        StringBuilder reconstructed = new StringBuilder();
        for (Edit e : s.edits()) {
            if (e.type() == EditType.EQUAL || e.type() == EditType.INSERT) {
                if (!reconstructed.isEmpty()) reconstructed.append(' ');
                reconstructed.append(e.bToken().text());
            }
        }

        // Reconstruct b directly for comparison
        StringBuilder expected = new StringBuilder();
        for (Token t : b) {
            if (!expected.isEmpty()) expected.append(' ');
            expected.append(t.text());
        }

        assertThat(reconstructed.toString()).isEqualTo(expected.toString());
    }

    @Test
    void editCountIsMinimal() {
        // "thirty days" → "twenty-one days": minimum is 1 delete + 1 insert = D=2
        List<Token> a = tok("within thirty days");
        List<Token> b = tok("within twenty-one days");
        EditScript  s = diff.diff(a, b);

        long nonEqual = s.edits().stream().filter(e -> e.type() != EditType.EQUAL).count();
        assertThat(nonEqual).isEqualTo(2); // 1 delete + 1 insert
    }

    // --- legal text integration --------------------------------------------

    @Test
    void detectsAmendmentBetweenProvisionVersions() {
        // Article 5 v1 → Article 4 v2: "fourteen" replaced by "twenty-one"
        String v1 = "Appeals against administrative decisions shall be filed within "
                  + "fourteen days of notification to the affected party";
        String v2 = "Appeals against administrative decisions shall be filed within "
                  + "twenty-one days of notification to the affected party";

        EditScript s = diff.diff(tok(v1), tok(v2));

        assertThat(countType(s, EditType.DELETE)).isEqualTo(1);
        assertThat(countType(s, EditType.INSERT)).isEqualTo(1);

        Edit deleted = s.edits().stream()
                .filter(e -> e.type() == EditType.DELETE).findFirst().orElseThrow();
        Edit inserted = s.edits().stream()
                .filter(e -> e.type() == EditType.INSERT).findFirst().orElseThrow();

        assertThat(deleted.aToken().text()).isEqualTo("fourteen");
        assertThat(inserted.bToken().text()).isEqualTo("twenty-one");
    }
}
