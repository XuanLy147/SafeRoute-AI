package com.comeforfood.saferoute.core.network;

import com.comeforfood.saferoute.core.model.*;
import com.google.gson.JsonObject;
import java.util.List;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

/** Paths and payloads from tv345-contract-2026-11-07. */
public interface ApiService {
    @GET("api/v1/review/history") Call<List<ModerationDto.History>> reviewHistory(@Query("offset") int offset,@Query("limit") int limit);
    @GET("api/v1/review/duplicates") Call<List<ModerationDto.Pair>> duplicates(@Query("q") String query,@Query("offset") int offset,@Query("limit") int limit);
    @GET("api/v1/admin/reports") Call<List<ReportDto.ReportResponse>> adminReports(@Query("q") String query,@Query("status") String status,@Query("offset") int offset,@Query("limit") int limit);
    @GET("api/v1/admin/overview") Call<ModerationDto.Overview> overview();
    @GET("api/v1/reports/{id}/votes/mine") Call<ModerationDto.MyVote> myVote(@Path("id") String id);

    @GET("api/v1/admin/users") Call<List<AdminDto.User>> users(@Query("q") String query,@Query("offset") int offset,@Query("limit") int limit);
    @PATCH("api/v1/admin/users/{id}/role") Call<AdminDto.Changed> changeRole(@Path("id") long id,@Body AdminDto.Change change);
    @GET("api/v1/admin/role-audit") Call<List<AdminDto.Audit>> roleAudit(@Query("offset") int offset,@Query("limit") int limit);
    @POST("api/v1/auth/login") Call<AuthDto.AuthResponse> login(@Body AuthDto.LoginRequest body);
    @POST("api/v1/auth/register") Call<AuthDto.AuthResponse> register(@Body AuthDto.RegisterRequest body);
    @GET("api/v1/auth/me") Call<AuthDto.AuthResponse> me();
    @GET("api/v1/auth/me") Call<AuthDto.AuthResponse> meWithToken(@Header("Authorization") String bearer);
    @GET("api/v1/reports") Call<List<ReportDto.ReportResponse>> reports();
    @GET("api/v1/reports/mine") Call<List<ReportDto.ReportResponse>> mine();
    @GET("api/v1/reports/{id}") Call<ReportDto.ReportResponse> detail(@Path("id") String id);
    @POST("api/v1/reports") Call<ReportDto.ReportResponse> create(@Body ReportDto.CreateRequest body);
    @POST("api/v1/reports/{id}/votes") Call<JsonObject> vote(@Path("id") String id, @Body ReportDto.VoteRequest body);
    @GET("api/v1/review/queue") Call<List<ReportDto.ReportResponse>> queue();
    @PATCH("api/v1/reports/{id}/status") Call<JsonObject> review(@Path("id") String id, @Body ReportDto.ReviewDecisionRequest body);
    @Multipart @POST("api/v1/images") Call<JsonObject> upload(@Part MultipartBody.Part file);
    @Multipart @POST("api/v1/ai/detect") Call<DetectionDto.DetectionResponse> detect(@Part MultipartBody.Part file);
    @GET Call<ResponseBody> image(@Url String url);
    @POST("api/v1/routes/compare") Call<JsonObject> compare(@Body JsonObject body);
}
