package com.comeforfood.saferoute.feature.routing;

import com.comeforfood.saferoute.feature.shared.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI model, with units explicit. No risk score is calculated on the client. */
public final class Route {
    public final String id, name, reason;
    public final int durationSeconds, distanceMeters, verifiedCount, pendingCount;
    public final Double riskScore;
    public final List<Point> points;
    public Route(String id, String name, int durationSeconds, int distanceMeters, Double riskScore,
                 int verifiedCount, int pendingCount, String reason, List<Point> points) {
        if (id == null || id.trim().isEmpty() || durationSeconds < 0 || distanceMeters < 0
                || verifiedCount < 0 || pendingCount < 0 || (riskScore != null
                && (!Double.isFinite(riskScore) || riskScore < 0)))
            throw new IllegalArgumentException("Invalid route metadata");
        RouteGeometry.validate(points);
        this.id = id; this.name = name == null ? "" : name;
        this.durationSeconds = durationSeconds; this.distanceMeters = distanceMeters;
        this.riskScore = riskScore; this.verifiedCount = verifiedCount; this.pendingCount = pendingCount;
        this.reason = reason == null ? "" : reason;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }
}
