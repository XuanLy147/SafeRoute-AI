package com.comeforfood.saferoute;

import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.routing.Route;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
import com.comeforfood.saferoute.feature.shared.Tv1ViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Test-only data. These synthetic paths never ship as selectable app routes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w390dp-h844dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
public class Tv1UiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private SafeRouteApp app;
    @Before public void start() {
        app = (SafeRouteApp) RuntimeEnvironment.getApplication();
        // The runtime now uses HTTP. Keep the disconnected fixture explicit in this regression suite.
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c){c.failure(Failure.NOT_CONNECTED);return ()->{};}
            public Request compare(Point a,Point b,Callback<RouteComparison> c){c.failure(Failure.NOT_CONNECTED);return ()->{};}
        });
    }
    private void launch() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get(); settle(); activity.openMap(); settle();
    }
    private void settle() {
        shadowOf(Looper.getMainLooper()).idle();
        if (activity != null) activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void stop() { if (controller != null) controller.pause().stop().destroy(); }
    private void openSearch() {
        activity.openRoutes(null);
        settle();
    }
    private void enterPoints() {
        ((EditText) activity.findViewById(R.id.origin)).setText("10.75, 106.67");
        ((EditText) activity.findViewById(R.id.destination)).setText("10.76, 106.68");
    }
    private void compare() { activity.findViewById(R.id.compare).performClick(); settle(); }
    private Route route(String id, int hazards) {
        return new Route(id, "Fixture " + id, 600, 3200, 2.0, hazards, 0, "Test fixture only",
                Arrays.asList(new Point(10.75, 106.67), new Point(10.76, 106.68)));
    }
    private void source(RouteComparison result) {
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) { c.success(Collections.emptyList()); return () -> {}; }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { c.success(result); return () -> {}; }
        });
    }
    @Test public void startsWithoutMapsKeyAndShowsDisconnectedData() {
        launch();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.map_placeholder).getVisibility());
        assertEquals(activity.getString(R.string.reports_disconnected), ((TextView) activity.findViewById(R.id.status)).getText().toString());
    }
    @Test public void invalidInputDoesNotNavigateOrInventRoutes() {
        launch(); openSearch();
        ((EditText) activity.findViewById(R.id.origin)).setText("91, 0");
        compare();
        assertNotNull(((TextInputLayout) activity.findViewById(R.id.origin_layout)).getError());
        assertNotNull(activity.findViewById(R.id.compare));
    }
    @Test public void identicalEndpointsAreRejected() {
        launch(); openSearch(); enterPoints();
        ((EditText) activity.findViewById(R.id.destination)).setText("10.75, 106.67");
        compare();
        assertEquals(activity.getString(R.string.same_endpoints), ((TextInputLayout) activity.findViewById(R.id.destination_layout)).getError().toString());
    }
    @Test public void swapsEndpointsAndPreservesAcrossRecreation() {
        launch(); openSearch(); enterPoints();
        activity.findViewById(R.id.swap).performClick();
        controller.recreate(); activity = controller.get(); settle();
        assertEquals("10.76, 106.68", ((EditText) activity.findViewById(R.id.origin)).getText().toString());
        assertEquals("10.75, 106.67", ((EditText) activity.findViewById(R.id.destination)).getText().toString());
    }
    @Test public void disconnectedSearchShowsHonestMessageAndBackRetainsInputs() {
        launch(); openSearch(); enterPoints(); compare();
        assertEquals(activity.getString(R.string.route_disconnected), ((TextView) activity.findViewById(R.id.status)).getText().toString());
        activity.findViewById(R.id.edit).performClick(); settle();
        assertEquals("10.75, 106.67", ((EditText) activity.findViewById(R.id.origin)).getText().toString());
    }
    @Test public void oneRouteAndNoHazardsAreExplainedWithoutExtraCandidate() {
        source(new RouteComparison(Collections.singletonList(route("only", 0)), "only", "Fixture scale"));
        launch(); openSearch(); enterPoints(); compare();
        assertEquals(activity.getString(R.string.single_route), ((TextView) activity.findViewById(R.id.status)).getText().toString());
        assertEquals(1, ((LinearLayout) activity.findViewById(R.id.route_list)).getChildCount());
        assertEquals(activity.getString(R.string.no_hazards), ((TextView) activity.findViewById(R.id.hazards)).getText().toString());
    }
    @Test public void emptyRouteResultDoesNotRenderCard() {
        source(new RouteComparison(Collections.emptyList(), null, ""));
        launch(); openSearch(); enterPoints(); compare();
        assertEquals(activity.getString(R.string.route_empty), ((TextView) activity.findViewById(R.id.status)).getText().toString());
        assertEquals(0, ((LinearLayout) activity.findViewById(R.id.route_list)).getChildCount());
    }
    @Test public void userCanSelectAnotherReturnedRoute() {
        source(new RouteComparison(Arrays.asList(route("a", 1), route("b", 2)), "b", "Fixture scale"));
        launch(); openSearch(); enterPoints(); compare();
        LinearLayout list = activity.findViewById(R.id.route_list);
        assertEquals(2, list.getChildCount());
        list.getChildAt(0).findViewById(R.id.select).performClick(); settle();
        assertEquals("a", new ViewModelProvider(activity).get(Tv1ViewModel.class).selectedRoute());
    }
    @Test public void lateResponseFromCancelledRequestCannotOverwriteNewResult() {
        final java.util.ArrayList<Tv1DataSource.Callback<RouteComparison>> callbacks = new java.util.ArrayList<>();
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) { c.success(Collections.emptyList()); return () -> {}; }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { callbacks.add(c); return () -> {}; }
        });
        launch();
        Tv1ViewModel model = new ViewModelProvider(activity).get(Tv1ViewModel.class);
        model.input("10.75,106.67", "10.76,106.68");
        model.compare(); model.compare();
        callbacks.get(1).success(new RouteComparison(Collections.singletonList(route("new", 0)), null, ""));
        settle();
        callbacks.get(0).success(new RouteComparison(Collections.singletonList(route("old", 0)), null, ""));
        settle();
        assertEquals("new", model.routes().getValue().data.initialSelection().id);
    }
    @Test public void stalledRequestBecomesRetryableNetworkError() {
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) { return () -> {}; }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { return () -> {}; }
        });
        launch(); openSearch(); enterPoints(); compare();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31)); settle();
        assertEquals(activity.getString(R.string.route_network), ((TextView) activity.findViewById(R.id.status)).getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.retry).getVisibility());
    }

    public static class TestReportScreen extends androidx.fragment.app.Fragment {
        public TestReportScreen() { super(android.R.layout.simple_list_item_1); }
    }
    public static class TestProfileScreen extends androidx.fragment.app.Fragment {
        public TestProfileScreen() { super(android.R.layout.simple_list_item_1); }
    }
    @Test public void registeredTv2ScreensUseSameHostAndKeepCorrectTabAfterRecreation() {
        app.setTv2Screens(new com.comeforfood.saferoute.feature.shared.Tv2Screens() {
            public androidx.fragment.app.Fragment report() { return new TestReportScreen(); }
            public androidx.fragment.app.Fragment profile() { return new TestProfileScreen(); }
        });
        launch();
        ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_report);
        settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof TestReportScreen);
        controller.recreate(); activity = controller.get(); settle();
        assertEquals(R.id.nav_report, ((BottomNavigationView) activity.findViewById(R.id.navigation)).getSelectedItemId());
        ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_profile);
        settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof TestProfileScreen);
        assertEquals(R.id.nav_profile, ((BottomNavigationView) activity.findViewById(R.id.navigation)).getSelectedItemId());
    }
    @Test public void reportUpdateInvalidatesRiskAndDiscardsPendingRouteCallback() {
        java.util.ArrayList<Tv1DataSource.Callback<RouteComparison>> callbacks = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger reloads = new java.util.concurrent.atomic.AtomicInteger();
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) {
                reloads.incrementAndGet(); c.success(Collections.emptyList()); return () -> {};
            }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { callbacks.add(c); return () -> {}; }
        });
        launch(); openSearch(); enterPoints(); compare();
        activity.getSupportFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED, new android.os.Bundle());
        settle();
        assertEquals(activity.getString(R.string.route_reports_changed),
                ((TextView) activity.findViewById(R.id.status)).getText().toString());
        callbacks.get(0).success(new RouteComparison(Collections.singletonList(route("stale", 0)), "stale", "Fixture"));
        settle();
        assertEquals(0, ((LinearLayout) activity.findViewById(R.id.route_list)).getChildCount());
        assertTrue(new ViewModelProvider(activity).get(Tv1ViewModel.class).routes().getValue().requiresRefresh);
        assertTrue(reloads.get() >= 2);
        activity.findViewById(R.id.retry).performClick(); settle();
        callbacks.get(1).success(new RouteComparison(Collections.singletonList(route("fresh", 1)), "fresh", "Fixture"));
        settle();
        assertEquals(1, ((LinearLayout) activity.findViewById(R.id.route_list)).getChildCount());
        assertEquals("fresh", new ViewModelProvider(activity).get(Tv1ViewModel.class).selectedRoute());
    }
    @Test public void reportRefreshRemovesRejectedItemAndRetainsPendingLabel() {
        java.util.concurrent.atomic.AtomicBoolean rejected = new java.util.concurrent.atomic.AtomicBoolean();
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) {
                c.success(Collections.singletonList(new Hazard("fixture", "MOCK_FOR_DEV", "",
                        new Point(10.75,106.67), Hazard.Type.D40,
                        rejected.get() ? Hazard.Status.REJECTED : Hazard.Status.PENDING_VERIFICATION)));
                return () -> {};
            }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { c.failure(Failure.NOT_CONNECTED); return () -> {}; }
        });
        launch();
        LinearLayout list = activity.findViewById(R.id.report_list);
        assertEquals(1, list.getChildCount());
        TextView status = list.getChildAt(0).findViewById(R.id.status);
        assertEquals(activity.getString(R.string.pending), status.getText().toString());
        rejected.set(true);
        activity.getSupportFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED, new android.os.Bundle());
        settle();
        assertEquals(0, list.getChildCount());
    }
    @Test public void coarseLocationPermissionIsAcceptedWithoutFinePermission() {
        shadowOf(app).grantPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION);
        assertTrue(com.comeforfood.saferoute.feature.shared.ForegroundLocation.permitted(app));
    }
    @Test public void deniedLocationLeavesManualCoordinateEntryUsable() {
        shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        launch(); openSearch();
        activity.findViewById(R.id.location).performClick(); settle();
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request = shadowOf(activity).getLastRequestedPermission();
        assertNotNull(request);
        int[] denied = new int[request.requestedPermissions.length];
        java.util.Arrays.fill(denied, android.content.pm.PackageManager.PERMISSION_DENIED);
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, denied);
        settle(); enterPoints(); compare();
        assertEquals(activity.getString(R.string.route_disconnected), ((TextView) activity.findViewById(R.id.status)).getText().toString());
    }
    @Test public void permissionResultAfterRotationDoesNotCrashOrBlockManualEntry() {
        shadowOf(app).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        launch(); openSearch();
        activity.findViewById(R.id.location).performClick(); settle();
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request = shadowOf(activity).getLastRequestedPermission();
        assertNotNull(request);
        controller.recreate(); activity = controller.get(); settle();
        int[] denied = new int[request.requestedPermissions.length];
        java.util.Arrays.fill(denied, android.content.pm.PackageManager.PERMISSION_DENIED);
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, denied);
        settle(); enterPoints(); compare();
        assertNotNull(activity.findViewById(R.id.status));
    }
    @Test @Config(qualifiers = "w844dp-h390dp-land-night")
    public void mapUsesScrollableLayoutAtLargeFontInLandscape() {
        RuntimeEnvironment.setFontScale(2.0f);
        launch();
        androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.content);
        assertTrue(fragment.requireView() instanceof androidx.core.widget.NestedScrollView);
        assertNotNull(activity.findViewById(R.id.refresh));
        assertNotNull(activity.findViewById(R.id.verified));
        openSearch(); enterPoints(); compare();
        assertNotNull(activity.findViewById(R.id.edit));
    }


    private void enterAddresses() {
        ((EditText) activity.findViewById(R.id.origin)).setText("Market, Ho Chi Minh City");
        ((EditText) activity.findViewById(R.id.destination)).setText("Museum, Ho Chi Minh City");
    }
    private void chooseFirstAddress() {
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        dialog.getListView().performItemClick(null, 0, 0);
        settle();
    }
    @Test public void namesResolveToSelectedCoordinatesAndSurviveSwapAndRecreation() {
        app.setAddressSearch((query, callback) -> {
            boolean market = query.startsWith("Market");
            callback.success(Collections.singletonList(new com.comeforfood.saferoute.feature.routing.AddressSearch.Place(
                    query, new Point(market ? 10.75 : 10.76, 106.67))));
            return () -> {};
        });
        launch(); openSearch(); enterAddresses(); compare();
        chooseFirstAddress(); chooseFirstAddress();
        Tv1ViewModel model = new ViewModelProvider(activity).get(Tv1ViewModel.class);
        assertEquals(10.75, Point.parse(model.origin()).latitude, 0);
        assertEquals(10.76, Point.parse(model.destination()).latitude, 0);
        assertTrue(((TextView) activity.findViewById(R.id.endpoints)).getText().toString().contains("Market"));
        activity.getOnBackPressedDispatcher().onBackPressed(); settle();
        activity.findViewById(R.id.swap).performClick();
        controller.recreate(); activity = controller.get(); settle();
        assertEquals("Museum, Ho Chi Minh City", ((EditText) activity.findViewById(R.id.origin)).getText().toString());
        assertEquals(10.76, Point.parse(new ViewModelProvider(activity).get(Tv1ViewModel.class).origin()).latitude, 0);
    }
    @Test public void editingChosenAddressDiscardsOldCoordinates() {
        launch(); openSearch();
        Tv1ViewModel model = new ViewModelProvider(activity).get(Tv1ViewModel.class);
        model.selectEndpoint(true, "Old place", new Point(10.75, 106.67));
        ((EditText) activity.findViewById(R.id.origin)).setText("New place");
        assertEquals("", model.origin());
        assertEquals("New place", model.originText());
    }
    @Test public void staleAddressCallbackDoesNotShowChoicesAfterEditing() {
        final com.comeforfood.saferoute.feature.routing.AddressSearch.Callback[] pending =
                new com.comeforfood.saferoute.feature.routing.AddressSearch.Callback[1];
        final boolean[] cancelled = {false};
        app.setAddressSearch((query, callback) -> { pending[0] = callback; return () -> cancelled[0] = true; });
        launch(); openSearch(); enterAddresses(); compare();
        ((EditText) activity.findViewById(R.id.origin)).setText("Other place");
        pending[0].success(Collections.singletonList(new com.comeforfood.saferoute.feature.routing.AddressSearch.Place(
                "Old response", new Point(10.75, 106.67))));
        settle();
        assertTrue(cancelled[0]);
        assertEquals("", new ViewModelProvider(activity).get(Tv1ViewModel.class).origin());
        assertEquals(View.GONE, activity.findViewById(R.id.address_progress).getVisibility());
        assertTrue(activity.findViewById(R.id.compare).isEnabled());
    }
    @Test public void addressEmptyAndNetworkErrorsPreserveInputsAndAllowRetry() {
        app.setAddressSearch((query, callback) -> { callback.success(Collections.emptyList()); return () -> {}; });
        launch(); openSearch(); enterAddresses(); compare();
        assertEquals(activity.getString(R.string.address_not_found),
                ((TextInputLayout) activity.findViewById(R.id.origin_layout)).getError().toString());
        app.setAddressSearch((query, callback) -> {
            callback.failure(com.comeforfood.saferoute.feature.routing.AddressSearch.Failure.NETWORK); return () -> {};
        });
        compare();
        assertEquals(activity.getString(R.string.address_error),
                ((TextInputLayout) activity.findViewById(R.id.origin_layout)).getError().toString());
        assertEquals("Market, Ho Chi Minh City", ((EditText) activity.findViewById(R.id.origin)).getText().toString());
        assertTrue(activity.findViewById(R.id.compare).isEnabled());
    }
    @Test public void differentNamesResolvingToSamePointAreRejected() {
        app.setAddressSearch((query, callback) -> {
            callback.success(Collections.singletonList(new com.comeforfood.saferoute.feature.routing.AddressSearch.Place(
                    query, new Point(10.75, 106.67)))); return () -> {};
        });
        launch(); openSearch(); enterAddresses(); compare(); chooseFirstAddress(); chooseFirstAddress();
        assertEquals(activity.getString(R.string.same_endpoints),
                ((TextInputLayout) activity.findViewById(R.id.destination_layout)).getError().toString());
    }
}
