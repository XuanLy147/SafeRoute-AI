package com.comeforfood.saferoute.feature.routing;

import com.comeforfood.saferoute.feature.shared.Point;
import java.util.ArrayList;
import java.util.List;

public final class RouteGeometry {
    private RouteGeometry() {}
    public static void validate(List<Point> points) {
        if (points == null || points.size() < 2 || points.get(0) == null)
            throw new IllegalArgumentException("Missing route geometry");
        boolean distinct = false;
        for (Point point : points) {
            if (point == null) throw new IllegalArgumentException("Null route coordinate");
            if (!points.get(0).sameAs(point)) distinct = true;
        }
        if (!distinct) throw new IllegalArgumentException("Degenerate route geometry");
    }
    /** Google polyline precision 5; call only if the final API explicitly uses this encoding. */
    public static List<Point> decodePolyline(String encoded) {
        if (encoded == null || encoded.length() > 1000000) throw new IllegalArgumentException("Invalid polyline");
        int[] cursor = {0}; long lat = 0, lng = 0;
        List<Point> points = new ArrayList<>();
        while (cursor[0] < encoded.length()) {
            lat += read(encoded, cursor); lng += read(encoded, cursor);
            points.add(new Point(lat / 1e5, lng / 1e5));
        }
        validate(points);
        return points;
    }
    private static long read(String encoded, int[] cursor) {
        long result = 0;
        for (int shift = 0; shift <= 30; shift += 5) {
            if (cursor[0] >= encoded.length()) throw new IllegalArgumentException("Truncated polyline");
            int digit = encoded.charAt(cursor[0]++) - 63;
            if (digit < 0 || digit > 63) throw new IllegalArgumentException("Invalid polyline character");
            result |= (long) (digit & 31) << shift;
            if (digit < 32) return (result & 1) == 0 ? result >> 1 : ~(result >> 1);
        }
        throw new IllegalArgumentException("Polyline overflow");
    }
}
