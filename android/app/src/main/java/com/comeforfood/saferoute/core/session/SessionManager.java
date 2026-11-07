package com.comeforfood.saferoute.core.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages Auth Tokens and User Roles on device (Owned by TV2)
 */
public class SessionManager {

    private static final String PREF_NAME = "saferoute_session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_NAME = "user_fullname";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String email, String role, String fullName) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE, role)
                .putString(KEY_NAME, fullName)
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty() && !getToken().startsWith("mock_") && !"token_registered".equals(getToken());
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "USER");
    }

    public String getFullName() {
        return prefs.getString(KEY_NAME, "");
    }

    public boolean isReviewerOrAdmin() {
        String role = getRole();
        return isLoggedIn() && ("REVIEWER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
