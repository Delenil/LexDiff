package com.example.lexdiff.move;

import com.example.lexdiff.domain.Provision;
import java.util.List;

public interface MoveDetector {
    List<MoveMatch> detect(List<Provision> deleted, List<Provision> inserted);
}