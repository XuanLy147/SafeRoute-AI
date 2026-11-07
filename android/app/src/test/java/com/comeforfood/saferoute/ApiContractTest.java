package com.comeforfood.saferoute;

import com.comeforfood.saferoute.core.network.*;
import com.comeforfood.saferoute.core.model.*;
import com.comeforfood.saferoute.core.session.SessionManager;
import com.comeforfood.saferoute.feature.routing.RouteComparison;
import com.google.gson.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ApiContractTest {
    private MockWebServer server;
    private ApiClient client;
    private SessionManager session;
    @Before public void start() throws Exception {
        server=new MockWebServer();server.start();
        session=new SessionManager(RuntimeEnvironment.getApplication());session.clearSession();
        client=new ApiClient(RuntimeEnvironment.getApplication(),server.url("/").toString());
    }
    @After public void stop() throws Exception { session.clearSession();server.shutdown(); }
    private void json(int code,String body){server.enqueue(new MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(body));}
    @Test public void registerUsesFullNameAndNeverSendsRole() throws Exception {
        json(200,"{\"access_token\":\"test-token\",\"token_type\":\"bearer\",\"role\":\"USER\",\"email\":\"a@example.com\"}");
        assertTrue(client.service.register(new AuthDto.RegisterRequest("Tên Việt","a@example.com","password")).execute().isSuccessful());
        RecordedRequest request=server.takeRequest();assertEquals("/api/v1/auth/register",request.getPath());
        JsonObject body=JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();assertEquals("Tên Việt",body.get("full_name").getAsString());assertFalse(body.has("role"));
    }
    @Test public void unauthorizedClearsSessionButForbiddenDoesNot() throws Exception {
        session.saveSession("test-token","a@example.com","USER","A");
        json(403,"{\"detail\":\"Forbidden\"}");client.service.me().execute();
        assertEquals("Bearer test-token",server.takeRequest().getHeader("Authorization"));assertTrue(session.isLoggedIn());
        json(401,"{\"detail\":\"Expired\"}");client.service.me().execute();assertFalse(session.isLoggedIn());
    }
    @Test public void privateImagesNeverSendTokensToOtherOrigins() {
        assertEquals(server.url("/api/v1/images/abc").toString(),client.imageUrl("/api/v1/images/abc"));
        assertThrows(IllegalArgumentException.class,()->client.imageUrl("https://example.com/api/v1/images/abc"));
        assertThrows(IllegalArgumentException.class,()->client.imageUrl("/api/v1/images/abc?token=secret"));
        assertThrows(IllegalArgumentException.class,()->new ApiClient(RuntimeEnvironment.getApplication(),server.url("/api/v1/").toString()));
    }
    @Test public void numericReportIdsAndNullableMetadataAreNotInvented() {
        ReportDto.ReportResponse r=new Gson().fromJson("{\"id\":123,\"status\":\"PENDING_VERIFICATION\",\"image_url\":null}",ReportDto.ReportResponse.class);
        assertEquals("123",r.getId());assertNull(r.getModelVersion());assertNull(r.getImageUrl());
        assertNull(new ReportDto.ReportResponse().getId());
    }
    private JsonObject route(String status,String risk) {
        return JsonParser.parseString("{\"risk_assessment_status\":\""+status+"\",\"recommendation_basis\":\"SINGLE_ROUTE\",\"recommended_route_id\":\"route_1\",\"routes\":[{\"id\":\"route_1\",\"summary\":\"A\",\"duration_seconds\":125,\"distance_meters\":321,\"polyline\":\"_p~iF~ps|U_ulLnnqC_mqNvxq`@\",\"risk\":"+risk+"}]}").getAsJsonObject();
    }
    private String risk="{\"score\":0,\"hazards_count\":1,\"verified_hazards_count\":0,\"pending_hazards_count\":1,\"reason\":\"Zero weighted score with pending report\"}";
    @Test public void preservesSingleRouteAndZeroScoreWithPendingHazard() {
        RouteComparison c=NetworkDataSource.mapRoutes(route("AVAILABLE",risk));assertEquals(1,c.routes.size());assertEquals("route_1",c.recommendedId);
        assertEquals(0,c.routes.get(0).riskScore,0);assertEquals(1,c.routes.get(0).pendingCount);assertEquals(125,c.routes.get(0).durationSeconds);
        assertEquals(38.5,c.routes.get(0).points.get(0).latitude,1e-6);assertEquals(-120.2,c.routes.get(0).points.get(0).longitude,1e-6);
    }
    @Test public void unavailableAndInvalidNeverDisplayAZeroScore() {
        for(String status:Arrays.asList("UNAVAILABLE","INVALID"))assertNull(NetworkDataSource.mapRoutes(route(status,risk)).routes.get(0).riskScore);
        assertNull(NetworkDataSource.mapRoutes(route("PARTIAL","null")).routes.get(0).riskScore);
        assertThrows(IllegalArgumentException.class,()->NetworkDataSource.mapRoutes(route("AVAILABLE","null")));
    }
    @Test public void rejectsBrokenPolylineAndCounts() {
        assertThrows(IllegalArgumentException.class,()->NetworkDataSource.decode("_"));
        assertThrows(IllegalArgumentException.class,()->NetworkDataSource.mapRoutes(route("AVAILABLE",risk.replace("\"hazards_count\":1","\"hazards_count\":2"))));
    }
    @Test public void validatesEveryBboxValueAndConfidence() {
        assertTrue(new DetectionDto("D40",0.8f,Arrays.asList(0f,0f,1f,1f)).valid());
        assertFalse(new DetectionDto("D40",0.8f,Arrays.asList(0f,Float.NaN,1f,1f)).valid());
        assertFalse(new DetectionDto("D40",1.2f,Arrays.asList(0f,0f,1f,1f)).valid());
        assertFalse(new DetectionDto("Repair",0.8f,Arrays.asList(1f,0f,0f,1f)).valid());
    }
    @Test public void writesAreNotRetriedAndReviewUsesReason() throws Exception {
        json(409,"{\"detail\":\"Already reviewed\"}");
        assertEquals(409,client.service.review("123",new ReportDto.ReviewDecisionRequest("VERIFIED","Evidence checked","not a server field")).execute().code());
        RecordedRequest request=server.takeRequest();assertEquals("PATCH",request.getMethod());assertEquals("/api/v1/reports/123/status",request.getPath());
        JsonObject body=JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();assertTrue(body.has("reason"));assertFalse(body.has("reviewer_note"));assertEquals(1,server.getRequestCount());
    }

    @Test public void failedLoginDoesNotExpireOrSendExistingSession() throws Exception {
        session.saveSession("existing-token","a@example.com","ADMIN","A");
        json(401,"{}");
        client.service.login(new AuthDto.LoginRequest("b@example.com","wrong-password")).execute();
        assertNull(server.takeRequest().getHeader("Authorization"));
        assertEquals("existing-token",session.getToken());
    }
    @Test public void candidateProfileNeverReplacesCurrentSession() throws Exception {
        session.saveSession("existing-token","a@example.com","ADMIN","A");
        json(401,"{}");
        client.service.meWithToken("Bearer candidate-token").execute();
        assertEquals("Bearer candidate-token",server.takeRequest().getHeader("Authorization"));
        assertEquals("existing-token",session.getToken());
    }
    @Test public void lateProfileCannotResurrectLogoutOrOverwriteNewAccount() {
        session.saveSession("first-token","a@example.com","USER","A");
        long generation=session.generation();
        session.clearSession();
        assertFalse(session.saveIfUnchanged(generation,"first-token","a@example.com","ADMIN","A"));
        session.saveSession("second-token","b@example.com","USER","B");
        assertFalse(session.expireIfTokenMatches("first-token"));
        assertEquals("second-token",session.getToken());
    }
    @Test public void unknownRoleIsNeverAccepted() {
        assertThrows(IllegalArgumentException.class,()->session.saveSession("token","a@example.com","OWNER","A"));
        assertFalse(session.isLoggedIn());
    }


    @Test public void closedReadConnectionRecoversOnce() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        json(200,"[]");
        assertTrue(client.service.reports().execute().isSuccessful());
        assertEquals(2,server.getRequestCount());
    }
    @Test public void closedReadConnectionHasBoundedRetry() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        assertThrows(java.io.IOException.class,()->client.service.reports().execute());
        assertEquals(2,server.getRequestCount());
    }
    @Test public void uncertainWriteIsNeverRetried() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        assertThrows(java.io.IOException.class,()->client.service.vote("1",new ReportDto.VoteRequest("SUPPORT",null)).execute());
        assertEquals(1,server.getRequestCount());
    }
    @Test public void deniedImageKeepsVerifiedRoleButExplicitRoleDenialInvalidatesIt() throws Exception {
        session.saveSession("token","a@example.com","USER","A");
        json(403,"{}");client.service.image(server.url("/api/v1/images/test").toString()).execute();
        assertTrue(session.isVerified());
        server.enqueue(new MockResponse().setResponseCode(403).setHeader("X-SafeRoute-Role-Refresh","required").setBody("{}"));
        client.service.queue().execute();
        assertTrue(session.isLoggedIn());assertFalse(session.isVerified());
    }
    @Test public void routeExplanationDoesNotExposeInternalEnums() {
        RouteComparison c=NetworkDataSource.mapRoutes(route("UNAVAILABLE","null"));
        assertFalse(c.scoreDescription.contains("UNAVAILABLE"));
        assertFalse(c.scoreDescription.contains("SINGLE_ROUTE"));
        assertFalse(c.scoreDescription.contains(" ? "));
    }

}
