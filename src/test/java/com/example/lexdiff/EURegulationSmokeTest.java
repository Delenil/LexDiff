package com.example.lexdiff;

import com.example.lexdiff.domain.DocumentMetadata;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.parse.FileDocumentLoader;
import com.example.lexdiff.parse.RegexDocumentParser;
import com.example.lexdiff.parse.SegmentationProfile;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.TextReportRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EURegulationSmokeTest {

    private static final SegmentationProfile PROFILE =
            new SegmentationProfile("^Article\\s+\\d+", null, null);

    @Test
    void euRegulation858_parsesAndComparesSuccessfully() {
        FileDocumentLoader loader = new FileDocumentLoader();
        RegexDocumentParser parser = new RegexDocumentParser();

        LegalDocument old = parser.parse(
                loader.load("src/main/resources/fixtures/EURegulation_858_old.txt"),
                new DocumentMetadata("EU Regulation 2018/858", "EU", "original", LocalDate.of(2018, 5, 30)),
                PROFILE);
        LegalDocument amended = parser.parse(
                loader.load("src/main/resources/fixtures/EURegulation_858_new.txt"),
                new DocumentMetadata("EU Regulation 2018/858", "EU", "amended", LocalDate.of(2024, 1, 1)),
                PROFILE);

        AmendmentReport report = new DocumentComparator().compare(old, amended);

        assertTrue(old.provisions().size() > 0);
        assertTrue(amended.provisions().size() > 0);
        assertFalse(report.operations().isEmpty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new TextReportRenderer().render(report, out);
        assertTrue(out.toString().contains("Amendment Report"));
    }
}
