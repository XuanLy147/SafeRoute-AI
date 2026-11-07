package com.comeforfood.saferoute;

import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.auth.*;
import com.comeforfood.saferoute.feature.map.*;
import com.comeforfood.saferoute.feature.report.*;
import com.comeforfood.saferoute.feature.shared.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w390dp-h844dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
public class HtmlFlowTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void launch() {
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();settle();
    }
    @After public void finish() {new SessionManager(activity).clearSession();controller.pause().stop().destroy();}
    private void settle(){shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();shadowOf(Looper.getMainLooper()).idle();}
    private void click(int id){assertNotNull(activity.findViewById(id));activity.findViewById(id).performClick();settle();}
    private void tab(int id){((BottomNavigationView)activity.findViewById(R.id.navigation)).setSelectedItemId(id);settle();}
    private void tag(String tag){View v=activity.findViewById(R.id.content).findViewWithTag(tag);assertNotNull(v);v.performClick();settle();}
    private void back(){activity.getOnBackPressedDispatcher().onBackPressed();settle();}
    private void recreate(){controller.recreate();activity=controller.get();settle();}
    private Object screen(){return activity.getSupportFragmentManager().findFragmentById(R.id.content);}
    @Test public void firstRunFollowsIntroductionPermissionsLoginAndExplicitGuest() {
        assertTrue(screen() instanceof WelcomeFragment);
        assertEquals(View.GONE,activity.findViewById(R.id.navigation).getVisibility());
        click(R.id.btnStart);
        for(int i=0;i<3;i++){assertTrue(screen() instanceof OnboardingFragment);click(R.id.introNext);}
        assertEquals(activity.getString(R.string.intro_permissions_title),((TextView)activity.findViewById(R.id.introTitle)).getText().toString());
        recreate();click(R.id.introNext);assertTrue(screen() instanceof LoginFragment);
        click(R.id.tvGoToRegister);assertTrue(screen() instanceof RegisterFragment);
        back();assertTrue(screen() instanceof LoginFragment);
        click(R.id.btnGuest);assertTrue(screen() instanceof MapFragment);
        assertFalse(new SessionManager(activity).isLoggedIn());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.navigation).getVisibility());
        assertNull(((BottomNavigationView)activity.findViewById(R.id.navigation)).getMenu().findItem(R.id.nav_routes));
        assertNotNull(((BottomNavigationView)activity.findViewById(R.id.navigation)).getMenu().findItem(R.id.nav_my_reports));
    }
    @Test public void returningGuestStartsAtLoginWithoutRepeatingIntroduction() {
        activity.finishIntroduction();settle();controller.pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();settle();
        assertTrue(screen() instanceof LoginFragment);
    }
    @Test public void mapOwnsRoutingAndAccountPagesUseBackStack() {
        activity.openMap();settle();click(R.id.routes);
        assertEquals(R.id.nav_map,((BottomNavigationView)activity.findViewById(R.id.navigation)).getSelectedItemId());
        back();assertTrue(screen() instanceof MapFragment);
        click(R.id.searchArea);assertNotNull(activity.findViewById(R.id.areaQuery));back();
        click(R.id.openFilters);tag("apply");assertTrue(screen() instanceof MapFragment);
        tab(R.id.nav_profile);click(R.id.btnSettings);
        assertEquals(FlowPageFragment.Page.SETTINGS,((FlowPageFragment)screen()).page());
        back();assertNotNull(activity.findViewById(R.id.btnSettings));
        tab(R.id.nav_my_reports);assertEquals(View.VISIBLE,activity.findViewById(R.id.reportsUnavailable).getVisibility());
    }
    @Test public void reportStepsValidateKeepDraftAndNeverFabricateSuccess() {
        activity.openMap();settle();tab(R.id.nav_report);
        assertEquals(View.GONE,activity.findViewById(R.id.navigation).getVisibility());
        ReportDraft draft=new ViewModelProvider(activity).get(ReportDraft.class);
        click(R.id.btnStartReport);assertEquals(1,draft.step());
        // Test-only drawable as the selected photo; no private image or live API.
        draft.image("android.resource://"+activity.getPackageName()+"/"+R.drawable.ic_map);draft.step(2);recreate();
        draft=new ViewModelProvider(activity).get(ReportDraft.class);
        click(R.id.btnUseImage);assertEquals(3,draft.step());
        click(R.id.btnSimulateAi);assertEquals(3,draft.step());
        draft.point(new Point(10.75,106.67));click(R.id.btnSimulateAi);assertEquals(4,draft.step());
        assertEquals(activity.getString(R.string.ai_unavailable_title),((TextView)activity.findViewById(R.id.tvAiTitle)).getText().toString());
        click(R.id.btnManual);click(R.id.btnReview);assertEquals(5,draft.step());
        ((RadioButton)((RadioGroup)activity.findViewById(R.id.categoryOptions)).getChildAt(3)).performClick();
        ((EditText)activity.findViewById(R.id.etDescription)).setText("Test-only report draft");
        click(R.id.btnReview);assertEquals(6,draft.step());
        click(R.id.btnSubmitReport);assertEquals(6,draft.step());
        ((CheckBox)activity.findViewById(R.id.confirmReport)).setChecked(true);
        recreate();draft=new ViewModelProvider(activity).get(ReportDraft.class);
        assertTrue(draft.confirmed());assertEquals("Test-only report draft",draft.description());assertEquals(3,draft.category());
        click(R.id.btnSubmitReport);assertEquals(7,draft.step());
        click(R.id.btnRetryReport);assertEquals(7,draft.step());
        back();assertEquals(6,draft.step());back();assertEquals(5,draft.step());
    }
    @Test public void reviewerAndAdminMenusAreDistinctAndGuestIsDeniedEveryRestrictedPage() {
        for(FlowPageFragment.Page page:FlowPageFragment.Page.values())if(page.restricted()){
            activity.openFlow(page);settle();
            assertEquals(View.VISIBLE,activity.findViewById(R.id.accessDeniedContent).getVisibility());
            assertEquals(View.GONE,activity.findViewById(R.id.reviewerContent).getVisibility());
        }
        SessionManager session=new SessionManager(activity);
        session.saveSession("test-only-role-fixture","review@example.invalid","REVIEWER","Test reviewer");
        activity.openSessionHome();settle();
        assertEquals(FlowPageFragment.Page.REVIEW_HOME,((FlowPageFragment)screen()).page());
        tag("REVIEW_QUEUE");assertEquals(FlowPageFragment.Page.REVIEW_QUEUE,((FlowPageFragment)screen()).page());back();
        activity.openFlow(FlowPageFragment.Page.ADMIN_HOME);settle();
        assertEquals(View.VISIBLE,activity.findViewById(R.id.accessDeniedContent).getVisibility());
        session.saveSession("test-only-role-fixture","admin@example.invalid","ADMIN","Test admin");
        activity.openSessionHome();settle();
        assertEquals(FlowPageFragment.Page.ADMIN_HOME,((FlowPageFragment)screen()).page());
        tab(R.id.nav_role_users);tag("ADMIN_ROLES");
        assertEquals(View.GONE,activity.findViewById(R.id.navigation).getVisibility());
        activity.logout();settle();assertTrue(screen() instanceof LoginFragment);assertFalse(session.isLoggedIn());
    }
    @Test @Config(qualifiers="w640dp-h360dp-land-night") public void roleScreensRenderWithLargeTextAndRecreate() {
        RuntimeEnvironment.setFontScale(1.5f);
        new SessionManager(activity).saveSession("test-only-role-fixture","a@example.invalid","ADMIN","Test");
        for(FlowPageFragment.Page page:FlowPageFragment.Page.values()) {
            activity.openFlow(page);settle();assertNotNull(activity.findViewById(R.id.flowTitle));
        }
        activity.openFlow(FlowPageFragment.Page.REVIEW_REJECT);settle();tag("decision");
        assertFalse(new SessionManager(activity).getToken().isEmpty());
        recreate();assertEquals(FlowPageFragment.Page.REVIEW_REJECT,((FlowPageFragment)screen()).page());
    }
}
