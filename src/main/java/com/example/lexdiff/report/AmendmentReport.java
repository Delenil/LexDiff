package com.example.lexdiff.report;

import java.util.List;

public record AmendmentReport(
        String title,
        String fromVersion,
        String toVersion,
        List<ChangeOperation> operations
) {}