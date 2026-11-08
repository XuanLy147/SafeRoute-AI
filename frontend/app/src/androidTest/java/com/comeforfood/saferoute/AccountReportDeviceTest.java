package com.comeforfood.saferoute;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.inspector.WindowInspector;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=29)
public class AccountReportDeviceTest {
    private MainActivity activity;
    private void onActivity(java.util.function.Consumer<MainActivity> action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity candidate : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                if (candidate instanceof MainActivity) activity=(MainActivity)candidate;
            assertNotNull(activity);
            action.accept(activity);
        });
    }
    private void launch() throws Exception {
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream out =
                     new android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
                             .getUiAutomation().executeShellCommand("am start -n com.comeforfood.saferoute/.MainActivity"))) {
            byte[] buffer=new byte[1024]; while (out.read(buffer)!=-1) {}
        }
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(activity==null && SystemClock.elapsedRealtime()<deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.app.Activity candidate:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(candidate instanceof MainActivity) activity=(MainActivity)candidate;
            });
            SystemClock.sleep(100);
        }
        assertNotNull(activity);
    }
    private void tab(int id) { onActivity(a -> {
        ((BottomNavigationView)a.findViewById(R.id.navigation)).setSelectedItemId(id);
        a.getSupportFragmentManager().executePendingTransactions();
    }); }
    private void click(int id) { onActivity(a -> {
        View view=a.findViewById(id); assertNotNull(view); view.performClick();
        a.getSupportFragmentManager().executePendingTransactions();
    }); }
    private void capture(String name) throws Exception {
        SystemClock.sleep(600);
        onActivity(a -> assertTrue(a.hasWindowFocus()));
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"tv1-device-tests");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(folder,name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
        } finally { bitmap.recycle(); }
    }
    @Test public void integratedScreensNavigateAndRenderInBothThemes() throws Exception {
        launch();
        try {
            tab(R.id.nav_report); capture("merged-report-light");
            tab(R.id.nav_profile); capture("merged-profile-light");
            click(R.id.btnAccount); capture("merged-login-light");
            click(R.id.tvGoToRegister); capture("merged-register-light");
            click(R.id.btnAuthBack); click(R.id.btnAuthBack);
            click(R.id.btnMyReports); capture("merged-history-light");
            onActivity(a -> assertEquals(View.GONE,a.findViewById(R.id.reportPending).getVisibility()));
            click(R.id.btnBack); click(R.id.btnFeedback); capture("merged-community-light");
            click(R.id.btnBack);
            onActivity(a -> a.openFeatureFragment(new com.comeforfood.saferoute.feature.review.ReviewerFragment()));
            onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            onActivity(a -> assertEquals(View.VISIBLE,a.findViewById(R.id.accessDeniedContent).getVisibility()));
            capture("merged-review-access");
            tab(R.id.nav_profile);
            onActivity(a -> a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            SystemClock.sleep(1500);
            onActivity(a -> assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
                    a.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK));
            onActivity(a -> assertTrue(a.getSupportFragmentManager().findFragmentById(R.id.content) instanceof com.comeforfood.saferoute.feature.profile.ProfileFragment));
            capture("merged-profile-dark");
            tab(R.id.nav_report); capture("merged-report-dark");
        } finally { if(activity!=null) onActivity(a -> a.finish()); }
    }
}
