# LexDiff

A command-line tool that compares two versions of a legal document and reports what changed — which provisions were added, removed, modified, moved, or renumbered.

Unlike a standard `diff`, LexDiff understands legal document structure. It knows that a provision moving from Article 4 to Article 7 is a *move*, not a deletion and an unrelated insertion. It also pinpoints exactly which words changed inside a provision, rather than just flagging the whole block as different.

Built as a senior project at AUBG.

---

## Getting Started

**Requirements:** Java 17, Gradle (the wrapper is included so you don't need to install it separately).

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests DocumentComparatorTest
```

---

## Running LexDiff

### Compare two versions of a document

```bash
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --doc-a old_version.txt \
  --doc-b new_version.txt \
  --title "Law on Public Information" \
  --jurisdiction BG
```

### Compare multiple sequential versions at once

```bash
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --docs v1.txt,v2.txt,v3.txt \
  --versions v1,v2,v3 \
  --output-dir ./reports
```

This produces one report per consecutive pair (`v1-v2.txt`, `v2-v3.txt`).

### Get JSON output instead of plain text

```bash
java -jar build/libs/LexDiff-0.0.1-SNAPSHOT.jar \
  --doc-a v1.txt --doc-b v2.txt \
  --format json \
  --output report.json
```

### All available options

| Option | Description | Default |
|---|---|---|
| `--doc-a` | Path to the older document | required |
| `--doc-b` | Path to the newer document | required |
| `--docs` | Comma-separated list of paths (batch mode) | — |
| `--versions` | Version labels matching `--docs` | auto (v1, v2, …) |
| `--title` | Document title shown in the report | Untitled |
| `--jurisdiction` | Jurisdiction code shown in the report | N/A |
| `--article-regex` | Regex to detect article headings | `^Article\s+\d+` |
| `--section-regex` | Regex to detect section headings | — |
| `--paragraph-regex` | Regex to detect paragraph headings | — |
| `--format` | `text` or `json` | text |
| `--output` | Write report to a file instead of stdout | — |
| `--output-dir` | Directory for batch report files | — |

---

## How It Works

The comparison runs through five stages:

```
Input → Parse → Diff → Move Detection → Report
```

1. **Input** — reads the raw text files from disk.
2. **Parse** — scans each document line by line, using regex patterns to detect headings like "Article 1". Each heading starts a new *provision* (the basic unit of comparison). Every provision gets a stable internal ID that is separate from its visible label, which is what makes renumbering detectable.
3. **Diff** — for provisions that exist in both versions under the same label, the tool runs Myers' algorithm to find the minimum set of word-level changes. This is the same algorithm used by `git diff`.
4. **Move Detection** — provisions that disappeared from one version and appeared in the other are compared using a shingle-based similarity score (Jaccard similarity on trigrams). If the score exceeds 0.3, they are considered the same provision that was moved or renumbered.
5. **Report** — all detected changes are assembled into an `AmendmentReport` and rendered as plain text or JSON.

### Change types

| Type | Meaning |
|---|---|
| `ADD_PROVISION` | A provision appears in the new version with no match in the old one |
| `DELETE_PROVISION` | A provision from the old version has no match in the new one |
| `MODIFY_PROVISION` | Same label in both versions, but the text changed |
| `MOVE_PROVISION` | High textual similarity to a deleted provision, different position |
| `RENUMBER_PROVISION` | Same as MOVE but the label also changed |

---

## Project Structure

```
src/main/java/com/example/lexdiff/
├── LexDiffApplication.java     # entry point, argument parsing, batch mode
├── DocumentComparator.java     # orchestrates the full pipeline
├── domain/                     # data model: Provision, LegalDocument, Token, ...
├── parse/                      # RegexDocumentParser, FileDocumentLoader
├── diff/                       # MyersDiffAlgorithm, WordTokenizer
├── similarity/                 # ShingleFingerprinter, JaccardSimilarityModel
├── move/                       # FingerprintMoveDetector
└── report/                     # TextReportRenderer, JsonReportRenderer

src/test/java/com/example/lexdiff/
├── DocumentComparatorTest.java     # unit tests for the comparator
├── IntegrationTest.java            # full pipeline with fixture files
├── AcceptanceTest.java             # scenario tests from thesis Table 2
├── EURegulationSmokeTest.java      # real EU regulation as a smoke test
├── PerformanceBenchmarkTest.java   # timing benchmarks
├── diff/                           # unit tests for Myers diff and tokenizer
├── move/                           # unit tests for move detection
├── parse/                          # unit tests for parsing and file loading
├── report/                         # unit tests for text and JSON renderers
└── similarity/                     # unit tests for fingerprinting and Jaccard

src/test/resources/fixtures/        # statute text files used in tests
```

---

## Test Fixtures

The project includes two sets of fixture files — short synthetic statutes for unit and acceptance tests, and longer real-world documents for smoke tests and benchmarks.

### Synthetic statutes (`src/test/resources/fixtures/`)

These are short made-up statutes written specifically to test particular scenarios:

- `statute_v1.txt` / `statute_v2.txt` — a public information access statute; v2 changes "fourteen days" to "twenty-one days" in one article, leaving everything else the same
- `statute_v3.txt` / `statute_v4.txt` — a civil court procedure statute; v4 inserts a brand new article and renumbers an existing one
- `statute_move_v1.txt` / `statute_move_v2.txt` — a statute where several provisions physically relocate to different positions between versions

### Real-world documents (`src/main/resources/fixtures/`)

These are actual legal texts used to verify the tool works on real input:

- `EURegulation_858_old.txt` / `EURegulation_858_new.txt` — EU Regulation 2018/858 on motor vehicle approval; used in the smoke test to confirm the full pipeline runs without errors on a real regulation
- `EU_32018R0858_28-05-2024.txt` / `EU_32018R0858_01-07-2024.txt` — two consecutive dated versions of the same EU regulation; used in the performance benchmarks
- `uncitral_arbitration_rules_2010_articles_1_2.txt` / `uncitral_arbitration_rules_2013_articles_1_2.txt` — Articles 1 and 2 of the UNCITRAL Arbitration Rules across two editions (2010 and 2013)
- `bulgarian_statutes_old.txt` / `bulgarian_statutes_revised.txt` — a paired corpus of Bulgarian statutes before and after amendment, used to test multi-language (Cyrillic) support
- `нк_преди.txt` / `нк_след.txt` — the Bulgarian Criminal Code (Наказателен кодекс) before and after amendments published in Official Gazette issues 19/2012 and 74/2015

---

## Tech Stack

- **Java 17**
- **Spring Boot 4.0.3** (used for the CLI runner and application wiring)
- **Gradle** (build tool)
- **JUnit 5** (all tests)
- No database — purely file-based input and output

---

## Extending the Tool

The main components are all behind interfaces, so swapping them out is straightforward:

- **New output format** → implement `ReportRenderer`
- **New diff algorithm** → implement `DiffAlgorithm`
- **New jurisdiction** → create a `SegmentationProfile` with the right regex patterns
- **Tune move sensitivity** → adjust the similarity threshold in `FingerprintMoveDetector` (default: 0.3) or the shingle size in `ShingleFingerprinter` (default: 3)
