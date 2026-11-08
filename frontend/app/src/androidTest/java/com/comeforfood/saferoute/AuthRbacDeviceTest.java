package com.comeforfood.saferoute;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.comeforfood.saferoute.core.network.ApiClient;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.auth.LoginFragment;
import com.comeforfood.saferoute.feature.shared.FlowPageFragment;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Synthetic HTTP fixtures only. Never changes roles in the user's database. */
public class AuthRbacDeviceTest {
    private ActivityScenario<MainActivity> scenario;
    private TextView text(View view,String value) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(value))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
    private void awaitText(String value) throws Exception {
        long deadline=System.currentTimeMillis()+8000;AtomicBoolean found=new AtomicBoolean();
        while(System.currentTimeMillis()<deadline){
            scenario.onActivity(a->found.set(text(a.findViewById(R.id.content),value)!=null));
            if(found.get())return;Thread.sleep(100);
        }
        fail("Expected visible fixture text: "+value);
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Unlock the phone for screenshot verification",bitmap);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name+".png");
        try(FileOutputStream output=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }
    @Test(timeout=60000) public void loginValidationAdminDirectoryAndLogout() throws Exception {
        SafeRouteApp app=(SafeRouteApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        SessionManager session=new SessionManager(app);
        String token=session.getToken(),email=session.getEmail(),role=session.getRole(),name=session.getFullName();
        java.lang.reflect.Field field=SafeRouteApp.class.getDeclaredField("api");field.setAccessible(true);Object old=field.get(app);
        try(MockWebServer server=new MockWebServer()) {
            server.setDispatcher(new Dispatcher(){
                @Override public MockResponse dispatch(RecordedRequest request){
                    String path=request.getPath();String body;
                    if(path.startsWith("/api/v1/auth/me"))body="{\"email\":\"admin@example.invalid\",\"role\":\"ADMIN\",\"full_name\":\"QA Admin\"}";
                    else if(path.startsWith("/api/v1/admin/users"))body="[{\"id\":42,\"email\":\"member@example.invalid\",\"full_name\":\"QA Member\",\"role\":\"USER\"}]";
                    else if(path.startsWith("/api/v1/admin/role-audit"))body="[{\"id\":1,\"actor_id\":3,\"user_id\":42,\"from_role\":\"USER\",\"to_role\":\"REVIEWER\",\"reason\":\"QA fixture only\",\"created_at\":\"2026-11-08T00:00:00Z\"}]";
                    else return new MockResponse().setResponseCode(401).setBody("{}");
                    return new MockResponse().setHeader("Content-Type","application/json").setBody(body);
                }
            });server.start();session.clearSession();field.set(app,new ApiClient(app,server.url("/").toString()));
            scenario=ActivityScenario.launch(MainActivity.class);
            scenario.onActivity(a->a.openFeatureFragment(new LoginFragment()));
            awaitText("Đăng nhập");
            scenario.onActivity(a->{
                ((EditText)a.findViewById(R.id.etEmail)).setText("bad-email");
                a.findViewById(R.id.btnLogin).performClick();
                assertNotNull(((EditText)a.findViewById(R.id.etEmail)).getError());
            });
            capture("auth-login-validation");
            scenario.onActivity(a->{
                session.saveSession("synthetic-admin-fixture","admin@example.invalid","ADMIN","QA Admin");
                a.openSessionHome();
            });
            awaitText(app.getString(R.string.role_admin_home));
            scenario.onActivity(a->a.openFlow(FlowPageFragment.Page.ADMIN_USERS));
            awaitText("member@example.invalid");capture("auth-admin-users");
            scenario.onActivity(a->text(a.findViewById(R.id.content),"Lịch sử thay đổi quyền").performClick());
            awaitText("QA fixture only");capture("auth-role-history");
            scenario.onActivity(MainActivity::logout);
            awaitText("Đăng nhập");assertFalse(session.isLoggedIn());
        } finally {
            if(scenario!=null)scenario.close();
            field.set(app,old);
            if(token==null)session.clearSession();else session.saveSession(token,email,role,name);
        }
    }
}
