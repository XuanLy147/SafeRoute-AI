package com.comeforfood.saferoute.core.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

/**
 * AI Detection Data Transfer Object (Owned by TV2 & TV4)
 * Matches YOLOv12s prediction contract for /api/v1/ai/detect
 */
public class DetectionDto implements Serializable {

    @SerializedName("class_code")
    private String classCode; // D00, D10, D20, D40, Repair

    @SerializedName("confidence")
    private float confidence; // 0.0 to 1.0

    /**
     * Normalized Bounding Box: [x_min, y_min, x_max, y_max] where each value is in range [0.0, 1.0]
     */
    @SerializedName("bbox")
    private List<Float> bbox;

    public DetectionDto() {}

    public DetectionDto(String classCode, float confidence, List<Float> bbox) {
        this.classCode = classCode;
        this.confidence = confidence;
        this.bbox = bbox;
    }

    public String getClassCode() {
        return classCode != null ? classCode : "D40";
    }

    public float getConfidence() {
        return confidence;
    }

    public List<Float> getBbox() {
        return bbox;
    }

    /**
     * Vietnamese display name corresponding to RDD2022 / YOLOv12s classes
     */
    public String getDisplayName() {
        if (classCode == null) return "Hư hỏng mặt đường";
        switch (classCode.toUpperCase()) {
            case "D00": return "D00 · Nứt dọc";
            case "D10": return "D10 · Nứt ngang";
            case "D20": return "D20 · Nứt mạng nhện";
            case "D40": return "D40 · Ổ gà";
            case "REPAIR": return "Vết sửa chữa";
            default: return classCode;
        }
    }

    public static class DetectionResponse implements Serializable {
        @SerializedName("model_version")
        private String modelVersion;

        @SerializedName("detections")
        private List<DetectionDto> detections;

        public String getModelVersion() {
            return modelVersion != null ? modelVersion : "v1.1";
        }

        public List<DetectionDto> getDetections() {
            return detections;
        }
    }
}

