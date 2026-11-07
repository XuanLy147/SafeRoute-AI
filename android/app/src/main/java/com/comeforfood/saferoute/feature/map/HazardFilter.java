package com.comeforfood.saferoute.feature.map;

import java.util.ArrayList;
import java.util.List;

public final class HazardFilter {
    public enum Category { ALL, POTHOLE, CRACKS }
    private HazardFilter() {}
    public static List<Hazard> apply(List<Hazard> reports, Category category, boolean verifiedOnly) {
        List<Hazard> result = new ArrayList<>();
        for (Hazard report : reports) {
            if (report == null || report.status == Hazard.Status.REJECTED) continue;
            if (verifiedOnly && report.status != Hazard.Status.VERIFIED) continue;
            if (category == Category.POTHOLE && report.type != Hazard.Type.D40) continue;
            if (category == Category.CRACKS && report.type != Hazard.Type.D00
                    && report.type != Hazard.Type.D10 && report.type != Hazard.Type.D20) continue;
            result.add(report);
        }
        return result;
    }
}
