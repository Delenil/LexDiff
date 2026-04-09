package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class WordTokenizerTest {

    private WordTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new WordTokenizer();
    }

    @Test
    void tokenizesPlainWords() {
        List<Token> tokens = tokenizer.tokenize("Citizens shall have the right");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("Citizens", "shall", "have", "the", "right");
    }

    @Test
    void separatesPunctuationFromWords() {
        List<Token> tokens = tokenizer.tokenize("within thirty days, of request.");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("within", "thirty", "days", ",", "of", "request", ".");
    }

    @Test
    void treatsHyphenatedWordAsSingleToken() {
        List<Token> tokens = tokenizer.tokenize("twenty-one days non-binding");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("twenty-one", "days", "non-binding");
    }

    @Test
    void preservesOriginalCase() {
        List<Token> tokens = tokenizer.tokenize("Citizens SHALL have THE right");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("Citizens", "SHALL", "have", "THE", "right");
    }

    @Test
    void collapsesWhitespaceAsSeparatorOnly() {
        List<Token> tokens = tokenizer.tokenize("word   another\tword");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("word", "another", "word");
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(tokenizer.tokenize("")).isEmpty();
        assertThat(tokenizer.tokenize("   ")).isEmpty();
    }

    @Test
    void returnsEmptyListForNullText() {
        assertThat(tokenizer.tokenize(null)).isEmpty();
    }

    @Test
    void tokenizesSemicolonAndColon() {
        List<Token> tokens = tokenizer.tokenize("Article 5; Section 2: body");

        assertThat(tokens).extracting(Token::text)
                .containsExactly("Article", "5", ";", "Section", "2", ":", "body");
    }

    @Test
    void resultIsUnmodifiable() {
        List<Token> tokens = tokenizer.tokenize("some text");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> tokens.add(new Token("extra")));
    }
}
