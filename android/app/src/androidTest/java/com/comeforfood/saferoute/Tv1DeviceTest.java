package com.comeforfood.saferoute;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.routing.Route;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1DataSource;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/**
 * Executes Android views on a USB device/emulator. All data is MOCK_FOR_DEV and remains in androidTest.
 * Does not claim live backend/Maps/GPS verification. No permissions or device settings are changed.
 */
@RunWith(AndroidJUnit4.class)
public class Tv1DeviceTest {
    private SafeRouteApp app;
    private Tv1DataSource originalSource;
    private ActivityScenario<MainActivity> scenario;
    @Before public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        originalSource = app.getDataSource();
        // The runtime now uses HTTP. Keep the disconnected fixture explicit in this regression suite.
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c){c.failure(Failure.NOT_CONNECTED);return ()->{};}
            public Request compare(Point a,Point b,Callback<RouteComparison> c){c.failure(Failure.NOT_CONNECTED);return ()->{};}
        });
    }
    @After public void tearDown() {
        if (scenario != null) scenario.close();
        app.setDataSource(originalSource);
    }
    private void launch() { scenario = ActivityScenario.launch(MainActivity.class); settle(); scenario.onActivity(a -> a.openMap()); settle(); }
    private void settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        if (scenario != null) scenario.onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void openSearch() {
        scenario.onActivity(a -> a.openRoutes(null));
        settle();
    }
    private void compare() {
        scenario.onActivity(a -> {
            ((EditText) a.findViewById(R.id.origin)).setText("10.75, 106.67");
            ((EditText) a.findViewById(R.id.destination)).setText("10.76, 106.68");
            a.findViewById(R.id.compare).performClick();
        });
        settle();
    }
    private Route route(String id, int count) {
        return new Route(id, "MOCK_FOR_DEV " + id, 600, 3200, 2.0, count, 0,
                "MOCK_FOR_DEV - synthetic UI test only",
                Arrays.asList(new Point(10.75, 106.67), new Point(10.76, 106.68)));
    }
    private void source(RouteComparison result) {
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) { c.success(Collections.emptyList()); return () -> {}; }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { c.success(result); return () -> {}; }
        });
    }
    private void screenshot(String name) throws Exception {
        settle();
        // Capture only while the tested Activity is in front, never after scenario.close().
        scenario.onActivity(a -> assertTrue(a.hasWindowFocus()));
        android.os.SystemClock.sleep(250);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Screenshot unavailable", image);
        File directory = new File(app.getExternalFilesDir(null), "tv1-device-tests");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }
    @Test public void disconnectedAppAndManualInputRemainUsable() throws Exception {
        launch(); screenshot("01-map-no-backend");
        openSearch();
        scenario.onActivity(a -> {
            ((EditText) a.findViewById(R.id.origin)).setText("91, 0");
            a.findViewById(R.id.compare).performClick();
            assertNotNull(((TextInputLayout) a.findViewById(R.id.origin_layout)).getError());
        });
        compare();
        scenario.onActivity(a -> assertEquals(a.getString(R.string.route_disconnected),
                ((TextView) a.findViewById(R.id.status)).getText().toString()));
        screenshot("02-route-not-connected");
    }
    @Test public void swapAndActivityRecreationKeepInputs() {
        launch(); openSearch();
        scenario.onActivity(a -> {
            ((EditText) a.findViewById(R.id.origin)).setText("10.75, 106.67");
            ((EditText) a.findViewById(R.id.destination)).setText("10.76, 106.68");
            a.findViewById(R.id.swap).performClick();
        });
        scenario.recreate(); settle();
        scenario.onActivity(a -> {
            assertEquals("10.76, 106.68", ((EditText) a.findViewById(R.id.origin)).getText().toString());
            assertEquals("10.75, 106.67", ((EditText) a.findViewById(R.id.destination)).getText().toString());
        });
    }
    @Test public void singleRouteAndNoHazardsAreExplained() throws Exception {
        source(new RouteComparison(Collections.singletonList(route("only", 0)), "only", "MOCK_FOR_DEV scale"));
        launch(); openSearch(); compare();
        scenario.onActivity(a -> {
            assertEquals(1, ((LinearLayout) a.findViewById(R.id.route_list)).getChildCount());
            assertEquals(a.getString(R.string.single_route), ((TextView) a.findViewById(R.id.status)).getText().toString());
            assertEquals(a.getString(R.string.no_hazards), ((TextView) a.findViewById(R.id.hazards)).getText().toString());
        });
        screenshot("03-single-route-MOCK_FOR_DEV");
    }
    @Test public void zeroRoutesCreateNoCandidate() {
        source(new RouteComparison(Collections.emptyList(), null, ""));
        launch(); openSearch(); compare();
        scenario.onActivity(a -> {
            assertEquals(0, ((LinearLayout) a.findViewById(R.id.route_list)).getChildCount());
            assertEquals(a.getString(R.string.route_empty), ((TextView) a.findViewById(R.id.status)).getText().toString());
        });
    }
    @Test public void rejectedReportDisappearsAfterRefresh() throws Exception {
        AtomicBoolean rejected = new AtomicBoolean(false);
        app.setDataSource(new Tv1DataSource() {
            public Request loadReports(Callback<List<Hazard>> c) {
                c.success(Collections.singletonList(new Hazard("test", "MOCK_FOR_DEV", "Synthetic UI test",
                        new Point(10.75,106.67), Hazard.Type.D40,
                        rejected.get() ? Hazard.Status.REJECTED : Hazard.Status.PENDING_VERIFICATION)));
                return () -> {};
            }
            public Request compare(Point a, Point b, Callback<RouteComparison> c) { c.failure(Failure.NOT_CONNECTED); return () -> {}; }
        });
        launch();
        scenario.onActivity(a -> {
            LinearLayout list = a.findViewById(R.id.report_list);
            assertEquals(1, list.getChildCount());
            assertEquals(a.getString(R.string.pending), ((TextView) list.getChildAt(0).findViewById(R.id.status)).getText().toString());
        });
        screenshot("04-report-pending-MOCK_FOR_DEV");
        rejected.set(true);
        scenario.onActivity(a -> a.getSupportFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED, new Bundle()));
        settle();
        scenario.onActivity(a -> assertEquals(0, ((LinearLayout) a.findViewById(R.id.report_list)).getChildCount()));
    }
    @Test public void reportChangeClearsDisplayedRiskUntilRefresh() {
        source(new RouteComparison(Arrays.asList(route("a", 1), route("b", 0)), "b", "MOCK_FOR_DEV scale"));
        launch(); openSearch(); compare();
        scenario.onActivity(a -> {
            assertEquals(2, ((LinearLayout) a.findViewById(R.id.route_list)).getChildCount());
            a.getSupportFragmentManager().setFragmentResult(MainActivity.REPORTS_CHANGED, new Bundle());
        });
        settle();
        scenario.onActivity(a -> {
            assertEquals(0, ((LinearLayout) a.findViewById(R.id.route_list)).getChildCount());
            assertEquals(a.getString(R.string.route_reports_changed), ((TextView) a.findViewById(R.id.status)).getText().toString());
            assertEquals(View.VISIBLE, a.findViewById(R.id.retry).getVisibility());
        });
    }
}
