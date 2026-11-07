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
    private ActivityScenario<MainActivity> scenario;
    private void ui(java.util.function.Consumer<MainActivity> action){scenario.onActivity(action::accept);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
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
        scenario=ActivityScenario.launch(MainActivity.class);
        try {
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
            ui(a->{((RadioButton)((RadioGroup)a.findViewById(R.id.categoryOptions)).getChildAt(3)).performClick();((EditText)a.findViewById(R.id.etDescription)).setText("Synthetic Android device integration input");a.findViewById(R.id.btnReview).performClick();});
            ui(a->{((CheckBox)a.findViewById(R.id.confirmReport)).setChecked(true);a.findViewById(R.id.btnSubmitReport).performClick();});
            waitFor(a->a.getSupportFragmentManager().findFragmentById(R.id.content) instanceof ReportDetailFragment);
            List<ReportDto.ReportResponse> mine=api.mine().execute().body();assertNotNull(mine);assertEquals(1,mine.size());String id=mine.get(0).getId();assertEquals("PENDING_VERIFICATION",mine.get(0).getStatus());assertNotNull(mine.get(0).getImageUrl());
            waitFor(a->text(a.findViewById(R.id.flowActions),"Đồng tình")!=null);
            ui(a->{View vote=text(a.findViewById(R.id.flowActions),"Đồng tình");assertNotNull(vote);vote.performClick();});
            waitFor(a->{TextView body=a.findViewById(R.id.flowBody);return body!=null&&body.getText().toString().contains("Đồng tình: 1");});
            scenario.recreate();assertEquals(id,api.mine().execute().body().get(0).getId());
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
                ui(a->{com.comeforfood.saferoute.feature.routing.RouteComparison routes=new ViewModelProvider(a).get(Tv1ViewModel.class).routes().getValue().data;assertTrue(routes.scoreDescription.contains("UNAVAILABLE"));for(com.comeforfood.saferoute.feature.routing.Route route:routes.routes){assertTrue(route.points.size()>=2);assertNull(route.riskScore);}});
            }

        } finally {session.clearSession();scenario.close();}
    }
}
