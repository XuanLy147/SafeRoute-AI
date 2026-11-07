package com.comeforfood.saferoute.core.network;

import android.content.Context;
import com.comeforfood.saferoute.BuildConfig;
import com.comeforfood.saferoute.core.session.SessionManager;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** One client. No logging, implicit write retries, cross-origin tokens or redirects. */
public final class ApiClient {
    public final ApiService service;
    public final HttpUrl origin;
    public ApiClient(Context context) { this(context, BuildConfig.API_BASE_URL); }
    public ApiClient(Context context, String baseUrl) {
        origin = HttpUrl.get(baseUrl);
        if (!origin.encodedPath().equals("/") || origin.query()!=null || !origin.username().isEmpty()
                || !origin.password().isEmpty() || (!BuildConfig.DEBUG && !origin.isHttps()))
            throw new IllegalArgumentException("API_BASE_URL must be an origin; release requires HTTPS");
        SessionManager session = new SessionManager(context);
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .addInterceptor(chain -> {
                Request request = chain.request();
                if (!sameOrigin(request.url())) throw new java.io.IOException("Cross-origin request rejected");
                String token = session.getToken();
                if (token != null) request = request.newBuilder().header("Authorization", "Bearer " + token).build();
                Response response = chain.proceed(request);
                if (response.code()==401 && token!=null && token.equals(session.getToken())) session.clearSession();
                return response;
            }).build();
        service = new Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
    public boolean sameOrigin(HttpUrl url) {
        return origin.scheme().equals(url.scheme()) && origin.host().equals(url.host()) && origin.port()==url.port();
    }
    public String imageUrl(String path) {
        HttpUrl url = origin.resolve(path);
        if (url==null || !sameOrigin(url) || !url.encodedPath().startsWith("/api/v1/images/") || url.query()!=null)
            throw new IllegalArgumentException("Invalid private image URL");
        return url.toString();
    }
}
