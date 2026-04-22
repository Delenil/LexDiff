package com.example.lexdiff;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.parse.FileDocumentLoader;
import com.example.lexdiff.parse.RegexDocumentParser;
import com.example.lexdiff.parse.SegmentationProfile;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.JsonReportRenderer;
import com.example.lexdiff.report.ReportRenderer;
import com.example.lexdiff.report.TextReportRenderer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class LexDiffApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(LexDiffApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Map<String, String> params = parseArgs(args);

        String title          = params.getOrDefault("title", "Untitled");
        String jurisdiction   = params.getOrDefault("jurisdiction", "N/A");
        String articleRegex   = params.getOrDefault("article-regex", "^Article\\s+\\d+");
        String sectionRegex   = params.getOrDefault("section-regex", null);
        String paragraphRegex = params.getOrDefault("paragraph-regex", null);
        String format         = params.getOrDefault("format", "text");

        FileDocumentLoader  loader   = new FileDocumentLoader();
        RegexDocumentParser parser   = new RegexDocumentParser();
        SegmentationProfile profile  = new SegmentationProfile(articleRegex, sectionRegex, paragraphRegex);
        ReportRenderer      renderer = format.equalsIgnoreCase("json")
                ? new JsonReportRenderer()
                : new TextReportRenderer();

        // batch mode
        String docsParam = params.get("docs");
        if (docsParam != null) {
            runBatch(docsParam, params, title, jurisdiction, profile, loader, parser, renderer, format);
            return;
        }

        // single-pair mode
        String docA      = params.get("doc-a");
        String docB      = params.get("doc-b");
        String versionA  = params.getOrDefault("version-a", "v1");
        String versionB  = params.getOrDefault("version-b", "v2");
        String outputPath = params.get("output");

        if (docA == null || docB == null) {
            System.err.println("Error: either --docs or both --doc-a and --doc-b are required.");
            printUsage();
            return;
        }

        DocumentMetadata metaA = new DocumentMetadata(title, jurisdiction, versionA, LocalDate.now());
        DocumentMetadata metaB = new DocumentMetadata(title, jurisdiction, versionB, LocalDate.now());

        LegalDocument docOld = parser.parse(loader.load(docA), metaA, profile);
        LegalDocument docNew = parser.parse(loader.load(docB), metaB, profile);

        AmendmentReport report = new DocumentComparator().compare(docOld, docNew);

        OutputStream out = (outputPath != null) ? new FileOutputStream(outputPath) : System.out;
        try {
            renderer.render(report, out);
        } finally {
            if (outputPath != null) out.close();
        }
    }

    private void runBatch(String docsParam,
                          Map<String, String> params,
                          String title,
                          String jurisdiction,
                          SegmentationProfile profile,
                          FileDocumentLoader loader,
                          RegexDocumentParser parser,
                          ReportRenderer renderer,
                          String format) throws Exception {

        String[] paths   = docsParam.split(",");
        String versionsParam = params.get("versions");
        String[] labels  = (versionsParam != null)
                ? versionsParam.split(",")
                : generateLabels(paths.length);
        String outputDir = params.get("output-dir");

        if (paths.length < 2) {
            System.err.println("Error: --docs requires at least two comma-separated paths.");
            return;
        }
        if (labels.length != paths.length) {
            System.err.println("Error: --versions count (" + labels.length +
                    ") must match --docs count (" + paths.length + ").");
            return;
        }

        List<LegalDocument> documents = new ArrayList<>();
        for (int i = 0; i < paths.length; i++) {
            DocumentMetadata meta = new DocumentMetadata(title, jurisdiction, labels[i].trim(), LocalDate.now());
            documents.add(parser.parse(loader.load(paths[i].trim()), meta, profile));
        }

        if (outputDir != null) {
            Files.createDirectories(Path.of(outputDir));
        }

        String extension = format.equalsIgnoreCase("json") ? "json" : "txt";
        PrintStream separator = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        for (int i = 0; i < documents.size() - 1; i++) {
            AmendmentReport report = new DocumentComparator().compare(documents.get(i), documents.get(i + 1));

            if (outputDir != null) {
                String fileName = labels[i].trim() + "-" + labels[i + 1].trim() + "." + extension;
                try (OutputStream out = new FileOutputStream(outputDir + "/" + fileName)) {
                    renderer.render(report, out);
                }
                System.out.println("Written: " + outputDir + "/" + fileName);
            } else {
                if (i > 0) separator.println("\n---\n");
                renderer.render(report, System.out);
            }
        }
    }

    private String[] generateLabels(int count) {
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            labels[i] = "v" + (i + 1);
        }
        return labels;
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                params.put(args[i].substring(2), args[i + 1]);
                i++;
            }
        }
        return params;
    }

    private void printUsage() {
        System.out.println("Usage:");
        System.out.println("  Single pair:  lexdiff --doc-a <path> --doc-b <path> [options]");
        System.out.println("  Multi-version: lexdiff --docs <p1,p2,...> --versions <v1,v2,...> [options]");
        System.out.println();
        System.out.println("Single-pair options:");
        System.out.println("  --doc-a <path>            path to the older document version");
        System.out.println("  --doc-b <path>            path to the newer document version");
        System.out.println("  --version-a <label>       version label for doc-a (default: v1)");
        System.out.println("  --version-b <label>       version label for doc-b (default: v2)");
        System.out.println("  --output <path>           write report to file instead of stdout");
        System.out.println();
        System.out.println("Multi-version options:");
        System.out.println("  --docs <p1,p2,...>        comma-separated ordered list of document paths");
        System.out.println("  --versions <v1,v2,...>    matching version labels (auto-generated if omitted)");
        System.out.println("  --output-dir <dir>        write one report file per pair into this directory");
        System.out.println();
        System.out.println("Shared options:");
        System.out.println("  --title <title>           document title (default: Untitled)");
        System.out.println("  --jurisdiction <code>     jurisdiction code (default: N/A)");
        System.out.println("  --article-regex <regex>   heading pattern (default: ^Article\\s+\\d+)");
        System.out.println("  --section-regex <regex>   section pattern (optional)");
        System.out.println("  --paragraph-regex <regex> paragraph pattern (optional)");
        System.out.println("  --format <text|json>      output format (default: text)");
    }
}
