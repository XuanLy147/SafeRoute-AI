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
    private okhttp3.mockwebserver.MockWebServer server;
    @Before public void launch() throws Exception {
        server=new okhttp3.mockwebserver.MockWebServer();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public okhttp3.mockwebserver.MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                return new okhttp3.mockwebserver.MockResponse().setResponseCode(503).setBody("{}");
            }
        });server.start();
        SafeRouteApp app=(SafeRouteApp)RuntimeEnvironment.getApplication();
        java.lang.reflect.Field field=SafeRouteApp.class.getDeclaredField("api");field.setAccessible(true);
        field.set(app,new com.comeforfood.saferoute.core.network.ApiClient(app,server.url("/").toString()));
        controller=Robolectric.buildActivity(MainActivity.class).setup();activity=controller.get();settle();
    }
    @After public void finish() throws Exception {new SessionManager(activity).clearSession();controller.pause().stop().destroy();server.shutdown();}
    private void settle(){shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();shadowOf(Looper.getMainLooper()).idle();}
    private void click(int id){assertNotNull(activity.findViewById(id));activity.findViewById(id).performClick();settle();}
    private void tab(int id){((BottomNavigationView)activity.findViewById(R.id.navigation)).setSelectedItemId(id);settle();}
    private void tag(String tag){View v=activity.findViewById(R.id.content).findViewWithTag(tag);assertNotNull(v);v.performClick();settle();}
    private void back(){activity.getOnBackPressedDispatcher().onBackPressed();settle();}
    private void recreate(){controller.recreate();activity=controller.get();settle();}
    private Object screen(){return activity.getSupportFragmentManager().findFragmentById(R.id.content);}
    @Test public void hazardPanelCollapsesAndRestoresAcrossRecreation() {
        activity.openMap();settle();
        assertEquals(View.VISIBLE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
        click(R.id.hazardPanelToggle);
        assertEquals(View.GONE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.status).getVisibility());
        click(R.id.refresh);
        assertEquals(View.GONE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
        recreate();
        assertEquals(View.GONE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
        click(R.id.hazardPanelToggle);
        assertEquals(View.VISIBLE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
    }
    @Test public void hazardPanelSupportsBothSwipeDirections() {
        activity.openMap();settle();
        swipePanel(20f,100f);
        assertEquals(View.GONE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
        swipePanel(100f,20f);
        assertEquals(View.VISIBLE,activity.findViewById(R.id.hazardPanelContent).getVisibility());
    }
    private void swipePanel(float from, float to) {
        View handle=activity.findViewById(R.id.hazardPanelToggle);
        long now=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,20f,from,0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+100,android.view.MotionEvent.ACTION_UP,20f,to,0);
        handle.dispatchTouchEvent(down);handle.dispatchTouchEvent(up);
        down.recycle();up.recycle();settle();
    }
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
        tab(R.id.nav_my_reports);assertTrue(screen() instanceof LoginFragment);
    }
    @Test public void reportStepsValidateKeepDraftAndNeverFabricateSuccess() {
        new SessionManager(activity).saveSession("test-only-user", "user@example.invalid", "USER", "Test user");settle();
        activity.openMap();settle();tab(R.id.nav_report);
        assertEquals(View.GONE,activity.findViewById(R.id.navigation).getVisibility());
        ReportDraft draft=new ViewModelProvider(activity).get(ReportDraft.class);
        assertEquals(1,draft.step());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.btnCamera).getVisibility());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.btnPickGallery).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.stepStart).getVisibility());
        // Test-only drawable as the selected photo; no private image or live API.
        draft.image("android.resource://"+activity.getPackageName()+"/"+R.drawable.ic_map);draft.step(2);recreate();
        draft=new ViewModelProvider(activity).get(ReportDraft.class);
        click(R.id.btnUseImage);assertEquals(3,draft.step());
        assertEquals(View.GONE,activity.findViewById(R.id.mapHint).getVisibility());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.tvCoordinates).getVisibility());
        assertEquals(activity.getString(R.string.my_location),((com.google.android.material.button.MaterialButton)activity.findViewById(R.id.btnAdjustPin)).getText().toString());
        assertEquals(activity.getString(R.string.report_location_or),((TextView)((android.widget.LinearLayout)activity.findViewById(R.id.stepGps)).getChildAt(4)).getText().toString());
        click(R.id.btnSimulateAi);assertEquals(3,draft.step());
        EditText manualLocation=(EditText)activity.findViewById(R.id.manualLocation);
        assertEquals(activity.getString(R.string.report_manual_location_hint),manualLocation.getHint().toString());
        final String[] searchedAddress={null};
        ((SafeRouteApp)activity.getApplication()).setAddressSearch((query,callback)->{
            searchedAddress[0]=query;
            callback.success(java.util.Collections.singletonList(
                    new com.comeforfood.saferoute.feature.routing.AddressSearch.Place("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",new Point(10.75,106.67))));
            return ()->{};
        });
        manualLocation.setText("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh");
        click(R.id.btnSimulateAi);
        assertEquals(4,draft.step());
        assertEquals("123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",searchedAddress[0]);
        assertTrue(draft.point().sameAs(new Point(10.75,106.67)));
        assertEquals("Địa chỉ: 123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh",
                ((TextView)activity.findViewById(R.id.tvCoordinates)).getText().toString());
        assertEquals(activity.getString(R.string.ai_unavailable_title),((TextView)activity.findViewById(R.id.tvAiTitle)).getText().toString());
        assertEquals(View.GONE,activity.findViewById(R.id.tvAiDisclaimer).getVisibility());
        assertEquals(activity.getString(R.string.report_manual),((com.google.android.material.button.MaterialButton)activity.findViewById(R.id.btnManual)).getText().toString());
        click(R.id.btnManual);click(R.id.btnReview);assertEquals(5,draft.step());
        RadioGroup categoryOptions=(RadioGroup)activity.findViewById(R.id.categoryOptions);
        assertEquals(4,categoryOptions.getChildCount());
        assertEquals(java.util.Arrays.asList("Nứt đường","Ổ gà","Ngập nước","Vật cản"),java.util.Arrays.asList(
                ((RadioButton)categoryOptions.getChildAt(0)).getText().toString(),
                ((RadioButton)categoryOptions.getChildAt(1)).getText().toString(),
                ((RadioButton)categoryOptions.getChildAt(2)).getText().toString(),
                ((RadioButton)categoryOptions.getChildAt(3)).getText().toString()));
        ((RadioButton)categoryOptions.getChildAt(3)).performClick();
        ((EditText)activity.findViewById(R.id.etDescription)).setText("Test-only report draft");
        click(R.id.btnReview);assertEquals(6,draft.step());
        String reviewSummary=((TextView)activity.findViewById(R.id.reviewSummary)).getText().toString();
        assertTrue(reviewSummary.contains("Vị trí: 123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh"));
        assertFalse(reviewSummary.contains("10.750000, 106.670000"));
        click(R.id.btnSubmitReport);assertEquals(6,draft.step());
        ((CheckBox)activity.findViewById(R.id.confirmReport)).setChecked(true);
        recreate();draft=new ViewModelProvider(activity).get(ReportDraft.class);
        assertTrue(draft.confirmed());assertEquals("Test-only report draft",draft.description());assertEquals(3,draft.category());
        click(R.id.btnSubmitReport);assertEquals(7,draft.step());
        click(R.id.btnRetryReport);assertEquals(7,draft.step());
        back();assertEquals(6,draft.step());back();assertEquals(5,draft.step());
    }
    @Test public void reportTabSkipsIntroductionAndBackReturnsToMap() {
        new SessionManager(activity).saveSession("test-only-user","user@example.invalid","USER","QA");settle();
        activity.openMap();settle();tab(R.id.nav_report);
        assertEquals(1,new ViewModelProvider(activity).get(ReportDraft.class).step());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.btnCamera).getVisibility());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.btnPickGallery).getVisibility());
        click(R.id.btnBack);
        assertTrue(screen() instanceof MapFragment);
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
        tab(R.id.nav_role_users);assertTrue(screen() instanceof com.comeforfood.saferoute.feature.admin.AdminUsersFragment);
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
