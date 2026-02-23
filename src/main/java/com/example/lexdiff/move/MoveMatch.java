package com.example.lexdiff.move;

public record MoveMatch(String oldId, String newId, double score, MoveType type) {}