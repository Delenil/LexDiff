package com.example.lexdiff.diff;

import com.example.lexdiff.domain.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements Myers' O(ND) shortest-edit-script algorithm.
 *
 * <p>Reference: Eugene W. Myers, "An O(ND) Difference Algorithm and Its Variations",
 * Algorithmica, vol. 1, nos. 1–4, 1986, pp. 251–266.
 *
 * <p>The algorithm finds the minimum number of insertions and deletions (edit distance D)
 * needed to transform token sequence {@code a} into token sequence {@code b}. It does so
 * by exploring edit-graph diagonals (where diagonal k satisfies x − y = k) and tracking
 * the furthest x-coordinate reachable on each diagonal for each edit distance d.
 *
 * <p>Token equality is determined by {@link Token#equals}, which compares text values
 * with exact (case-sensitive) matching. This is appropriate for legal texts where
 * consistent casing within a version pair is the norm and where case differences are
 * themselves meaningful amendments.
 */
public class MyersDiffAlgorithm implements DiffAlgorithm {

    @Override
    public EditScript diff(List<Token> a, List<Token> b) {
        int n = a.size();
        int m = b.size();

        if (n == 0 && m == 0) {
            return new EditScript(Collections.emptyList());
        }
        if (n == 0) {
            return allInserts(b);
        }
        if (m == 0) {
            return allDeletes(a);
        }

        int max = n + m;
        // v[k + max] = furthest x-coordinate reached on diagonal k.
        // Initialised to 0: the algorithm treats the virtual start as (0, 1) on
        // diagonal k=1 (i.e. before any real move), which is consistent with using
        // v[k+1] = 0 as the starting x when we move down on diagonal k = k+1 - 1.
        int[] v = new int[2 * max + 1];

        // trace[d] = snapshot of v[] saved at the start of edit-distance round d.
        // We need this snapshot during backtracking to reconstruct which branch was
        // chosen at each step.
        List<int[]> trace = new ArrayList<>();

        outer:
        for (int d = 0; d <= max; d++) {
            trace.add(v.clone());

            for (int k = -d; k <= d; k += 2) {
                int x;
                // Choose the better predecessor diagonal.
                // If k == -d we can only come from diagonal k+1 (a down / insert move).
                // If k ==  d we can only come from diagonal k-1 (a right / delete move).
                // Otherwise pick whichever diagonal reached furthest.
                if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
                    x = v[k + 1 + max];       // down move: x stays, y advances → insert
                } else {
                    x = v[k - 1 + max] + 1;   // right move: x advances → delete
                }

                int y = x - k;

                // Follow the snake: advance diagonally while tokens are equal.
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }

                v[k + max] = x;

                if (x >= n && y >= m) {
                    break outer;
                }
            }
        }

        return backtrack(trace, a, b, max);
    }

    // --- backtracking ----------------------------------------------------------

    /**
     * Reconstructs the edit script by walking backwards through the forward trace.
     *
     * <p>At each edit-distance step d, the algorithm re-determines which diagonal
     * the path came from (using the same decision rule as the forward pass but
     * applied to the saved snapshot), emits EQUAL edits for the snake, then emits
     * the single INSERT or DELETE that preceded the snake.
     */
    private EditScript backtrack(List<int[]> trace, List<Token> a, List<Token> b, int max) {
        List<Edit> edits = new ArrayList<>();
        int x = a.size();
        int y = b.size();

        for (int d = trace.size() - 1; d > 0; d--) {
            int[] v    = trace.get(d);   // V snapshot saved at the START of round d
            int   k    = x - y;

            int prevK;
            if (k == -d || (k != d && v[k - 1 + max] < v[k + 1 + max])) {
                prevK = k + 1;  // came from a down (insert) move
            } else {
                prevK = k - 1;  // came from a right (delete) move
            }

            int prevX = v[prevK + max];
            int prevY = prevX - prevK;

            // The snake for this round runs from (snakeStartX, snakeStartY) to (x, y).
            // snakeStart is one step past the non-diagonal move:
            //   insert → snakeStartX = prevX,     snakeStartY = prevY + 1
            //   delete → snakeStartX = prevX + 1, snakeStartY = prevY
            // In both cases snakeStartX > prevX iff it was a delete, so we can use
            // just x > prevX (for delete) or y > prevY + 1 → x > prevX (same thing
            // on diagonal k).  A simpler unified condition: the snake length equals
            // x - prevX - (prevK == k - 1 ? 1 : 0).
            int snakeLen = x - prevX - (prevK == k - 1 ? 1 : 0);
            for (int i = 0; i < snakeLen; i++) {
                edits.add(new Edit(EditType.EQUAL, a.get(x - 1), b.get(y - 1)));
                x--;
                y--;
            }

            // Emit the single non-diagonal move.
            if (prevK == k + 1) {
                // Insert: b[y-1] was inserted (y advances, x stays).
                edits.add(new Edit(EditType.INSERT, null, b.get(y - 1)));
                y--;
            } else {
                // Delete: a[x-1] was deleted (x advances, y stays).
                edits.add(new Edit(EditType.DELETE, a.get(x - 1), null));
                x--;
            }
            // Invariant after the step: (x, y) == (prevX, prevY).
        }

        // d == 0: any remaining tokens form the initial common prefix (all EQUAL).
        while (x > 0 && y > 0) {
            edits.add(new Edit(EditType.EQUAL, a.get(x - 1), b.get(y - 1)));
            x--;
            y--;
        }

        Collections.reverse(edits);
        return new EditScript(edits);
    }

    // --- trivial edge-case helpers --------------------------------------------

    private EditScript allInserts(List<Token> b) {
        List<Edit> edits = new ArrayList<>(b.size());
        for (Token t : b) {
            edits.add(new Edit(EditType.INSERT, null, t));
        }
        return new EditScript(edits);
    }

    private EditScript allDeletes(List<Token> a) {
        List<Edit> edits = new ArrayList<>(a.size());
        for (Token t : a) {
            edits.add(new Edit(EditType.DELETE, t, null));
        }
        return new EditScript(edits);
    }
}
