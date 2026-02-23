package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;
import java.util.List;

public interface DiffAlgorithm {
    EditScript diff(List<Token> a, List<Token> b);
}