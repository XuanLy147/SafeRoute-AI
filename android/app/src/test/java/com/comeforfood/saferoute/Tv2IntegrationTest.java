package com.comeforfood.saferoute;

import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.auth.LoginFragment;
import com.comeforfood.saferoute.feature.profile.ProfileFragment;
import com.comeforfood.saferoute.feature.report.ReportWizardFragment;
import com.comeforfood.saferoute.feature.review.ReviewerFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w390dp-h844dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
public class Tv2IntegrationTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private okhttp3.mockwebserver.MockWebServer server;
    @Before public void start() throws Exception {
        server=new okhttp3.mockwebserver.MockWebServer();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public okhttp3.mockwebserver.MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new okhttp3.mockwebserver.MockResponse().setResponseCode(503).setBody("{}");
            }
        });server.start();
        SafeRouteApp app=(SafeRouteApp)RuntimeEnvironment.getApplication();
        java.lang.reflect.Field field=SafeRouteApp.class.getDeclaredField("api");field.setAccessible(true);
        field.set(app,new com.comeforfood.saferoute.core.network.ApiClient(app,server.url("/").toString()));
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get(); settle(); activity.openMap(); settle();
    }
    @After public void finish() throws Exception { new SessionManager(activity).clearSession();controller.pause().stop().destroy();server.shutdown(); }
    private void settle() {
        shadowOf(Looper.getMainLooper()).idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void userFixture() {
        new SessionManager(activity).saveSession("test-only-user", "user@example.invalid", "USER", "Test user");settle();
    }
    private void tab(int id) {
        ((BottomNavigationView)activity.findViewById(R.id.navigation)).setSelectedItemId(id); settle();
    }
    @Test public void reportAndProfileUseOneHostWithoutInventedGps() {
        userFixture();
        tab(R.id.nav_report);
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ReportWizardFragment);
        assertEquals(1, new ViewModelProvider(activity).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).step());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btnCamera).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btnPickGallery).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.tvCoordinates).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.mapHint).getVisibility());
        tab(R.id.nav_profile);
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
        assertEquals("USER",
                ((TextView)activity.findViewById(R.id.tvRole)).getText().toString());
    }
    @Test public void authDoesNotCreateFakeTokensOrReviewerRole() {
        tab(R.id.nav_profile); activity.findViewById(R.id.btnAccount).performClick(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof LoginFragment);
        ((EditText)activity.findViewById(R.id.etEmail)).setText("invalid-email");
        ((EditText)activity.findViewById(R.id.etPassword)).setText("test-password");
        activity.findViewById(R.id.btnLogin).performClick(); settle();
        assertFalse(new SessionManager(activity).isLoggedIn());
        assertFalse(new SessionManager(activity).isReviewerOrAdmin());
        activity.findViewById(R.id.tvGoToRegister).performClick(); settle();
        ((EditText)activity.findViewById(R.id.etFullName)).setText("Test User");
        ((EditText)activity.findViewById(R.id.etEmail)).setText("invalid-email");
        ((EditText)activity.findViewById(R.id.etPassword)).setText("test-password");
        ((EditText)activity.findViewById(R.id.etConfirmPassword)).setText("test-password");
        activity.findViewById(R.id.btnRegister).performClick(); settle();
        assertFalse(new SessionManager(activity).isLoggedIn());
        activity.findViewById(R.id.btnAuthBack).performClick(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof LoginFragment);
    }
    @Test public void reportsStayEmptyUntilConnectedAndProfileTabMatchesDestination() {
        userFixture();
        tab(R.id.nav_report);
        activity.openTv2Fragment(new com.comeforfood.saferoute.feature.reports.MyReportsFragment()); settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.reportsUnavailable).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.reportPending).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.reportVerified).getVisibility());
        activity.findViewById(R.id.btnOpenProfile).performClick(); settle();
        assertEquals(R.id.nav_profile, ((BottomNavigationView)activity.findViewById(R.id.navigation)).getSelectedItemId());
    }
    @Test public void tv2RootSurvivesRecreationAndNestedBackReturnsToIt() {
        tab(R.id.nav_profile); controller.recreate(); activity=controller.get(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
        activity.findViewById(R.id.btnFeedback).performClick(); settle();
        assertNotNull(activity.findViewById(R.id.btnSendFeedback));
        activity.findViewById(R.id.btnBack).performClick(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
    }
    @Test public void guestAndLegacyMockTokensCannotOpenReviewerContent() {
        new SessionManager(activity).saveSession("mock_jwt_token_old", "admin@example.com", "ADMIN", "Test");
        assertFalse(new SessionManager(activity).isReviewerOrAdmin());
        activity.openTv2Fragment(new ReviewerFragment()); settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.accessDeniedContent).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.reviewerContent).getVisibility());
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-night") public void sharedNightColorsAndLargeTextInflate() {
        userFixture();
        RuntimeEnvironment.setFontScale(2f);
        assertEquals(activity.getColor(R.color.background), activity.getColor(R.color.bg_screen));
        assertEquals(activity.getColor(R.color.ink), activity.getColor(R.color.text_title));
        tab(R.id.nav_report); assertNotNull(activity.findViewById(R.id.btnSubmitReport));
        activity.logout();settle();
        assertNotNull(activity.findViewById(R.id.etEmail));
    }
    @Test public void backFromAccountReportsReturnsToAccount() {
        userFixture();
        tab(R.id.nav_profile);activity.findViewById(R.id.btnMyReports).performClick();settle();
        activity.findViewById(R.id.btnBack).performClick();settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
        assertNotNull(activity.findViewById(R.id.btnFeedback));
    }
    @Test public void reselectingProfileReturnsFromNestedScreenToRoot() {
        tab(R.id.nav_profile); activity.findViewById(R.id.btnFeedback).performClick(); settle();
        assertNotNull(activity.findViewById(R.id.btnSendFeedback));
        tab(R.id.nav_profile);
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
        assertEquals(0, activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    private com.comeforfood.saferoute.feature.report.ReportDraft preparedDraft() {
        userFixture();
        com.comeforfood.saferoute.feature.report.ReportDraft d=new ViewModelProvider(activity).get(com.comeforfood.saferoute.feature.report.ReportDraft.class);
        d.image("android.resource://"+activity.getPackageName()+"/"+R.drawable.ic_camera);
        d.point(new com.comeforfood.saferoute.feature.shared.Point(10.755,106.678));
        d.category(3);d.severity(2);d.description("Old draft");d.step(7);
        activity.openTv2Fragment(new ReportWizardFragment());settle();return d;
    }
    private View text(View v,String label) {
        if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){
            View found=text(((android.view.ViewGroup)v).getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }
    private void discardDraft() {
        View discard=text(activity.findViewById(R.id.content),"Bỏ bản nháp để tạo báo cáo mới");
        assertNotNull(discard);discard.performClick();settle();
        androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();settle();
    }
    @Test public void discardingDraftResetsVisibleInputsAndDefaultSeverity() {
        com.comeforfood.saferoute.feature.report.ReportDraft d=preparedDraft();
        discardDraft();
        assertEquals("",d.description());assertEquals(-1,d.category());assertEquals(1,d.severity());
        assertEquals("",((EditText)activity.findViewById(R.id.etDescription)).getText().toString());
        assertEquals(-1,((android.widget.RadioGroup)activity.findViewById(R.id.categoryOptions)).getCheckedRadioButtonId());
        android.widget.RadioGroup severity=activity.findViewById(R.id.severityOptions);
        assertEquals(severity.getChildAt(1).getId(),severity.getCheckedRadioButtonId());
    }
    @Test public void selectingSamePhotoAfterDiscardLoadsPreviewAgain() {
        com.comeforfood.saferoute.feature.report.ReportDraft d=preparedDraft();String uri=d.image();
        discardDraft();
        android.widget.ImageView preview=activity.findViewById(R.id.imgPreview);
        android.graphics.drawable.Drawable empty=preview.getDrawable();
        d.image(uri);activity.findViewById(R.id.btnStartReport).performClick();settle();
        assertNotSame("New selection must replace the empty placeholder",empty,preview.getDrawable());
        assertSame(preview.getDrawable(),((android.widget.ImageView)activity.findViewById(R.id.reviewImage)).getDrawable());
    }
    private int spinners(View v) {
        int n=v instanceof android.widget.Spinner?1:0;
        if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)n+=spinners(((android.view.ViewGroup)v).getChildAt(i));
        return n;
    }
    @Test public void failedReanalysisClearsOldAiEditorsModelAndConsent() {
        com.comeforfood.saferoute.feature.report.ReportDraft d=preparedDraft();
        d.modelVersion("previous-model");
        d.detections(java.util.Collections.singletonList(new com.comeforfood.saferoute.core.model.DetectionDto("D40",.8f,java.util.Arrays.asList(.1f,.1f,.5f,.5f))));
        d.confirmed(true);
        // Resource previews are intentionally not uploadable: exercise synchronous AI failure.
        activity.findViewById(R.id.btnSimulateAi).performClick();settle();
        assertTrue(d.detections().isEmpty());
        assertEquals("Old detection editors must disappear",0,spinners(activity.findViewById(R.id.cardAiResult)));
        assertNull(d.modelVersion());assertFalse(d.confirmed());
    }

    @Test public void unreadableReplacementPhotoRemovesPreviousPreview() throws Exception {
        com.comeforfood.saferoute.feature.report.ReportDraft d=preparedDraft();
        android.widget.ImageView preview=activity.findViewById(R.id.imgPreview);
        android.graphics.drawable.Drawable old=preview.getDrawable();
        d.image("content://missing.qa.provider/not-an-image");
        activity.findViewById(R.id.btnStartReport).performClick();settle();
        long end=System.currentTimeMillis()+5000;
        while(d.image()!=null&&System.currentTimeMillis()<end){Thread.sleep(10);settle();}
        assertNull("Unreadable photo must be rejected",d.image());
        assertNotSame("Do not display the previous photo after replacement fails",old,preview.getDrawable());
        assertSame(preview.getDrawable(),((android.widget.ImageView)activity.findViewById(R.id.reviewImage)).getDrawable());
        assertFalse(d.confirmed());
    }
}
