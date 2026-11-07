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
    private static volatile String memoryToken;
    private static volatile String validatedRole="USER";

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if(prefs.contains(KEY_TOKEN)) prefs.edit().remove(KEY_TOKEN).remove(KEY_ROLE).apply();
    }

    public void saveSession(String token, String email, String role, String fullName) {
        memoryToken=token;validatedRole=role;
        String encrypted=null;
        try { encrypted=TokenVault.encrypt(token); } catch(Exception ignored) { /* No plaintext persistence. */ }
        prefs.edit()
                .putString("encrypted_token", encrypted)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE, role)
                .putString(KEY_NAME, fullName)
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && !getToken().isEmpty() && !getToken().startsWith("mock_") && !"token_registered".equals(getToken());
    }

    public String getToken() {
        if(memoryToken!=null)return memoryToken;
        String encrypted=prefs.getString("encrypted_token",null);
        if(encrypted==null)return null;
        try { memoryToken=TokenVault.decrypt(encrypted);return memoryToken; }
        catch(Exception ignored) { clearSession();return null; }
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return validatedRole;
    }

    public String getFullName() {
        return prefs.getString(KEY_NAME, "");
    }

    public boolean isReviewerOrAdmin() {
        String role = getRole();
        return isLoggedIn() && ("REVIEWER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    public void clearSession() {
        memoryToken=null;validatedRole="USER";
        prefs.edit().clear().apply();
    }
}
