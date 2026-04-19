package com.example.lexdiff.report;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class JsonReportRenderer implements ReportRenderer {

    @Override
    public void render(AmendmentReport report, OutputStream out) {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);

        w.println("{");
        w.println("  \"title\": " + quoted(report.title()) + ",");
        w.println("  \"fromVersion\": " + quoted(report.fromVersion()) + ",");
        w.println("  \"toVersion\": " + quoted(report.toVersion()) + ",");
        List<ChangeOperation> ops = report.operations();

        if (ops.isEmpty()) {
            w.println("  \"operations\": []");
            w.println("}");
            return;
        }

        w.println("  \"operations\": [");

        for (int i = 0; i < ops.size(); i++) {
            ChangeOperation op = ops.get(i);
            w.println("    {");
            w.println("      \"type\": " + quoted(op.type().name()) + ",");
            w.println("      \"beforeId\": " + quoted(op.beforeId()) + ",");
            w.println("      \"afterId\": " + quoted(op.afterId()) + ",");
            w.printf ("      \"confidence\": %.4f,%n", op.confidence());
            w.println("      \"evidenceSnippets\": " + jsonArray(op.evidenceSnippets()));
            w.print  ("    }");
            w.println(i < ops.size() - 1 ? "," : "");
        }

        w.println("  ]");
        w.println("}");
    }

    private String quoted(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private String jsonArray(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(quoted(items.get(i)));
            if (i < items.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
