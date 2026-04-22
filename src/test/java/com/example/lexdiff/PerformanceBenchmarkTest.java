package com.example.lexdiff;

import com.example.lexdiff.diff.MyersDiffAlgorithm;
import com.example.lexdiff.diff.WordTokenizer;
import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.domain.NodeType;
import com.example.lexdiff.domain.Provision;
import com.example.lexdiff.domain.Token;
import com.example.lexdiff.move.FingerprintMoveDetector;
import com.example.lexdiff.parse.FileDocumentLoader;
import com.example.lexdiff.parse.RegexDocumentParser;
import com.example.lexdiff.parse.SegmentationProfile;
import com.example.lexdiff.report.AmendmentReport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Run selectively: ./gradlew test --tests "*.PerformanceBenchmarkTest"
@Tag("benchmark")
class PerformanceBenchmarkTest {

    private static final int WARMUP_REPS  = 5;
    private static final int MEASURE_REPS = 10;

    private static final String[] VOCAB = {
        "the", "authority", "shall", "person", "provide", "request", "information",
        "public", "document", "court", "article", "paragraph", "clause", "section",
        "decision", "appeal", "administrative", "procedure", "competent", "body",
        "within", "days", "period", "law", "regulation", "right", "obligation",
        "applicant", "authority", "written", "form", "grounds", "refusal", "access",
        "party", "proceeding", "evidence", "hearing", "judgment", "enforcement"
    };

    @Test
    void benchmark_myersDiff_tokenSizeScaling() {
        int[] sizes = {50, 200, 500, 1000, 2000};
        double editFraction = 0.10;

        MyersDiffAlgorithm differ = new MyersDiffAlgorithm();

        System.out.println("\n=== Myers Diff: token-list size scaling (edit fraction = 10%) ===");
        System.out.printf("%-12s  %-14s%n", "Tokens", "Avg time (ms)");
        System.out.println("-".repeat(30));

        for (int n : sizes) {
            List<Token> a = syntheticTokens(n, 0);
            List<Token> b = editedTokens(a, editFraction, 1);

            for (int i = 0; i < WARMUP_REPS; i++) differ.diff(a, b);

            long total = 0;
            for (int i = 0; i < MEASURE_REPS; i++) {
                long t0 = System.nanoTime();
                differ.diff(a, b);
                total += System.nanoTime() - t0;
            }

            System.out.printf("%-12d  %-14.3f%n", n, total / (double) MEASURE_REPS / 1_000_000.0);
        }
    }

    @Test
    void benchmark_myersDiff_editDensityScaling() {
        int n = 500;
        double[] fractions = {0.0, 0.05, 0.10, 0.25, 0.50, 1.0};

        MyersDiffAlgorithm differ = new MyersDiffAlgorithm();
        List<Token> base = syntheticTokens(n, 0);

        System.out.println("\n=== Myers Diff: edit-density scaling (n = 500 tokens) ===");
        System.out.printf("%-14s  %-14s%n", "Edit fraction", "Avg time (ms)");
        System.out.println("-".repeat(32));

        for (double fraction : fractions) {
            List<Token> b = editedTokens(base, fraction, 42);

            for (int i = 0; i < WARMUP_REPS; i++) differ.diff(base, b);

            long total = 0;
            for (int i = 0; i < MEASURE_REPS; i++) {
                long t0 = System.nanoTime();
                differ.diff(base, b);
                total += System.nanoTime() - t0;
            }

            System.out.printf("%-14.2f  %-14.3f%n", fraction, total / (double) MEASURE_REPS / 1_000_000.0);
        }
    }

    @Test
    void benchmark_moveDetection_provisionCountScaling() {
        int[] counts = {5, 10, 20, 50, 100};

        FingerprintMoveDetector detector = new FingerprintMoveDetector();

        System.out.println("\n=== Move Detection: unmatched-provision count scaling ===");
        System.out.printf("%-18s  %-14s%n", "Provisions (D x I)", "Avg time (ms)");
        System.out.println("-".repeat(36));

        for (int n : counts) {
            List<Provision> deleted  = syntheticProvisions(n, "old", 0);
            List<Provision> inserted = editedProvisions(deleted, 0.10, "new");

            for (int i = 0; i < WARMUP_REPS; i++) detector.detect(deleted, inserted);

            long total = 0;
            for (int i = 0; i < MEASURE_REPS; i++) {
                long t0 = System.nanoTime();
                detector.detect(deleted, inserted);
                total += System.nanoTime() - t0;
            }

            System.out.printf("%-18s  %-14.3f%n", n + " x " + n, total / (double) MEASURE_REPS / 1_000_000.0);
        }
    }

