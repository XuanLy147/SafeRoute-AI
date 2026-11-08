package com.comeforfood.saferoute;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.*;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.admin.AdminUsersFragment;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w390dp-h844dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
public class RegressionSafetyTest {
    private MockWebServer server;
    private ApiClient api;
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private final AtomicInteger users=new AtomicInteger(), audits=new AtomicInteger();
    private volatile boolean slow;
    @Before public void setup() throws Exception {
        server=new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest r) {
                String path=r.getPath();
                MockResponse response=new MockResponse().setHeader("Content-Type","application/json");
                if(path.startsWith("/api/v1/admin/users")) {
                    users.incrementAndGet();
                    response.setBody("[{\"id\":41,\"email\":\"fixture@example.invalid\",\"role\":\"USER\"}]");
                    if(slow)response.setBodyDelay(400,TimeUnit.MILLISECONDS);
                } else if(path.startsWith("/api/v1/admin/role-audit")) {
                    audits.incrementAndGet();
                    response.setBody("[{\"id\":1,\"actor_id\":3,\"user_id\":41,\"from_role\":\"USER\",\"to_role\":\"REVIEWER\",\"reason\":\"QA audit fixture\",\"created_at\":\"2026-11-08T00:00:00Z\"}]");
                } else if(path.equals("/api/v1/auth/me")) response.setBody("{\"email\":\"admin@example.invalid\",\"role\":\"ADMIN\"}");
                else response.setBody("[]");
                return response;
            }
        });
        server.start();
        SafeRouteApp app=(SafeRouteApp)RuntimeEnvironment.getApplication();
        new SessionManager(app).clearSession();
        api=new ApiClient(app,server.url("/").toString());
        java.lang.reflect.Field field=SafeRouteApp.class.getDeclaredField("api");
        field.setAccessible(true);field.set(app,api);
        controller=Robolectric.buildActivity(MainActivity.class).setup();
        activity=controller.get();idle();
        new SessionManager(app).saveSession("qa-only","admin@example.invalid","ADMIN","QA");
        idle();
    }
    @After public void close() throws Exception {
        new SessionManager(activity).clearSession();
        controller.pause().stop().destroy();server.shutdown();
    }
    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void until(BooleanSupplier test) throws Exception {
        long end=System.currentTimeMillis()+5000;
        while(!test.getAsBoolean()&&System.currentTimeMillis()<end){idle();Thread.sleep(10);}
        idle();assertTrue("Expected UI state within 5 seconds",test.getAsBoolean());
    }
    private boolean contains(View v,String text) {
        if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)
            if(contains(((ViewGroup)v).getChildAt(i),text))return true;
        return false;
    }
    private void recreateView(Fragment f) {
        activity.getSupportFragmentManager().beginTransaction().detach(f).commitNow();
        activity.getSupportFragmentManager().beginTransaction().attach(f).commitNow();
        idle();
    }
    @Test public void adminReloadsAfterLeavingDuringRequest() throws Exception {
        slow=true;AdminUsersFragment f=new AdminUsersFragment();
        activity.openFeatureFragment(f);idle();until(()->users.get()>=1);
        recreateView(f);
        until(()->users.get()>=2&&contains(f.requireView(),"fixture@example.invalid"));
    }
    @Test public void adminHistoryModeSurvivesViewRecreation() throws Exception {
        AdminUsersFragment f=new AdminUsersFragment();activity.openFeatureFragment(f);idle();
        until(()->contains(f.requireView(),"fixture@example.invalid"));
        clickText(f.requireView(),"Lịch sử thay đổi quyền");idle();
        until(()->contains(f.requireView(),"QA audit fixture"));
        int previous=audits.get();recreateView(f);
        until(()->audits.get()>previous&&contains(f.requireView(),"QA audit fixture"));
    }
    private boolean clickText(View v,String text) {
        if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)){v.performClick();return true;}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)
            if(clickText(((ViewGroup)v).getChildAt(i),text))return true;
        return false;
    }
    @Test public void destroyedOwnerNeverStartsAWrite() throws Exception {
        LifecycleOwner owner=new LifecycleOwner(){
            final LifecycleRegistry lifecycle=new LifecycleRegistry(this);
            public Lifecycle getLifecycle(){return lifecycle;}
        };
        LifecycleRegistry lifecycle=(LifecycleRegistry)owner.getLifecycle();
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        retrofit2.Call<com.google.gson.JsonObject> call=
            api.service.vote("1",new com.comeforfood.saferoute.core.model.ReportDto.VoteRequest("SUPPORT",null));
        ApiCalls.enqueue(owner,call,r->fail("Destroyed success callback"),e->fail("Destroyed failure callback"));
        assertTrue("Destroyed view must cancel before enqueue",call.isCanceled());
        assertFalse("Destroyed view must not execute a write",call.isExecuted());
    }
}
