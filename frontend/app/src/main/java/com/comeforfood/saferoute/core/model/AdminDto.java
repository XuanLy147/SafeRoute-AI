package com.comeforfood.saferoute.core.model;
import com.google.gson.annotations.SerializedName;
public final class AdminDto {
    public static class User {
        public long id;
        public String email, role;
        @SerializedName("full_name") public String fullName;
    }
    public static class Change {
        public final String role, reason;
        @SerializedName("expected_role") public final String expectedRole;
        public Change(String role,String expectedRole,String reason){this.role=role;this.expectedRole=expectedRole;this.reason=reason;}
    }
    public static class Changed { public User user; @SerializedName("audit_id") public long auditId; }
    public static class Audit {
        public long id;
        @SerializedName("actor_id") public long actorId;
        @SerializedName("user_id") public long userId;
        @SerializedName("from_role") public String fromRole;
        @SerializedName("to_role") public String toRole;
        @SerializedName("created_at") public String createdAt;
        public String reason;
    }
}
