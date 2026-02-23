package com.example.lexdiff.report;

import java.util.List;

public record ChangeOperation(
        ChangeType type,
        String beforeId,
        String afterId,
        double confidence,
        List<String> evidenceSnippets
) {}