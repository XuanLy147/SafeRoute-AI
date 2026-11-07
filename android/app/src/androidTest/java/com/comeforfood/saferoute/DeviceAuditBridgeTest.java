package com.comeforfood.saferoute;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.report.ReportDraft;
import com.comeforfood.saferoute.feature.shared.Point;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in local device QA bridge. Test APK only; loopback + per-run private secret. No mocked API. */
public class DeviceAuditBridgeTest {
    private MainActivity activity;
    private SafeRouteApp app;
    private org.json.JSONArray nodes;
    private final java.util.List<View> views=new java.util.ArrayList<>();
    private void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
    private void collect(View v) throws JSONException {
        if(v.getVisibility()!=View.VISIBLE)return;
        int index=views.size();views.add(v);
        String id="";
        try{if(v.getId()!=View.NO_ID)id=v.getResources().getResourceEntryName(v.getId());}catch(Exception ignored){}
        JSONObject n=new JSONObject().put("index",index).put("id",id).put("class",v.getClass().getSimpleName())
            .put("enabled",v.isEnabled()).put("clickable",v.isClickable()).put("shown",v.isShown());
        if(v instanceof TextView){
            boolean secret=v instanceof EditText && ((((TextView)v).getInputType()&0xf0)==0x80 || (((TextView)v).getInputType()&0xf0)==0x90);
            n.put("text",secret?"[PASSWORD]":((TextView)v).getText().toString());
            if(v instanceof EditText)n.put("hint",String.valueOf(((EditText)v).getHint()));
        }
        if(v instanceof TextView && ((TextView)v).getError()!=null)n.put("error",((TextView)v).getError().toString());
        if(v instanceof android.widget.Checkable)n.put("checked",((android.widget.Checkable)v).isChecked());
        nodes.put(n);
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i));
    }
    private JSONObject state() throws Exception {
        for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof MainActivity)activity=(MainActivity)a;
        nodes=new JSONArray();views.clear();
        if(android.os.Build.VERSION.SDK_INT>=29){
            for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root.isShown())collect(root);
        }else collect(activity.getWindow().getDecorView());
        return new JSONObject().put("nodes",nodes).put("role",new SessionManager(app).getRole())
            .put("loggedIn",new SessionManager(app).isLoggedIn());
    }
    private View find(JSONObject request) throws Exception {
        String id=request.optString("id"),text=request.optString("text");Integer index=request.has("index")?request.getInt("index"):null;
        state();
        if(index!=null)return views.get(index);
        java.util.List<View> found=new java.util.ArrayList<>();
        for(int i=0;i<nodes.length();i++){
            JSONObject n=nodes.getJSONObject(i);
            if(!id.isEmpty() && id.equals(n.optString("id")))found.add(views.get(i));
            else if(id.isEmpty()&&!text.isEmpty()&&text.equals(n.optString("text")))found.add(views.get(i));
        }
        if(found.size()!=1)throw new IllegalArgumentException("Control matches: "+found.size());
        return found.get(0);
    }
    @Test public void controlledAudit() throws Exception {
        app=(SafeRouteApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        org.junit.Assume.assumeTrue("Opt-in physical-device audit only", "true".equals(InstrumentationRegistry.getArguments().getString("deviceAudit")));
        File configFile=new File(app.getFilesDir(),"device-audit-config.json");
        JSONObject config=new JSONObject(new String(java.nio.file.Files.readAllBytes(configFile.toPath()),StandardCharsets.UTF_8));
        String secret=config.getString("secret");
        Intent launch=new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        onMain(()->app.startActivity(launch));
        long launchDeadline=SystemClock.elapsedRealtime()+120000;
        while(activity==null && SystemClock.elapsedRealtime()<launchDeadline){
            onMain(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(a instanceof MainActivity)activity=(MainActivity)a;
            });SystemClock.sleep(100);
        }
        if(activity==null)throw new IllegalStateException("App did not resume");
        onMain(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        try(ServerSocket server=new ServerSocket(37123,1,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(5000);
            long deadline=SystemClock.elapsedRealtime()+45*60*1000;
            boolean stop=false;
            while(!stop && SystemClock.elapsedRealtime()<deadline){
                Socket socket;
                try{socket=server.accept();}catch(SocketTimeoutException ignored){continue;}
                try(Socket connection=socket){
                    connection.setSoTimeout(10000);
                    String line=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8)).readLine();
                    JSONObject request=new JSONObject(line);
                    if(!secret.equals(request.optString("secret")))throw new SecurityException("Invalid QA secret");
                    String action=request.getString("action");stop="stop".equals(action);
                    if("probe".equals(action)) {
                        JSONObject diagnostic=new JSONObject();
                        try {retrofit2.Response<?> response=app.api().service.reports().execute();diagnostic.put("http",response.code());if(response.errorBody()!=null)response.errorBody().close();}
                        catch(Exception e){diagnostic.put("exception",e.getClass().getName()).put("message",String.valueOf(e.getMessage()));if(e.getCause()!=null)diagnostic.put("cause",e.getCause().getClass().getName()+": "+e.getCause().getMessage());}
                        connection.getOutputStream().write((diagnostic.toString()+"\n").getBytes(StandardCharsets.UTF_8));continue;
                    }
                    AtomicReference<JSONObject> result=new AtomicReference<>();
                    onMain(()->{
                        try {
                            switch(action){
                                case "click": if(!find(request).performClick())throw new IllegalStateException("Control not clickable");break;
                                case "select":
                                    android.widget.ListView list=(android.widget.ListView)find(request);int position=request.getInt("position");
                                    list.performItemClick(list.getChildAt(position-list.getFirstVisiblePosition()),position,list.getAdapter().getItemId(position));break;
                                case "type": ((EditText)find(request)).setText(request.getString("value"));break;
                                case "back": activity.getOnBackPressedDispatcher().onBackPressed();break;
                                case "session":
                                    new SessionManager(app).saveSession(request.getString("token"),request.getString("email"),request.getString("role"),request.optString("name"));
                                    activity.openSessionHome();break;
                                case "logout":activity.logout();break;
                                case "photo":
                                    File directory=new File(app.getCacheDir(),"report-photos");directory.mkdirs();File file=new File(directory,"device-qa.png");
                                    Bitmap bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GRAY);
                                    try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
                                    ReportDraft draft=new ViewModelProvider(activity).get(ReportDraft.class);
                                    draft.image(FileProvider.getUriForFile(app,app.getPackageName()+".photos",file).toString());
                                    draft.point(new Point(10.755,106.678));draft.step(3);
                                    activity.openTv2Fragment(new com.comeforfood.saferoute.feature.report.ReportWizardFragment());break;
                                case "state":case "stop":break;
                                default:throw new IllegalArgumentException("Unknown action");
                            }
                            result.set(state());
                        }catch(Exception e){try{result.set(new JSONObject().put("error",e.getClass().getSimpleName()+": "+e.getMessage()));}catch(Exception ignored){}}
                    });
                    connection.getOutputStream().write((result.get().toString()+"\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally {onMain(()->activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));configFile.delete();}
    }
}
