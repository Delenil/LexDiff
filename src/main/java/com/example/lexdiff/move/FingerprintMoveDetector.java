package com.example.lexdiff.move;

import com.example.lexdiff.domain.Provision;
import com.example.lexdiff.similarity.JaccardSimilarityModel;
import com.example.lexdiff.similarity.ShingleFingerprinter;
import com.example.lexdiff.similarity.ShingleSet;

import java.util.ArrayList;
import java.util.List;

public class FingerprintMoveDetector implements MoveDetector {

    public static final double DEFAULT_THRESHOLD = 0.3;

    private final ShingleFingerprinter fingerprinter;
    private final JaccardSimilarityModel similarity;
    private final double threshold;

    public FingerprintMoveDetector() {
        this(DEFAULT_THRESHOLD);
    }

    public FingerprintMoveDetector(double threshold) {
        this.fingerprinter = new ShingleFingerprinter();
        this.similarity    = new JaccardSimilarityModel();
        this.threshold     = threshold;
    }

    @Override
    public List<MoveMatch> detect(List<Provision> deleted, List<Provision> inserted) {
        List<MoveMatch> matches = new ArrayList<>();

        List<ShingleSet> deletedPrints  = deleted.stream().map(fingerprinter::fingerprint).toList();
        List<ShingleSet> insertedPrints = inserted.stream().map(fingerprinter::fingerprint).toList();

        boolean[] usedInserted = new boolean[inserted.size()];

        for (int d = 0; d < deleted.size(); d++) {
            double bestScore = threshold;
            int    bestIdx   = -1;

            for (int i = 0; i < inserted.size(); i++) {
                if (usedInserted[i]) continue;
                double score = similarity.similarity(deletedPrints.get(d), insertedPrints.get(i));
                if (score > bestScore) {
                    bestScore = score;
                    bestIdx   = i;
                }
            }

            if (bestIdx >= 0) {
                usedInserted[bestIdx] = true;
                Provision oldP = deleted.get(d);
                Provision newP = inserted.get(bestIdx);
                MoveType  type = labelChanged(oldP, newP) ? MoveType.RENUMBER : MoveType.MOVE;
                matches.add(new MoveMatch(oldP.id(), newP.id(), bestScore, type));
            }
        }

        return matches;
    }

    private boolean labelChanged(Provision a, Provision b) {
        return !a.label().equals(b.label());
    }
}
