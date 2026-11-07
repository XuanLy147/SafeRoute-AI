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
public class HtmlFlowDeviceTest {
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

    @Test public void htmlFlowAcrossUserReviewerAndAdmin() throws Exception {
        launch();
        com.comeforfood.saferoute.core.session.SessionManager session=new com.comeforfood.saferoute.core.session.SessionManager(activity);
        String oldToken=session.getToken(),oldRole=session.getRole(),oldEmail=session.getEmail(),oldName=session.getFullName();
        try {
            session.clearSession();
            onActivity(a -> a.openTv2Fragment(new com.comeforfood.saferoute.feature.auth.WelcomeFragment()));
            onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            capture("flow-01-welcome");
            click(R.id.btnStart);capture("flow-02-intro");
            click(R.id.introNext);click(R.id.introNext);click(R.id.introNext);capture("flow-03-permissions");
            click(R.id.introNext);capture("flow-04-login");
            click(R.id.tvGoToRegister);capture("visual-register");click(R.id.btnAuthBack);
            click(R.id.btnGuest);SystemClock.sleep(3500);capture("flow-05-map");
            click(R.id.routes);capture("visual-route-search");
            onActivity(a -> a.openMap());onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            tab(R.id.nav_report);capture("flow-06-photo-choice");
            onActivity(a -> {
                com.comeforfood.saferoute.feature.report.ReportDraft draft=new androidx.lifecycle.ViewModelProvider(a).get(com.comeforfood.saferoute.feature.report.ReportDraft.class);
                draft.image("android.resource://"+a.getPackageName()+"/"+R.drawable.ic_map);
                draft.step(2);
                a.openTv2Fragment(new com.comeforfood.saferoute.feature.report.ReportWizardFragment());
            });
            onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            click(R.id.btnUseImage);capture("flow-08-gps");
            onActivity(a -> new androidx.lifecycle.ViewModelProvider(a).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).point(new com.comeforfood.saferoute.feature.shared.Point(10.75,106.67)));
            click(R.id.btnSimulateAi);capture("flow-09-ai-unavailable");
            click(R.id.btnManual);capture("flow-10-manual");
            onActivity(a -> ((android.widget.RadioButton)((android.widget.RadioGroup)a.findViewById(R.id.categoryOptions)).getChildAt(3)).performClick());
            click(R.id.btnReview);capture("flow-11-review-fixture");
            onActivity(a -> ((android.widget.CheckBox)a.findViewById(R.id.confirmReport)).setChecked(true));
            click(R.id.btnSubmitReport);capture("flow-12-failure");
            onActivity(a -> a.openMap());onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            tab(R.id.nav_my_reports);capture("flow-13-history");
            tab(R.id.nav_profile);capture("flow-14-account");
            session.saveSession("test-only-role-fixture","review@example.invalid","REVIEWER","Test reviewer");
            onActivity(a -> a.openSessionHome());onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            capture("flow-15-reviewer-fixture");tab(R.id.nav_role_reports);capture("flow-16-queue-fixture");
            session.saveSession("test-only-role-fixture","admin@example.invalid","ADMIN","Test admin");
            onActivity(a -> a.openSessionHome());onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            capture("flow-17-admin-fixture");tab(R.id.nav_role_users);capture("flow-18-users-fixture");
            onActivity(a -> a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            SystemClock.sleep(1200);
            onActivity(a -> a.openSessionHome());onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());
            capture("flow-19-admin-dark-fixture");
            onActivity(a -> a.openTv2Fragment(new com.comeforfood.saferoute.feature.auth.LoginFragment()));
            onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());capture("visual-login-dark");
            onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            SystemClock.sleep(1500);
            onActivity(a -> a.openTv2Fragment(new com.comeforfood.saferoute.feature.auth.RegisterFragment()));
            onActivity(a -> a.getSupportFragmentManager().executePendingTransactions());capture("visual-register-landscape");
        } finally {
            if(oldToken==null)session.clearSession();else session.saveSession(oldToken,oldEmail,oldRole,oldName);
            onActivity(a -> {new androidx.lifecycle.ViewModelProvider(a).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).clear();a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);a.finish();});
        }
    }
}
