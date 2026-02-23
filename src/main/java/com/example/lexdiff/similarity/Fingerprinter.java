package com.example.lexdiff.similarity;

public interface Fingerprinter<T> {
    Fingerprint fingerprint(T input);
}