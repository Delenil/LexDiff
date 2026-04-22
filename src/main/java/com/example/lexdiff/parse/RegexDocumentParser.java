package com.example.lexdiff.parse;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RegexDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(RegexDocumentParser.class);

    // Scans line-by-line: heading lines start a new provision; body lines accumulate until the next heading.
    @Override
    public LegalDocument parse(String rawText, DocumentMetadata metadata, SegmentationProfile profile) {
        Map<Pattern, NodeType> patternTypeMap = buildPatternMap(profile);

        List<Provision> provisions = new ArrayList<>();
        String[] lines = rawText.split("\r?\n", -1);

        String currentLabel = null;
        NodeType currentType = null;
        List<String> bodyLines = new ArrayList<>();
        int provisionIndex = 0;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            NodeType matched = firstMatch(line, patternTypeMap);

            if (matched != null) {
                if (currentLabel != null) {
                    provisions.add(buildProvision(provisionIndex++, currentLabel, currentType, bodyLines));
                }
                currentLabel = line;
                currentType = matched;
                bodyLines = new ArrayList<>();
            } else if (currentLabel != null && !line.isEmpty()) {
                bodyLines.add(line);
            }
            // lines before the first heading are intentionally ignored
        }

        // flush the last open provision
        if (currentLabel != null) {
            provisions.add(buildProvision(provisionIndex, currentLabel, currentType, bodyLines));
        }

        if (provisions.isEmpty()) {
            throw new IllegalArgumentException(
                    "No provisions found in document '" + metadata.title() + "'. " +
                    "Check that the SegmentationProfile patterns match the document's headings.");
        }

        provisions.stream()
                .collect(Collectors.groupingBy(Provision::label, Collectors.counting()))
                .forEach((label, count) -> {
                    if (count > 1) {
                        log.warn("Duplicate label '{}' found {} times in '{}'. " +
                                "Label-based cross-version matching may be unreliable for this provision.",
                                label, count, metadata.title());
                    }
                });

        return new LegalDocument(metadata, Collections.unmodifiableList(provisions));
    }

    // --- helpers ---

    private Map<Pattern, NodeType> buildPatternMap(SegmentationProfile profile) {
        Map<Pattern, NodeType> map = new LinkedHashMap<>();
        addPattern(map, profile.articleRegex(),   NodeType.ARTICLE);
        addPattern(map, profile.sectionRegex(),   NodeType.SECTION);
        addPattern(map, profile.paragraphRegex(), NodeType.PARAGRAPH);
        return map;
    }

    private void addPattern(Map<Pattern, NodeType> map, String regex, NodeType type) {
        if (regex != null && !regex.isBlank()) {
            map.put(Pattern.compile(regex), type);
        }
    }

    private NodeType firstMatch(String line, Map<Pattern, NodeType> patternTypeMap) {
        for (Map.Entry<Pattern, NodeType> entry : patternTypeMap.entrySet()) {
            if (entry.getKey().matcher(line).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Provision buildProvision(int index, String label, NodeType type, List<String> bodyLines) {
        String id   = type.name().toLowerCase() + "-" + String.format("%04d", index);
        String text = String.join(" ", bodyLines).strip();
        return new Provision(id, label, type, text);
    }
}