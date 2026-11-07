package com.comeforfood.saferoute.core.model;

import com.google.gson.annotations.SerializedName;

/**
 * Data Transfer Objects for Authentication (Owned by TV2)
 * Matches the backend API contract for /api/v1/auth/login and /api/v1/auth/register
 */
public class AuthDto {

    public static class LoginRequest {
        @SerializedName("email")
        private final String email;

        @SerializedName("password")
        private final String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public String getPassword() { return password; }
    }

    public static class RegisterRequest {
        @SerializedName("full_name")
        private final String fullName;

        @SerializedName("email")
        private final String email;

        @SerializedName("password")
        private final String password;

        public RegisterRequest(String fullName, String email, String password) {
            this.fullName = fullName;
            this.email = email;
            this.password = password;
        }

        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
    }

    public static class AuthResponse {
        @SerializedName("access_token")
        private String accessToken;

        @SerializedName("token_type")
        private String tokenType;

        @SerializedName("role")
        private String role; // USER, REVIEWER, ADMIN

        @SerializedName("email")
        private String email;

        @SerializedName("full_name")
        private String fullName;

        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public String getRole() { return role != null ? role : "USER"; }
        public String getEmail() { return email; }
        public String getFullName() { return fullName; }
    }
}

