package com.comeforfood.saferoute;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.comeforfood.saferoute.core.model.*;
import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.auth.*;
import com.comeforfood.saferoute.feature.report.*;
import com.comeforfood.saferoute.feature.reports.*;
import com.comeforfood.saferoute.feature.shared.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in real HTTP/PostGIS test. Run only with scripts/qa/verify_android_e2e.py.
 * Synthetic image/coordinates are test inputs, never synthetic server responses.
 */
@RunWith(AndroidJUnit4.class)
public class LiveBackendDeviceTest {
    /** Xiaomi may block ActivityScenario's synchronous activity launch. This opt-in
     * harness uses the same lifecycle-observed foreground launch as DeviceAuditBridgeTest. */
    private interface Scenario {
        void onActivity(java.util.function.Consumer<MainActivity> action);
        void recreate();
        void close();
    }
    private Scenario launch(SafeRouteApp app) {
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("physicalDevice"))) {
            ActivityScenario<MainActivity> standard=ActivityScenario.launch(MainActivity.class);
            return new Scenario(){
                public void onActivity(java.util.function.Consumer<MainActivity> action){standard.onActivity(action::accept);}
                public void recreate(){standard.recreate();}
                public void close(){standard.close();}
            };
        }
        return new Scenario() {
            MainActivity current;
            {
                main(()->app.startActivity(new android.content.Intent(app,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)));
                resumed(null);
            }
            void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
            void resumed(MainActivity previous) {
                long deadline=SystemClock.elapsedRealtime()+20000;
                AtomicBoolean ready=new AtomicBoolean();
                while(SystemClock.elapsedRealtime()<deadline&&!ready.get()) {
                    main(()->{
                        for(android.app.Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                            if(a instanceof MainActivity&&a!=previous){current=(MainActivity)a;ready.set(true);}
                    });
                    if(!ready.get())SystemClock.sleep(100);
                }
                assertTrue("Unlock phone and bring SafeRoute to foreground",ready.get());
                main(()->current.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            }
            public void onActivity(java.util.function.Consumer<MainActivity> action){main(()->action.accept(current));}
            public void recreate(){MainActivity previous=current;main(previous::recreate);resumed(previous);}
            public void close(){main(()->{current.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);current.finish();});}
        };
    }
    private Scenario scenario;
    private void ui(java.util.function.Consumer<MainActivity> action){scenario.onActivity(action::accept);if(!"true".equals(InstrumentationRegistry.getArguments().getString("physicalDevice")))InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private void waitFor(Predicate<MainActivity> condition) {
        long deadline=SystemClock.elapsedRealtime()+45000;AtomicBoolean done=new AtomicBoolean();
        while(SystemClock.elapsedRealtime()<deadline){ui(a->done.set(condition.test(a)));if(done.get())return;SystemClock.sleep(150);}
        fail("UI condition timed out; no success claimed");
    }
    private static View text(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private static EditText edit(View root){if(root instanceof EditText)return(EditText)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){EditText found=edit(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}return null;}
    @Test public void realRegistrationPhotoAiReportVoteReviewMapAndPersistence() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();assertEquals("true",args.getString("backendE2E"));
        SafeRouteApp app=ApplicationProvider.getApplicationContext();SessionManager session=new SessionManager(app);session.clearSession();
        ApiService api=app.api().service;
        String email="android-"+UUID.randomUUID()+"@example.com";
        scenario=launch(app);
        try {
            ui(a->a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            ui(a->a.openTv2Fragment(new RegisterFragment()));
            ui(a->{((EditText)a.findViewById(R.id.etFullName)).setText("Android E2E");((EditText)a.findViewById(R.id.etEmail)).setText(email);((EditText)a.findViewById(R.id.etPassword)).setText("local-device-test-password");((EditText)a.findViewById(R.id.etConfirmPassword)).setText("local-device-test-password");a.findViewById(R.id.btnRegister).performClick();});
            waitFor(a->session.isLoggedIn()&&a.getSupportFragmentManager().findFragmentById(R.id.content) instanceof com.comeforfood.saferoute.feature.map.MapFragment);
            assertEquals("USER",api.me().execute().body().getRole());
            File dir=new File(app.getCacheDir(),"report-photos");assertTrue(dir.isDirectory()||dir.mkdirs());File photo=new File(dir,"e2e.png");
            Bitmap bitmap=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GRAY);try(FileOutputStream out=new FileOutputStream(photo)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
            String uri=FileProvider.getUriForFile(app,app.getPackageName()+".photos",photo).toString();
            ui(a->{ReportDraft draft=new ViewModelProvider(a).get(ReportDraft.class);draft.image(uri);draft.point(new Point(10.755,106.678));draft.step(3);a.openTv2Fragment(new ReportWizardFragment());});
            ui(a->a.findViewById(R.id.btnSimulateAi).performClick());
            waitFor(a->{TextView title=a.findViewById(R.id.tvAiTitle);return title!=null&&!title.getText().toString().contains("Đang phân tích");});
            ui(a->a.findViewById(R.id.btnManual).performClick());
            ui(a->{((RadioButton)((RadioGroup)a.findViewById(R.id.categoryOptions)).getChildAt(1)).performClick();((EditText)a.findViewById(R.id.etDescription)).setText("Synthetic Android device integration input");a.findViewById(R.id.btnReview).performClick();});
            ui(a->{((CheckBox)a.findViewById(R.id.confirmReport)).setChecked(true);a.findViewById(R.id.btnSubmitReport).performClick();});
            waitFor(a->a.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ReportDetailFragment);
            List<ReportDto.ReportResponse> mine=api.mine().execute().body();assertNotNull(mine);assertEquals(1,mine.size());String id=mine.get(0).getId();assertEquals("PENDING_VERIFICATION",mine.get(0).getStatus());assertNotNull(mine.get(0).getImageUrl());
            waitFor(a->text(a.findViewById(R.id.flowActions),"Đồng tình")!=null);
            ui(a->{View vote=text(a.findViewById(R.id.flowActions),"Đồng tình");assertNotNull(vote);vote.performClick();});
            waitFor(a->{TextView body=a.findViewById(R.id.flowBody);return body!=null&&body.getText().toString().contains("Đồng tình: 1");});
            scenario.recreate();ui(a->a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));assertEquals(id,api.mine().execute().body().get(0).getId());
            ui(MainActivity::logout);
            ui(a->{((EditText)a.findViewById(R.id.etEmail)).setText("android-review@example.com");((EditText)a.findViewById(R.id.etPassword)).setText("local-device-test-password");a.findViewById(R.id.btnLogin).performClick();});
            waitFor(a->session.isReviewerOrAdmin());assertEquals("REVIEWER",api.me().execute().body().getRole());
            assertTrue(api.queue().execute().body().stream().anyMatch(r->id.equals(r.getId())));
            ui(a->a.openTv2Fragment(ReportDetailFragment.at(id)));
            waitFor(a->text(a.findViewById(R.id.content),"Xác minh")!=null);
            ui(a->{edit(a.findViewById(R.id.flowActions)).setText("Reviewed synthetic device test image");text(a.findViewById(R.id.flowActions),"Xác minh").performClick();});
            waitFor(a->{TextView body=a.findViewById(R.id.flowBody);return body!=null&&body.getText().toString().startsWith("Đã xác minh");});
            ReportDto.ReportResponse detail=api.detail(id).execute().body();assertEquals("VERIFIED",detail.getStatus());assertEquals(1,detail.getAuditLogs().size());
            ui(MainActivity::openMap);
            waitFor(a->{Tv1ViewModel.State<List<com.comeforfood.saferoute.feature.map.Hazard>> state=new ViewModelProvider(a).get(Tv1ViewModel.class).reports().getValue();return state!=null&&state.data!=null&&state.data.stream().anyMatch(h->id.equals(h.id)&&h.status==com.comeforfood.saferoute.feature.map.Hazard.Status.VERIFIED);});
            assertFalse(api.queue().execute().body().stream().anyMatch(r->id.equals(r.getId())));
            if("true".equals(args.getString("liveMaps"))) {
                waitFor(a->{TextView status=a.findViewById(R.id.map_status);return status!=null&&a.getString(R.string.map_ready).contentEquals(status.getText());});
                File screenshots=new File(app.getExternalFilesDir(null),"release-evidence");assertTrue(screenshots.isDirectory()||screenshots.mkdirs());
                Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screen);
                try(FileOutputStream out=new FileOutputStream(new File(screenshots,"live-map.png"))){assertTrue(screen.compress(Bitmap.CompressFormat.PNG,100,out));}screen.recycle();
            }
            assertEquals(409,api.review(id,new ReportDto.ReviewDecisionRequest("VERIFIED","Repeat must fail",null)).execute().code());
            if("true".equals(args.getString("liveRoutes"))) {
                ui(a->a.openRoutes(null));
                ui(a->{((EditText)a.findViewById(R.id.origin)).setText("10.755, 106.678");((EditText)a.findViewById(R.id.destination)).setText("10.77, 106.69");a.findViewById(R.id.compare).performClick();});
                waitFor(a->{Tv1ViewModel.State<com.comeforfood.saferoute.feature.routing.RouteComparison> routes=new ViewModelProvider(a).get(Tv1ViewModel.class).routes().getValue();return routes!=null&&!routes.loading&&routes.data!=null&&!routes.data.routes.isEmpty();});
                ui(a->{com.comeforfood.saferoute.feature.routing.RouteComparison routes=new ViewModelProvider(a).get(Tv1ViewModel.class).routes().getValue().data;assertFalse(routes.scoreDescription.isEmpty());assertFalse(routes.scoreDescription.contains("UNAVAILABLE"));for(com.comeforfood.saferoute.feature.routing.Route route:routes.routes){assertTrue(route.points.size()>=2);if("true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("experimentalScoring")))assertNotNull(route.riskScore);else assertNull(route.riskScore);assertNotNull(route.nearbyHazards);assertEquals(30.0,route.hazardRadiusMeters,0.001);}});
            }

            if("true".equals(args.getString("adminE2E"))) {
                ui(MainActivity::logout);
                ui(a->{((EditText)a.findViewById(R.id.etEmail)).setText("android-admin@example.com");((EditText)a.findViewById(R.id.etPassword)).setText("local-device-test-password");a.findViewById(R.id.btnLogin).performClick();});
                waitFor(a->"ADMIN".equals(session.getRole())&&session.isVerified());
                ui(a->a.openFlow(FlowPageFragment.Page.ADMIN_REPORTS));
                String expectedAdminRow="#"+id+" \u00b7 "+com.comeforfood.saferoute.core.model.HazardLabel.display(detail.getCategoryUser())
                        +" \u00b7 "+detail.getStatusDisplay()+"\n"+com.comeforfood.saferoute.feature.shared.DisplayText.time(detail.getCreatedAt())
                        +"\n"+(detail.getDescription()==null?"":detail.getDescription());
                waitFor(a->text(a.findViewById(R.id.content),expectedAdminRow)!=null);
                ui(a->a.openFlow(FlowPageFragment.Page.ADMIN_AUDIT));
                waitFor(a->text(a.findViewById(R.id.content),"Lịch sử quyền tài khoản")!=null);
                ui(a->text(a.findViewById(R.id.content),"Lịch sử quyền tài khoản").performClick());
                waitFor(a->a.findViewById(R.id.flowBody) instanceof TextView && ((TextView)a.findViewById(R.id.flowBody)).getText().toString().equals("Chưa có thay đổi quyền."));
                ui(a->a.openFlow(FlowPageFragment.Page.ADMIN_METRICS));
                waitFor(a->text(a.findViewById(R.id.content),"Số liệu hiện tại từ hệ thống")!=null);
                ui(a->a.openFlow(FlowPageFragment.Page.ADMIN_SETTINGS));
                waitFor(a->text(a.findViewById(R.id.content),"Quản lý quyền tài khoản")!=null);
                ui(a->text(a.findViewById(R.id.content),"Quản lý quyền tài khoản").performClick());
                waitFor(a->a.findViewById(R.id.flowBody) instanceof TextView && ((TextView)a.findViewById(R.id.flowBody)).getText().toString().startsWith("Chọn tài khoản"));
                scenario.recreate();
                waitFor(a->a.findViewById(R.id.flowBody) instanceof TextView && ((TextView)a.findViewById(R.id.flowBody)).getText().toString().startsWith("Chọn tài khoản"));
                ui(MainActivity::logout);
                waitFor(a->a.findViewById(R.id.btnLogin)!=null);
            }
        } finally {session.clearSession();scenario.close();}
    }
}
