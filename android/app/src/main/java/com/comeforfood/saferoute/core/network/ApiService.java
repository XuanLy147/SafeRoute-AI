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
    @POST("api/v1/auth/login") Call<AuthDto.AuthResponse> login(@Body AuthDto.LoginRequest body);
    @POST("api/v1/auth/register") Call<AuthDto.AuthResponse> register(@Body AuthDto.RegisterRequest body);
    @GET("api/v1/auth/me") Call<AuthDto.AuthResponse> me();
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
