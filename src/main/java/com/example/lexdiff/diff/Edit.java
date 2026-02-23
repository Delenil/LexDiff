package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;

public record Edit(EditType type, Token aToken, Token bToken) {}