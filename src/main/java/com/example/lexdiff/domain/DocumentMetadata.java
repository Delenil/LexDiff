package com.example.lexdiff.domain;

import java.time.LocalDate;

public record DocumentMetadata(
        String title,
        String jurisdiction,
        String versionLabel,
        LocalDate versionDate
) {}