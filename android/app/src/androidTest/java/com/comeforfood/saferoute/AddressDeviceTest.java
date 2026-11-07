package com.comeforfood.saferoute;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import com.comeforfood.saferoute.feature.shared.Point;
import com.comeforfood.saferoute.feature.shared.Tv1ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in live device geocoder check, uses public landmarks, not the user's location. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class AddressDeviceTest {
    private MainActivity activity;
    private void onActivity(java.util.function.Consumer<MainActivity> action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> action.accept(activity));
    }
    private void launch() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // Shell launch avoids OEM background-start restrictions on the test process.
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream output =
                     new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation()
                             .executeShellCommand("am start -n com.comeforfood.saferoute/.MainActivity"))) {
            byte[] buffer = new byte[1024];
            while (output.read(buffer) != -1) { /* consume launcher status, never application data */ }
        }
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (activity == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (android.app.Activity candidate : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (candidate instanceof MainActivity) activity = (MainActivity) candidate;
            });
            if (activity == null) SystemClock.sleep(100);
        }
        assertNotNull("SafeRoute did not resume", activity);
    }

    private ListView findList(View view) {
        if (view instanceof ListView) return (ListView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ListView list = findList(group.getChildAt(i));
                if (list != null) return list;
            }
        }
        return null;
    }
    private void chooseFirst(String screenshot) throws Exception {
        AtomicBoolean available = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 20000;
        while (!available.get() && SystemClock.elapsedRealtime() < deadline) {
            onActivity(activity -> {
                for (View root : WindowInspector.getGlobalWindowViews()) {
                    ListView list = findList(root);
                    if (list != null && list.isShown() && list.getCount() > 0) available.set(true);
                }
            });
            if (!available.get()) SystemClock.sleep(100);
        }
        assertTrue("Live address lookup did not return a selectable result", available.get());
        capture(screenshot);
        onActivity(activity -> {
            for (View root : WindowInspector.getGlobalWindowViews()) {
                ListView list = findList(root);
                if (list != null && list.isShown() && list.getCount() > 0) {
                    list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
                    return;
                }
            }
            fail("Place selection dialog disappeared");
        });
        SystemClock.sleep(100);
    }
    private void capture(String name) throws Exception {
        // All global roots above are in the target app process, including its dialog.
        AtomicBoolean foreground = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            SystemClock.sleep(100);
            onActivity(activity -> {
                for (View root : WindowInspector.getGlobalWindowViews())
                    if (root.hasWindowFocus()) foreground.set(true);
            });
        } while (!foreground.get() && SystemClock.elapsedRealtime() < deadline);
        assertTrue("App window did not gain focus for capture", foreground.get());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "tv1-device-tests");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
    }
    @Test public void publicLandmarkNamesResolveAndKeepLabels() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveGeocoder")));
        launch();
        try {
            onActivity(activity -> {
                ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_routes);
                activity.getSupportFragmentManager().executePendingTransactions();
                ((EditText) activity.findViewById(R.id.origin)).setText("Chợ Bến Thành, TP Hồ Chí Minh");
                ((EditText) activity.findViewById(R.id.destination)).setText("Dinh Độc Lập, TP Hồ Chí Minh");
            });
            capture("address-form");
            onActivity(activity -> activity.findViewById(R.id.compare).performClick());
            chooseFirst("address-choice-a");
            chooseFirst("address-choice-b");
            onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                Tv1ViewModel model = new ViewModelProvider(activity).get(Tv1ViewModel.class);
                Point a = Point.parse(model.origin()), b = Point.parse(model.destination());
                assertFalse(a.sameAs(b));
                assertFalse(model.origin().equals(model.originText()));
                assertFalse(model.destination().equals(model.destinationText()));
                assertTrue(model.originText().contains("Chợ Bến Thành"));
                assertTrue(model.destinationText().contains("Dinh Độc Lập"));
                assertTrue(((TextView) activity.findViewById(R.id.endpoints)).getText().toString()
                        .contains(model.originText()));
            });
            capture("address-result");
        } finally { if (activity != null) onActivity(a -> a.finish()); }
    }
}
