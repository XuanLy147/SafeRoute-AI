package com.comeforfood.saferoute.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public final class ModerationDto {
    private ModerationDto() {}
    public static final class History {
        public long id;
        @SerializedName("report_id") public long reportId;
        @SerializedName("actor_id") public long actorId;
        @SerializedName("to_status") public String status;
        public String reason;
        @SerializedName("created_at") public String createdAt;
    }
    public static final class Pair {
        @SerializedName("report_id") public long reportId;
        @SerializedName("candidate_id") public long candidateId;
        public String category;
        @SerializedName("distance_m") public double distance;
    }
    public static final class Overview {
        @SerializedName("report_counts") public Map<String,Integer> reports;
        @SerializedName("user_counts") public Map<String,Integer> users;
        @SerializedName("review_count") public int reviews;
        @SerializedName("model_version") public String model;
        @SerializedName("ai_ready") public boolean aiReady;
        @SerializedName("risk_ready") public boolean riskReady;
        @SerializedName("risk_policy_revision") public String policy;
        @SerializedName("max_image_size_mb") public int maxImageMb;
        @SerializedName("duplicate_radius_m") public int duplicateRadius;
        @SerializedName("duplicate_window_days") public int duplicateDays;
    }
    public static final class MyVote { public String vote; }
}
