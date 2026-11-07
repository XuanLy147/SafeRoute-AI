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
    @Before public void start() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get(); settle(); activity.openMap(); settle();
    }
    @After public void finish() { controller.pause().stop().destroy(); }
    private void settle() {
        shadowOf(Looper.getMainLooper()).idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void tab(int id) {
        ((BottomNavigationView)activity.findViewById(R.id.navigation)).setSelectedItemId(id); settle();
    }
    @Test public void reportAndProfileUseOneHostWithoutInventedGps() {
        tab(R.id.nav_report);
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ReportWizardFragment);
        assertEquals(1, new ViewModelProvider(activity).get(com.comeforfood.saferoute.feature.report.ReportDraft.class).step());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btnCamera).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btnPickGallery).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.tvCoordinates).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.mapHint).getVisibility());
        tab(R.id.nav_profile);
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ProfileFragment);
        assertEquals(activity.getString(R.string.account_guest),
                ((TextView)activity.findViewById(R.id.tvRole)).getText().toString());
    }
    @Test public void authDoesNotCreateFakeTokensOrReviewerRole() {
        tab(R.id.nav_profile); activity.findViewById(R.id.btnAccount).performClick(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof LoginFragment);
        ((EditText)activity.findViewById(R.id.etEmail)).setText("reviewer@example.com");
        ((EditText)activity.findViewById(R.id.etPassword)).setText("test-password");
        activity.findViewById(R.id.btnLogin).performClick(); settle();
        assertFalse(new SessionManager(activity).isLoggedIn());
        assertFalse(new SessionManager(activity).isReviewerOrAdmin());
        activity.findViewById(R.id.tvGoToRegister).performClick(); settle();
        ((EditText)activity.findViewById(R.id.etFullName)).setText("Test User");
        ((EditText)activity.findViewById(R.id.etEmail)).setText("test@example.com");
        ((EditText)activity.findViewById(R.id.etPassword)).setText("test-password");
        ((EditText)activity.findViewById(R.id.etConfirmPassword)).setText("test-password");
        activity.findViewById(R.id.btnRegister).performClick(); settle();
        assertFalse(new SessionManager(activity).isLoggedIn());
        activity.findViewById(R.id.btnAuthBack).performClick(); settle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.content) instanceof LoginFragment);
    }
    @Test public void reportsStayEmptyUntilConnectedAndProfileTabMatchesDestination() {
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
        RuntimeEnvironment.setFontScale(2f);
        assertEquals(activity.getColor(R.color.background), activity.getColor(R.color.bg_screen));
        assertEquals(activity.getColor(R.color.ink), activity.getColor(R.color.text_title));
        tab(R.id.nav_report); assertNotNull(activity.findViewById(R.id.btnSubmitReport));
        tab(R.id.nav_profile); activity.findViewById(R.id.btnAccount).performClick(); settle();
        assertNotNull(activity.findViewById(R.id.etEmail));
    }
    @Test public void backFromAccountReportsReturnsToAccount() {
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
}