    @Test
    void benchmark_fullPipeline_documentSizeScaling() {
        int[] provisionCounts = {10, 25, 50, 100, 200};
        int wordsPerProvision = 60;

        DocumentComparator comparator = new DocumentComparator();

        System.out.println("\n=== Full Pipeline: end-to-end comparison, provisions per document ===");
        System.out.printf("%-14s  %-14s  %-20s%n", "Provisions", "Avg time (ms)", "Provisions/sec");
        System.out.println("-".repeat(52));

        for (int n : provisionCounts) {
            LegalDocument docA = syntheticDocument(n, wordsPerProvision, "v1");
            LegalDocument docB = amendedDocument(docA, 0.10, 0.05, "v2");

            for (int i = 0; i < WARMUP_REPS; i++) comparator.compare(docA, docB);

            long total = 0;
            for (int i = 0; i < MEASURE_REPS; i++) {
                long t0 = System.nanoTime();
                comparator.compare(docA, docB);
                total += System.nanoTime() - t0;
            }

            double avgMs = total / (double) MEASURE_REPS / 1_000_000.0;
            System.out.printf("%-14d  %-14.3f  %-20.0f%n", n, avgMs, n / (avgMs / 1000.0));
        }
    }

    @Test
    void benchmark_realDocument_euRegulation858() {
        SegmentationProfile profile   = new SegmentationProfile("^Article\\s+\\d+", null, null);
        FileDocumentLoader  loader    = new FileDocumentLoader();
        RegexDocumentParser parser    = new RegexDocumentParser();
        DocumentComparator  comparator = new DocumentComparator();

        String pathOld = "src/main/resources/fixtures/EU_32018R0858_28-05-2024.txt";
        String pathNew = "src/main/resources/fixtures/EU_32018R0858_01-07-2024.txt";

        DocumentMetadata metaOld = new DocumentMetadata("EU Regulation 2018/858", "EU", "28-05-2024", LocalDate.of(2024, 5, 28));
        DocumentMetadata metaNew = new DocumentMetadata("EU Regulation 2018/858", "EU", "01-07-2024", LocalDate.of(2024, 7,  1));

        for (int i = 0; i < WARMUP_REPS; i++) {
            comparator.compare(
                    parser.parse(loader.load(pathOld), metaOld, profile),
                    parser.parse(loader.load(pathNew), metaNew, profile));
        }

        long parseTotal = 0;
        LegalDocument old = null, amended = null;
        for (int i = 0; i < MEASURE_REPS; i++) {
            long t0 = System.nanoTime();
            old     = parser.parse(loader.load(pathOld), metaOld, profile);
            amended = parser.parse(loader.load(pathNew), metaNew, profile);
            parseTotal += System.nanoTime() - t0;
        }

        long compareTotal = 0;
        AmendmentReport report = null;
        for (int i = 0; i < MEASURE_REPS; i++) {
            long t0 = System.nanoTime();
            report = comparator.compare(old, amended);
            compareTotal += System.nanoTime() - t0;
        }

        double parseMs   = parseTotal   / (double) MEASURE_REPS / 1_000_000.0;
        double compareMs = compareTotal / (double) MEASURE_REPS / 1_000_000.0;

        System.out.println("\n=== Real Document: EU Regulation 2018/858 (two consecutive versions) ===");
        System.out.printf("Provisions parsed (old) : %d%n",   old.provisions().size());
        System.out.printf("Provisions parsed (new) : %d%n",   amended.provisions().size());
        System.out.printf("Changes detected        : %d%n",   report.operations().size());
        System.out.printf("Avg parse time (both)   : %.3f ms%n", parseMs);
        System.out.printf("Avg comparison time     : %.3f ms%n", compareMs);
        System.out.printf("Avg total time          : %.3f ms%n", parseMs + compareMs);
    }

