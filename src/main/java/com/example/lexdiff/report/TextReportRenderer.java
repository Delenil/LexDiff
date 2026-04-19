package com.example.lexdiff.report;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class TextReportRenderer implements ReportRenderer {

    @Override
    public void render(AmendmentReport report, OutputStream out) {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);

        w.println("=== Amendment Report ===");
        w.println("Title   : " + report.title());
        w.println("From    : " + report.fromVersion());
        w.println("To      : " + report.toVersion());
        w.println("Changes : " + report.operations().size());
        w.println();

        for (ChangeOperation op : report.operations()) {
            w.println("[" + op.type() + "]");
            if (op.beforeId() != null) w.println("  before : " + op.beforeId());
            if (op.afterId()  != null) w.println("  after  : " + op.afterId());
            if (op.type() == ChangeType.MOVE_PROVISION || op.type() == ChangeType.RENUMBER_PROVISION) {
                w.printf("  confidence : %.2f%n", op.confidence());
            }
            if (!op.evidenceSnippets().isEmpty()) {
                w.println("  evidence:");
                for (String snippet : op.evidenceSnippets()) {
                    w.println("    - " + snippet);
                }
            }
            w.println();
        }
    }
}
