package com.example.lexdiff.parse;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.*;

class FileDocumentLoaderTest {

    private final FileDocumentLoader loader = new FileDocumentLoader();

    @Test
    void loadsExistingFileAsString() {
        String content = loader.load("src/test/resources/fixtures/statute_v1.txt");

        assertThat(content).isNotBlank();
        assertThat(content).contains("Article");
    }

    @Test
    void loadedContentContainsAllArticles() {
        String content = loader.load("src/test/resources/fixtures/statute_v1.txt");

        assertThat(content).contains("Article 1");
        assertThat(content).contains("Article 2");
        assertThat(content).contains("Article 3");
        assertThat(content).contains("Article 4");
        assertThat(content).contains("Article 5");
    }

    @Test
    void throwsUncheckedIOExceptionForMissingFile() {
        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> loader.load("nonexistent/path/file.txt"))
                .withMessageContaining("Failed to load document");
    }

    @Test
    void throwsUncheckedIOExceptionForEmptyPath() {
        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> loader.load(""));
    }

    @Test
    void v1AndV2FixturesHaveDifferentContent() {
        String v1 = loader.load("src/test/resources/fixtures/statute_v1.txt");
        String v2 = loader.load("src/test/resources/fixtures/statute_v2.txt");

        assertThat(v1).isNotEqualTo(v2);
    }
}
