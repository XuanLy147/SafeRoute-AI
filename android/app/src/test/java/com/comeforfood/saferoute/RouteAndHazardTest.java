package com.comeforfood.saferoute;

import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.map.HazardFilter;
import com.comeforfood.saferoute.feature.routing.Route;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import com.comeforfood.saferoute.feature.routing.RouteGeometry;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class RouteAndHazardTest {
    private final Point a = new Point(10.75, 106.67), b = new Point(10.76, 106.68);
    private Route route(String id, Double risk) {
        return new Route(id, "", 600, 4000, risk, 1, 2, "Test fixture only", Arrays.asList(a, b));
    }
    private Hazard hazard(String id, Hazard.Type type, Hazard.Status status) {
        return new Hazard(id, "Fixture", "", a, type, status);
    }
    @Test public void parseCoordinatesKeepsLatitudeLongitudeOrder() {
        Point point = Point.parse(" 10.755400, 106.678200 ");
        assertEquals(10.7554, point.latitude, 0.0000001);
        assertEquals(106.6782, point.longitude, 0.0000001);
        assertEquals("10.755400, 106.678200", point.format());
    }
    @Test public void acceptsBoundaryCoordinates() {
        assertEquals(-90, Point.parse("-90, -180").latitude, 0);
        assertEquals(180, Point.parse("90, 180").longitude, 0);
    }
    @Test public void rejectsInvalidCoordinateInputs() {
        for (String input : Arrays.asList("", "10", "10,20,30", "91,0", "0,181", "NaN,0", "0,Infinity", "address,20", ",", "10,"))
            assertThrows(input, IllegalArgumentException.class, () -> Point.parse(input));
    }
    @Test public void rejectsSwappedVietnamCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> Point.parse("106.6782,10.7554"));
    }
    @Test public void identicalEndpointsDetected() { assertTrue(a.sameAs(new Point(10.75, 106.67))); assertFalse(a.sameAs(b)); }
    @Test public void decodeOfficialGoogleExample() {
        List<Point> points = RouteGeometry.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertEquals(3, points.size());
        assertEquals(38.5, points.get(0).latitude, 0.00001);
        assertEquals(-120.95, points.get(1).longitude, 0.00001);
        assertEquals(43.252, points.get(2).latitude, 0.00001);
        assertEquals(-126.453, points.get(2).longitude, 0.00001);
    }
    @Test public void rejectsTruncatedAndInvalidPolylines() {
        for (String line : Arrays.asList("", "_", "??", "????", "~", "!!!!!!!!", "~~~~~~~~~~~~~~"))
            assertThrows(line, IllegalArgumentException.class, () -> RouteGeometry.decodePolyline(line));
    }
    @Test public void rejectsMissingAndDegenerateGeometry() {
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.validate(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.validate(Arrays.asList(a, a)));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.validate(Arrays.asList(a, null)));
    }
    @Test public void zeroRoutesRemainZero() {
        RouteComparison result = new RouteComparison(Collections.emptyList(), null, "");
        assertEquals(0, result.routes.size()); assertNull(result.initialSelection());
    }
    @Test public void singleRouteDoesNotGenerateAlternativesOrRecommendation() {
        RouteComparison result = new RouteComparison(Collections.singletonList(route("only", null)), null, "");
        assertEquals(1, result.routes.size()); assertNull(result.recommendedId);
        assertNull(result.initialSelection().riskScore);
    }
    @Test public void respectsServerRecommendationInsteadOfChoosingLowestScore() {
        RouteComparison result = new RouteComparison(Arrays.asList(route("a", 2.0), route("b", 5.0)), "b", "Test scale");
        assertEquals("b", result.initialSelection().id);
    }
    @Test public void rejectsUnknownOrDuplicateRouteIds() {
        assertThrows(IllegalArgumentException.class, () -> new RouteComparison(Arrays.asList(route("a", 1.0), route("a", 2.0)), null, ""));
        assertThrows(IllegalArgumentException.class, () -> new RouteComparison(Collections.singletonList(route("a", 1.0)), "missing", ""));
    }
    @Test public void rejectsInvalidRiskAndMetrics() {
        assertThrows(IllegalArgumentException.class, () -> route("a", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> route("a", -1.0));
        assertThrows(IllegalArgumentException.class, () -> new Route("a", "", -1, 4, 0.0, 0, 0, "", Arrays.asList(a, b)));
    }
    @Test public void rejectedReportsNeverAppearEvenInAllFilter() {
        List<Hazard> reports = Arrays.asList(hazard("a", Hazard.Type.D40, Hazard.Status.REJECTED),
                hazard("b", Hazard.Type.D40, Hazard.Status.PENDING_VERIFICATION));
        List<Hazard> filtered = HazardFilter.apply(reports, HazardFilter.Category.ALL, false);
        assertEquals(1, filtered.size()); assertEquals("b", filtered.get(0).id);
    }
    @Test public void crackAndStatusFiltersCompose() {
        List<Hazard> reports = Arrays.asList(hazard("a", Hazard.Type.D00, Hazard.Status.VERIFIED),
                hazard("b", Hazard.Type.D10, Hazard.Status.PENDING_VERIFICATION),
                hazard("c", Hazard.Type.D40, Hazard.Status.VERIFIED),
                hazard("d", Hazard.Type.REPAIR, Hazard.Status.VERIFIED));
        List<Hazard> filtered = HazardFilter.apply(reports, HazardFilter.Category.CRACKS, true);
        assertEquals(1, filtered.size()); assertEquals("a", filtered.get(0).id);
    }
    @Test public void repairRemainsATypeAndIsNotTreatedAsResolved() {
        assertEquals(1, HazardFilter.apply(Collections.singletonList(
                hazard("repair", Hazard.Type.REPAIR, Hazard.Status.PENDING_VERIFICATION)), HazardFilter.Category.ALL, false).size());
    }
}