    private List<Token> syntheticTokens(int count, long seed) {
        Random rng = new Random(seed);
        List<Token> tokens = new ArrayList<>(count);
        for (int i = 0; i < count; i++) tokens.add(new Token(VOCAB[rng.nextInt(VOCAB.length)]));
        return tokens;
    }

    private List<Token> editedTokens(List<Token> base, double fraction, long seed) {
        Random rng = new Random(seed);
        List<Token> result = new ArrayList<>(base);
        int edits = (int) Math.round(base.size() * fraction);
        for (int i = 0; i < edits; i++) result.set(rng.nextInt(result.size()), new Token(VOCAB[rng.nextInt(VOCAB.length)]));
        return result;
    }

    private List<Provision> syntheticProvisions(int count, String versionTag, long seed) {
        Random rng = new Random(seed);
        List<Provision> provisions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            provisions.add(new Provision(
                    versionTag + "-art-" + (i + 1), "Article " + (i + 1),
                    NodeType.ARTICLE, buildText(60, rng)));
        }
        return provisions;
    }

    private List<Provision> editedProvisions(List<Provision> base, double editFraction, String versionTag) {
        Random rng = new Random(99);
        List<Provision> result = new ArrayList<>(base.size());
        for (int i = 0; i < base.size(); i++) {
            Provision p = base.get(i);
            result.add(new Provision(versionTag + "-art-" + (i + 1), p.label(), p.type(), editText(p.text(), editFraction, rng)));
        }
        return result;
    }

    private LegalDocument syntheticDocument(int provisionCount, int wordsEach, String version) {
        Random rng = new Random(version.hashCode());
        DocumentMetadata meta = new DocumentMetadata("Synthetic Statute", "TEST", version, LocalDate.of(2025, 1, 1));
        List<Provision> provisions = new ArrayList<>(provisionCount);
        for (int i = 0; i < provisionCount; i++) {
            provisions.add(new Provision(
                    version + "-art-" + (i + 1), "Article " + (i + 1),
                    NodeType.ARTICLE, buildText(wordsEach, rng)));
        }
        return new LegalDocument(meta, provisions);
    }

    private LegalDocument amendedDocument(LegalDocument base, double modifyFraction, double moveFraction, String version) {
        Random rng = new Random(77);
        List<Provision> original = base.provisions();
        int n = original.size();

        List<Provision> amended = new ArrayList<>(n);
        for (Provision p : original) {
            amended.add(new Provision(version + "-" + p.id(), p.label(), p.type(), p.text()));
        }

        int toModify = (int) Math.round(n * modifyFraction);
        for (int i = 0; i < toModify; i++) {
            int idx = rng.nextInt(n);
            Provision p = amended.get(idx);
            amended.set(idx, new Provision(p.id(), p.label(), p.type(), editText(p.text(), 0.20, rng)));
        }

        // Label-swap simulates a moved provision without changing text content.
        int toMove = (int) Math.round(n * moveFraction);
        for (int i = 0; i < toMove && i + 1 < n; i += 2) {
            int a = rng.nextInt(n), b = rng.nextInt(n);
            if (a == b) continue;
            Provision pa = amended.get(a), pb = amended.get(b);
            amended.set(a, new Provision(pa.id(), pb.label(), pa.type(), pa.text()));
            amended.set(b, new Provision(pb.id(), pa.label(), pb.type(), pb.text()));
        }

        return new LegalDocument(
                new DocumentMetadata(base.metadata().title(), base.metadata().jurisdiction(), version, LocalDate.of(2025, 6, 1)),
                amended);
    }

    private String buildText(int wordCount, Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append(VOCAB[rng.nextInt(VOCAB.length)]);
        }
        return sb.toString();
    }

    private String editText(String text, double fraction, Random rng) {
        String[] words = text.split(" ");
        int edits = (int) Math.round(words.length * fraction);
        for (int i = 0; i < edits; i++) words[rng.nextInt(words.length)] = VOCAB[rng.nextInt(VOCAB.length)];
        return String.join(" ", words);
    }
}
