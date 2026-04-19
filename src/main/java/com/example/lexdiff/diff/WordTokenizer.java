package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a normalized text string into a sequence of {@link Token} objects.
 *
 * <p>Each token is either:
 * <ul>
 *   <li>a <em>word token</em> — one or more word characters, optionally joined by
 *       hyphens (e.g. {@code twenty-one}, {@code non-binding}), or</li>
 *   <li>a <em>punctuation token</em> — any single non-whitespace, non-word character
 *       (e.g. {@code ,}, {@code .}, {@code ;}).</li>
 * </ul>
 *
 * <p>Whitespace is used as a delimiter and is never emitted as a token.
 * Original casing is preserved so that evidence snippets in the amendment report
 * remain readable and traceable to the source text.
 */
public class WordTokenizer implements Tokenizer {

    // Matches a word (possibly hyphenated) OR a single punctuation character.
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\\w+(?:-\\w+)*|[^\\w\\s]", Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public List<Token> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(text);
        while (m.find()) {
            tokens.add(new Token(m.group()));
        }
        return Collections.unmodifiableList(tokens);
    }
}
