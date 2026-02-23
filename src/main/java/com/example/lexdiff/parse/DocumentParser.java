package com.example.lexdiff.parse;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;

public interface DocumentParser {
    LegalDocument parse(String rawText, DocumentMetadata metadata, SegmentationProfile profile);
}