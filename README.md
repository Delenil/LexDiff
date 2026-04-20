# LexDiff

A CLI tool for detecting and reporting structured differences in legislative and legal texts. Unlike conventional diff tools, LexDiff respects document hierarchy, identifies provisions that were moved or renumbered, and produces evidence-based amendment reports.

## Build & Run

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests DocumentComparatorTest

# Run the application
./gradlew bootRun --args="--doc-a v1.txt --doc-b v2.txt --title 'My Law' --jurisdiction TEST"
```

### CLI Modes

```bash
# Single pair
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --doc-a old.txt --doc-b new.txt \
  --title "Some Law" --jurisdiction BG

# Batch (multiple sequential versions)
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --docs v1.txt,v2.txt,v3.txt \
  --versions v1,v2,v3 \
  --output-dir ./reports

# JSON output
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --doc-a v1.txt --doc-b v2.txt --format json --output report.json
```

## Tech Stack

- **Java 17**, **Spring Boot 4.0.3**, **Gradle**
- **JUnit 5** for all tests
- No database; purely file-based I/O

## Architecture

Five-layer pipeline:

```
Input → Parse → Diff → Move Detection → Report
```

| Package | Responsibility |
|---|---|
| `parse` | Regex-based hierarchical parsing into `LegalDocument` / `Provision` |
| `diff` | Myers' O(ND) token-level edit script computation |
| `similarity` | Jaccard-on-shingles scoring for unmatched provisions |
| `move` | Greedy best-match move/renumber detection (threshold 0.3) |
| `report` | Text and JSON renderers for `AmendmentReport` |

**`DocumentComparator`** wires the entire pipeline:
1. Match provisions by visible label.
2. Token-diff each matched pair (Myers algorithm).
3. Feed unmatched provisions into move detector.
4. Assemble `AmendmentReport` with `ChangeOperation` records.

**`LexDiffApplication`** is the Spring Boot CLI entry point.

## Key Domain Concepts

- **Provision** — atomic legal unit with an internal ID (`article-0005`) distinct from its visible label (`Art. 5`). This separation is what enables renumber detection.
- **ChangeType** — `ADD_PROVISION`, `DELETE_PROVISION`, `MODIFY_PROVISION`, `MOVE_PROVISION`, `RENUMBER_PROVISION`.
- **SegmentationProfile** — configurable regex patterns that tell the parser how to recognize articles, sections, and paragraphs for a given jurisdiction.

## Project Structure

```
src/main/java/com/example/lexdiff/
├── LexDiffApplication.java     # CLI entry, argument parsing, batch mode
├── DocumentComparator.java     # Pipeline orchestration
├── domain/                     # Immutable data model (Java records)
├── parse/                      # RegexDocumentParser, FileDocumentLoader
├── diff/                       # MyersDiffAlgorithm, WordTokenizer
├── similarity/                 # ShingleFingerprinter, JaccardSimilarityModel
├── move/                       # FingerprintMoveDetector, MoveMatch
└── report/                     # TextReportRenderer, JsonReportRenderer

src/test/java/com/example/lexdiff/
├── unit/                       # Per-component tests
├── integration/                # Full pipeline tests
└── acceptance/                 # Real-world scenario tests (thesis Table 2)

src/test/resources/fixtures/    # Statute text fixtures (EN + BG)
```

## Test Fixtures

- `statute_v1/v2.txt` — public information access statute; v2 changes "14 days" → "21 days"
- `statute_v3/v4.txt` — civil court procedure; v4 adds renumbering and a new insertion
- `statute_move_v1/v2.txt` — provisions relocated and renumbered across versions
- Bulgarian fixtures for multi-language (Cyrillic) coverage

## Extending the System

- **New output format** — implement `ReportRenderer` interface.
- **New diff algorithm** — implement `DiffAlgorithm` interface; `DocumentComparator` uses it transparently.
- **New jurisdiction** — create a `SegmentationProfile` with appropriate regex patterns.
- **Tune move detection** — adjust shingle size in `ShingleFingerprinter` or similarity threshold in `FingerprintMoveDetector`.
