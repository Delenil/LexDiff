package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;
import java.util.List;

public interface Tokenizer {
    List<Token> tokenize(String text);
}