package fr.mathilde.easybans.warning;

import java.util.List;

public record WarningCategory(String id, String displayName, List<WarningThreshold> thresholds) {

    /** Thresholds that trigger exactly at this total warning count. */
    public List<WarningThreshold> thresholdsMatching(int totalCount) {
        return thresholds.stream().filter(t -> t.count() == totalCount).toList();
    }
}
