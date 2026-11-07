package com.comeforfood.saferoute.core.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Report Data Transfer Object (Owned by TV2 & TV3)
 * Matches /api/v1/reports and /api/v1/review contracts
 */
public class ReportDto implements Serializable {

    public static final String STATUS_PENDING = "PENDING_VERIFICATION";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String VOTE_SUPPORT = "SUPPORT";
    public static final String VOTE_OPPOSE = "OPPOSE";
    public static final String VOTE_UNSURE = "UNSURE";

    public static class CreateRequest implements Serializable {
        @SerializedName("image_id")
        private String imageId;

        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;

        @SerializedName("category_user")
        private String categoryUser; // D00, D10, D20, D40, Repair, Ngập nước, Vật cản

        @SerializedName("severity")
        private String severity; // LOW, MEDIUM, HIGH

        @SerializedName("description")
        private String description;

        @SerializedName("ai_detections")
        private List<DetectionDto> aiDetections;

        public CreateRequest(String imageId, double latitude, double longitude, String categoryUser,
                             String severity, String description, List<DetectionDto> aiDetections) {
            this.imageId = imageId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.categoryUser = categoryUser;
            this.severity = severity;
            this.description = description;
            this.aiDetections = aiDetections;
        }

        public String getImageId() { return imageId; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getCategoryUser() { return categoryUser; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
        public List<DetectionDto> getAiDetections() { return aiDetections; }
    }

    public static class VoteRequest implements Serializable {
        @SerializedName("vote_type")
        private String voteType; // SUPPORT, OPPOSE, UNSURE

        @SerializedName("note")
        private String note;

        public VoteRequest(String voteType, String note) {
            this.voteType = voteType;
            this.note = note;
        }

        public String getVoteType() { return voteType; }
        public String getNote() { return note; }
    }

    public static class ReviewDecisionRequest implements Serializable {
        @SerializedName("status")
        private String status; // VERIFIED, REJECTED

        @SerializedName("reason")
        private String reason; // Mandatory when REJECTED

        @SerializedName("reviewer_note")
        private String reviewerNote;

        public ReviewDecisionRequest(String status, String reason, String reviewerNote) {
            this.status = status;
            this.reason = reason;
            this.reviewerNote = reviewerNote;
        }

        public String getStatus() { return status; }
        public String getReason() { return reason; }
        public String getReviewerNote() { return reviewerNote; }
    }

    public static class AuditLogEntry implements Serializable {
        @SerializedName("reviewer_id")
        private String reviewerId;

        @SerializedName("old_status")
        private String oldStatus;

        @SerializedName("new_status")
        private String newStatus;

        @SerializedName("reason")
        private String reason;

        @SerializedName("timestamp")
        private String timestamp;

        public AuditLogEntry(String reviewerId, String oldStatus, String newStatus, String reason, String timestamp) {
            this.reviewerId = reviewerId;
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public String getReviewerId() { return reviewerId; }
        public String getOldStatus() { return oldStatus; }
        public String getNewStatus() { return newStatus; }
        public String getReason() { return reason; }
        public String getTimestamp() { return timestamp; }
    }

    public static class ReportResponse implements Serializable {
        @SerializedName("id")
        private String id;

        @SerializedName("user_id")
        private String userId;

        @SerializedName("reporter_name")
        private String reporterName;

        @SerializedName("category_user")
        private String categoryUser;

        @SerializedName("status")
        private String status; // PENDING_VERIFICATION, VERIFIED, REJECTED

        @SerializedName("severity")
        private String severity;

        @SerializedName("latitude")
        private double latitude;

        @SerializedName("longitude")
        private double longitude;

        @SerializedName("address")
        private String address;

        @SerializedName("description")
        private String description;

        @SerializedName("image_url")
        private String imageUrl;

        @SerializedName("created_at")
        private String createdAt;

        @SerializedName("ai_detections")
        private List<DetectionDto> aiDetections;

        @SerializedName("support_votes")
        private int supportVotes;

        @SerializedName("oppose_votes")
        private int opposeVotes;

        @SerializedName("unsure_votes")
        private int unsureVotes;

        @SerializedName("model_version")
        private String modelVersion;

        @SerializedName("audit_logs")
        private List<AuditLogEntry> auditLogs;

        public ReportResponse() {}

        public ReportResponse(String id, String categoryUser, String status, String address,
                              double latitude, double longitude, String createdAt,
                              String description, int supportVotes, int opposeVotes) {
            this.id = id;
            this.categoryUser = categoryUser;
            this.status = status;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.createdAt = createdAt;
            this.description = description;
            this.supportVotes = supportVotes;
            this.opposeVotes = opposeVotes;
            this.modelVersion = "weights-v0* (YOLOv8s)";
            this.aiDetections = new ArrayList<>();
            this.auditLogs = new ArrayList<>();
        }

        public String getId() { return id != null ? id : "SR-0001"; }
        public String getUserId() { return userId != null ? userId : "user_unknown"; }
        public String getReporterName() { return reporterName != null ? reporterName : "Người dùng cộng đồng"; }
        public String getCategoryUser() { return categoryUser != null ? categoryUser : "D40"; }
        public String getStatus() { return status != null ? status : STATUS_PENDING; }
        public void setStatus(String status) { this.status = status; }
        public String getSeverity() { return severity != null ? severity : "MEDIUM"; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getAddress() { return address != null ? address : "Quận 5, TP. Hồ Chí Minh"; }
        public String getDescription() { return description != null ? description : ""; }
        public String getImageUrl() { return imageUrl; }
        public String getCreatedAt() { return createdAt != null ? createdAt : "10:15 Hôm nay"; }
        public List<DetectionDto> getAiDetections() { return aiDetections; }
        public void setAiDetections(List<DetectionDto> aiDetections) { this.aiDetections = aiDetections; }
        public int getSupportVotes() { return supportVotes; }
        public void setSupportVotes(int votes) { this.supportVotes = votes; }
        public int getOpposeVotes() { return opposeVotes; }
        public void setOpposeVotes(int votes) { this.opposeVotes = votes; }
        public int getUnsureVotes() { return unsureVotes; }
        public void setUnsureVotes(int votes) { this.unsureVotes = votes; }
        public String getModelVersion() { return modelVersion != null ? modelVersion : "weights-v0*"; }
        public List<AuditLogEntry> getAuditLogs() { return auditLogs != null ? auditLogs : new ArrayList<>(); }

        public String getStatusDisplay() {
            if (STATUS_VERIFIED.equalsIgnoreCase(status)) return "Đã xác minh";
            if (STATUS_REJECTED.equalsIgnoreCase(status)) return "Từ chối";
            return "Chờ xác minh";
        }
    }
}
