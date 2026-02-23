package com.example.lexdiff.domain;

import java.util.List;

public record LegalDocument(
        DocumentMetadata metadata,
        List<Provision> provisions
) {}