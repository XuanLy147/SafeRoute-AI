package com.comeforfood.saferoute.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/** Encrypted credentials; elevated permissions only after a server profile response. */
public class SessionManager {
    private static final Object LOCK = new Object();
    private static final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    private static String memoryToken;
    private static String validatedRole;
    private static long generation;
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("saferoute_session", Context.MODE_PRIVATE);
        if (prefs.contains("access_token")) prefs.edit().remove("access_token").remove("user_role").apply();
    }
    public LiveData<Long> changes() { return changes; }
    public long generation() { synchronized (LOCK) { return generation; } }
    public static boolean knownRole(String role) {
        return "USER".equals(role) || "REVIEWER".equals(role) || "ADMIN".equals(role);
    }
    public void saveSession(String token, String email, String role, String fullName) {
        synchronized (LOCK) {
            if (token == null || token.isEmpty() || !knownRole(role)) throw new IllegalArgumentException("Invalid session");
            memoryToken = token;
            validatedRole = role;
            String encrypted = null;
            try { encrypted = TokenVault.encrypt(token); } catch (Exception ignored) { /* Memory only; never plaintext. */ }
            prefs.edit().putString("encrypted_token", encrypted).putString("user_email", email)
                .putString("user_fullname", fullName == null ? "" : fullName).remove("user_role").apply();
            changes.postValue(++generation);
        }
    }
    public boolean saveIfUnchanged(long expected, String token, String email, String role, String name) {
        synchronized (LOCK) {
            if (expected != generation) return false;
            saveSession(token, email, role, name);
            return true;
        }
    }
    public String getToken() {
        synchronized (LOCK) {
            if (memoryToken != null) return memoryToken;
            String encrypted = prefs.getString("encrypted_token", null);
            if (encrypted == null) return null;
            try { memoryToken = TokenVault.decrypt(encrypted); return memoryToken; }
            catch (Exception ignored) { clearSession(); return null; }
        }
    }
    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty() && !token.startsWith("mock_") && !"token_registered".equals(token);
    }
    public boolean isVerified() { synchronized (LOCK) { return isLoggedIn() && validatedRole != null; } }
    public String getRole() { synchronized (LOCK) { return validatedRole == null ? "USER" : validatedRole; } }
    public String getEmail() { return prefs.getString("user_email", ""); }
    public String getFullName() { return prefs.getString("user_fullname", ""); }
    public boolean isReviewerOrAdmin() {
        return isVerified() && ("REVIEWER".equals(getRole()) || "ADMIN".equals(getRole()));
    }
    public boolean expireIfTokenMatches(String token) {
        synchronized (LOCK) {
            if (token == null || !token.equals(getToken())) return false;
            clearSession(); return true;
        }
    }
    public void invalidatePermissions(String token) {
        synchronized (LOCK) {
            if(token!=null && token.equals(getToken()) && validatedRole!=null) {
                validatedRole=null;changes.postValue(++generation);
            }
        }
    }
    public void clearSession() {
        synchronized (LOCK) {
            memoryToken = null; validatedRole = null;
            prefs.edit().clear().apply();
            changes.postValue(++generation);
        }
    }
}
