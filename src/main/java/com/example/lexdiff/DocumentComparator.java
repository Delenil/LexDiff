package com.example.lexdiff;

import com.example.lexdiff.diff.Edit;
import com.example.lexdiff.diff.EditType;
import com.example.lexdiff.diff.MyersDiffAlgorithm;
import com.example.lexdiff.diff.WordTokenizer;
import com.example.lexdiff.domain.LegalDocument;
import com.example.lexdiff.domain.Provision;
import com.example.lexdiff.move.FingerprintMoveDetector;
import com.example.lexdiff.move.MoveMatch;
import com.example.lexdiff.report.AmendmentReport;
import com.example.lexdiff.report.ChangeOperation;
import com.example.lexdiff.report.ChangeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DocumentComparator {

    private final WordTokenizer tokenizer        = new WordTokenizer();
    private final MyersDiffAlgorithm differ      = new MyersDiffAlgorithm();
    private final FingerprintMoveDetector mover  = new FingerprintMoveDetector();

    // Matches provisions by label, diffs matched pairs, then resolves unmatched ones as moves/renames/adds/deletes.
    public AmendmentReport compare(LegalDocument a, LegalDocument b) {
        Map<String, Provision> byLabelA = indexByLabel(a.provisions());
        Map<String, Provision> byLabelB = indexByLabel(b.provisions());

        List<ChangeOperation> ops   = new ArrayList<>();
        List<Provision>       unmatched_a = new ArrayList<>();
        List<Provision>       unmatched_b = new ArrayList<>(b.provisions());

        for (Provision pA : a.provisions()) {
            Provision pB = byLabelB.get(pA.label());
            if (pB == null) {
                unmatched_a.add(pA);
            } else {
                unmatched_b.remove(pB);
                List<Edit> edits = differ.diff(tokenizer.tokenize(pA.text()), tokenizer.tokenize(pB.text())).edits();
                boolean changed = edits.stream().anyMatch(e -> e.type() != EditType.EQUAL);
                if (changed) {
                    ops.add(new ChangeOperation(ChangeType.MODIFY_PROVISION, pA.id(), pB.id(), 1.0, buildEvidence(edits)));
                }
            }
        }

        List<MoveMatch> moves = mover.detect(unmatched_a, unmatched_b);

        Map<String, Provision> deletedById  = new LinkedHashMap<>();
        for (Provision p : unmatched_a) deletedById.put(p.id(), p);
        Map<String, Provision> insertedById = new LinkedHashMap<>();
        for (Provision p : unmatched_b) insertedById.put(p.id(), p);

        Map<String, MoveMatch> matchedByOldId = new LinkedHashMap<>();
        Map<String, MoveMatch> matchedByNewId = new LinkedHashMap<>();
        for (MoveMatch m : moves) {
            matchedByOldId.put(m.oldId(), m);
            matchedByNewId.put(m.newId(), m);
        }

        for (MoveMatch m : moves) {
            ChangeType type = m.type() == com.example.lexdiff.move.MoveType.RENUMBER
                    ? ChangeType.RENUMBER_PROVISION
                    : ChangeType.MOVE_PROVISION;
            List<String> evidence = new ArrayList<>();
            Provision oldP = deletedById.get(m.oldId());
            Provision newP = insertedById.get(m.newId());
            if (oldP != null && !oldP.text().isBlank()) evidence.add("before: " + oldP.text());
            if (newP != null && !newP.text().isBlank()) evidence.add("after: "  + newP.text());
            ops.add(new ChangeOperation(type, m.oldId(), m.newId(), m.score(), evidence));
        }

        for (Provision pA : unmatched_a) {
            if (!matchedByOldId.containsKey(pA.id())) {
                ops.add(new ChangeOperation(ChangeType.DELETE_PROVISION, pA.id(), null, 1.0, List.of(pA.text())));
            }
        }

        for (Provision pB : unmatched_b) {
            if (!matchedByNewId.containsKey(pB.id())) {
                ops.add(new ChangeOperation(ChangeType.ADD_PROVISION, null, pB.id(), 1.0, List.of(pB.text())));
            }
        }

        String title = a.metadata().title();
        String from  = a.metadata().versionLabel();
        String to    = b.metadata().versionLabel();
        return new AmendmentReport(title, from, to, ops);
    }

    private Map<String, Provision> indexByLabel(List<Provision> provisions) {
        Map<String, Provision> map = new LinkedHashMap<>();
        for (Provision p : provisions) {
            map.put(p.label(), p);
        }
        return map;
    }

    private List<String> buildEvidence(List<Edit> edits) {
        String deleted = edits.stream()
                .filter(e -> e.type() == EditType.DELETE)
                .map(e -> e.aToken().text())
                .collect(Collectors.joining(" "));
        String inserted = edits.stream()
                .filter(e -> e.type() == EditType.INSERT)
                .map(e -> e.bToken().text())
                .collect(Collectors.joining(" "));
        List<String> snippets = new ArrayList<>();
        if (!deleted.isEmpty())  snippets.add("removed: " + deleted);
        if (!inserted.isEmpty()) snippets.add("added: "   + inserted);
        return snippets;
    }
}
