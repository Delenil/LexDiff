package com.example.lexdiff.similarity;

public interface SimilarityModel<T> {
    double similarity(T a, T b);
}