package com.example.lexdiff.parse;

public record SegmentationProfile(
        String articleRegex,
        String sectionRegex,
        String paragraphRegex
) {}