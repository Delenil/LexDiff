package com.example.lexdiff.parse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDocumentLoader implements DocumentLoader {

    @Override
    public String load(String resourceOrPath) {
        try {
            return Files.readString(Path.of(resourceOrPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load document: " + resourceOrPath, e);
        }
    }
}