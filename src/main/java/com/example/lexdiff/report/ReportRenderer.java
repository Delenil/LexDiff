package com.example.lexdiff.report;

import java.io.OutputStream;

public interface ReportRenderer {
    void render(AmendmentReport report, OutputStream out);
}