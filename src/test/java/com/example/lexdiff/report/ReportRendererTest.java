package com.example.lexdiff.report;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportRendererTest {

    private AmendmentReport sampleReport() {
        List<ChangeOperation> ops = List.of(
                new ChangeOperation(ChangeType.ADD_PROVISION,    null,      "a-0002", 1.0, List.of("New article text.")),
                new ChangeOperation(ChangeType.DELETE_PROVISION, "a-0001",  null,     1.0, List.of()),
                new ChangeOperation(ChangeType.MODIFY_PROVISION, "a-0003",  "a-0003", 1.0, List.of("before snippet", "after snippet")),
                new ChangeOperation(ChangeType.MOVE_PROVISION,   "a-0004",  "a-0004", 0.87, List.of()),
                new ChangeOperation(ChangeType.RENUMBER_PROVISION,"a-0005", "a-0006", 0.95, List.of())
        );
        return new AmendmentReport("Test Statute", "v1", "v2", ops);
    }

    @Test
    void textRendererContainsHeader() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(sampleReport(), out);
        String text = out.toString();

        assertTrue(text.contains("Amendment Report"));
        assertTrue(text.contains("Test Statute"));
        assertTrue(text.contains("v1"));
        assertTrue(text.contains("v2"));
    }

    @Test
    void textRendererContainsAllChangeTypes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(sampleReport(), out);
        String text = out.toString();

        assertTrue(text.contains("ADD_PROVISION"));
        assertTrue(text.contains("DELETE_PROVISION"));
        assertTrue(text.contains("MODIFY_PROVISION"));
        assertTrue(text.contains("MOVE_PROVISION"));
        assertTrue(text.contains("RENUMBER_PROVISION"));
    }

    @Test
    void textRendererShowsEvidenceSnippets() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(sampleReport(), out);
        String text = out.toString();

        assertTrue(text.contains("before snippet"));
        assertTrue(text.contains("after snippet"));
    }

    @Test
    void textRendererShowsConfidenceForMoveAndRenumber() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(sampleReport(), out);
        String text = out.toString();

        assertTrue(text.contains("0.87") || text.contains("0,87"));
        assertTrue(text.contains("0.95") || text.contains("0,95"));
    }

    @Test
    void jsonRendererProducesValidStructure() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JsonReportRenderer().render(sampleReport(), out);
        String json = out.toString();

        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}" + System.lineSeparator()) || json.endsWith("}"));
        assertTrue(json.contains("\"title\""));
        assertTrue(json.contains("\"fromVersion\""));
        assertTrue(json.contains("\"toVersion\""));
        assertTrue(json.contains("\"operations\""));
    }

    @Test
    void jsonRendererContainsAllOperations() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JsonReportRenderer().render(sampleReport(), out);
        String json = out.toString();

        assertTrue(json.contains("ADD_PROVISION"));
        assertTrue(json.contains("DELETE_PROVISION"));
        assertTrue(json.contains("MODIFY_PROVISION"));
        assertTrue(json.contains("MOVE_PROVISION"));
        assertTrue(json.contains("RENUMBER_PROVISION"));
    }

    @Test
    void jsonRendererEscapesSpecialCharacters() {
        List<ChangeOperation> ops = List.of(
                new ChangeOperation(ChangeType.ADD_PROVISION, null, "a-0001", 1.0,
                        List.of("text with \"quotes\" and \\backslash"))
        );
        AmendmentReport report = new AmendmentReport("My \"Statute\"", "v1", "v2", ops);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JsonReportRenderer().render(report, out);
        String json = out.toString();

        assertTrue(json.contains("\\\"quotes\\\""));
        assertTrue(json.contains("\\\\backslash"));
    }

    @Test
    void jsonRendererEscapesNewlinesInEvidenceSnippets() {
        List<ChangeOperation> ops = List.of(
                new ChangeOperation(ChangeType.MODIFY_PROVISION, "a-0001", "a-0001", 1.0,
                        List.of("line one\nline two"))
        );
        AmendmentReport report = new AmendmentReport("Statute", "v1", "v2", ops);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JsonReportRenderer().render(report, out);
        String json = out.toString();

        assertTrue(json.contains("\\n"), "Newlines in evidence snippets must be escaped as \\n in JSON");
        assertFalse(json.contains("\"\nline"), "Raw newlines must not appear inside JSON string values");
    }

    @Test
    void emptyOperationsListRendersCorrectly() {
        AmendmentReport report = new AmendmentReport("Empty", "v1", "v2", List.of());

        ByteArrayOutputStream textOut = new ByteArrayOutputStream();
        new TextReportRenderer().render(report, textOut);
        assertTrue(textOut.toString().contains("Changes : 0"));

        ByteArrayOutputStream jsonOut = new ByteArrayOutputStream();
        new JsonReportRenderer().render(report, jsonOut);
        assertTrue(jsonOut.toString().contains("\"operations\": []"));
    }
}
