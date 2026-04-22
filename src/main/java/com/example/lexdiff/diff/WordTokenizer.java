package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordTokenizer implements Tokenizer {

    // hyphenated word (e.g. "twenty-one") or a single punctuation character
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
