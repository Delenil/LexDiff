package com.example.lexdiff.domain;

public record Provision(
        String id,
        String label,
        NodeType type,
        String text
) {}