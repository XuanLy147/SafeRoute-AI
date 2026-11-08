package com.comeforfood.saferoute;
import android.os.Looper;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.core.network.ApiClient;
import com.comeforfood.saferoute.feature.auth.AuthViewModel;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AuthFlowTest {
    private MockWebServer server;
    private SessionManager session;
    private AuthViewModel model;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();
        session=new SessionManager(RuntimeEnvironment.getApplication());session.clearSession();
        model=new AuthViewModel(RuntimeEnvironment.getApplication(),new ApiClient(RuntimeEnvironment.getApplication(),server.url("/").toString()).service);
    }
    @After public void close() throws Exception {session.clearSession();server.shutdown();}
    private void json(int code,String body){server.enqueue(new MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(body));}
    private void complete() throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(model.state.getValue().loading && System.currentTimeMillis()<until){
            Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("Auth operation did not finish",model.state.getValue().loading);
    }
    @Test public void roleComesFromMeAndDoubleSubmitIsIgnored() throws Exception {
        json(200,"{\"access_token\":\"candidate\",\"token_type\":\"bearer\",\"role\":\"ADMIN\"}");
        json(200,"{\"email\":\"a@example.com\",\"role\":\"USER\",\"full_name\":\"A\"}");
        model.submit(null,"a@example.com","password");
        model.submit(null,"a@example.com","password");
        complete();
        assertTrue(model.state.getValue().complete);
        assertEquals("USER",session.getRole());assertFalse(session.isReviewerOrAdmin());
        assertEquals(2,server.getRequestCount());
        assertNull(server.takeRequest().getHeader("Authorization"));
        assertEquals("Bearer candidate",server.takeRequest().getHeader("Authorization"));
    }
    @Test public void failedProfileNeverCreatesPartiallyAuthenticatedSession() throws Exception {
        json(200,"{\"access_token\":\"candidate\",\"token_type\":\"bearer\",\"role\":\"ADMIN\"}");
        json(503,"{}");
        model.submit(null,"a@example.com","password");complete();
        assertFalse(session.isLoggedIn());assertFalse(model.state.getValue().message.isEmpty());
    }
    @Test public void offlineRestoreKeepsCredentialsForRetry() throws Exception {
        session.saveSession("existing","a@example.com","USER","A");
        json(503,"{}");model.restore();complete();
        assertEquals("existing",session.getToken());
        assertFalse(model.state.getValue().complete);
    }
    @Test public void expiredRestoreClearsSession() throws Exception {
        session.saveSession("existing","a@example.com","USER","A");
        json(401,"{}");model.restore();complete();
        assertFalse(session.isLoggedIn());
    }
    @Test public void logoutWhileLoginRunsCannotBringAccountBack() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type","application/json")
            .setBody("{\"access_token\":\"candidate\",\"token_type\":\"bearer\"}").setBodyDelay(200,TimeUnit.MILLISECONDS));
        json(200,"{\"email\":\"a@example.com\",\"role\":\"ADMIN\"}");
        model.submit(null,"a@example.com","password");session.clearSession();complete();
        assertFalse(session.isLoggedIn());assertFalse(model.state.getValue().complete);
    }
}
