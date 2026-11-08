package com.comeforfood.saferoute.feature.shared;

import java.io.Serializable;
import java.util.Locale;

/** WGS84 UI coordinates, not an API DTO. */
public final class Point implements Serializable {
    public final double latitude, longitude;
    public Point(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Invalid coordinates");
        this.latitude = latitude; this.longitude = longitude;
    }
    public static Point parse(String input) {
        if (input == null) throw new IllegalArgumentException("Missing coordinates");
        String[] values = input.trim().split(",", -1);
        if (values.length != 2) throw new IllegalArgumentException("Use latitude, longitude");
        return new Point(Double.parseDouble(values[0].trim()), Double.parseDouble(values[1].trim()));
    }
    public boolean sameAs(Point other) {
        return other != null && Math.abs(latitude - other.latitude) < 0.000001
                && Math.abs(longitude - other.longitude) < 0.000001;
    }
    public String format() { return String.format(Locale.US, "%.6f, %.6f", latitude, longitude); }
}
