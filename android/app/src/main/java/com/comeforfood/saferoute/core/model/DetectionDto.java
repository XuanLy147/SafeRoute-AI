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

    public boolean valid() {
        if(!java.util.Arrays.asList("D00","D10","D20","D40","Repair").contains(classCode)||!Float.isFinite(confidence)||confidence<0||confidence>1||bbox==null||bbox.size()!=4)return false;
        for(Float v:bbox)if(v==null||!Float.isFinite(v)||v<0||v>1)return false;
        return bbox.get(0)<bbox.get(2)&&bbox.get(1)<bbox.get(3);
    }
    public String getClassCode() {
        return classCode;
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
        return HazardLabel.display(classCode);
    }

    public static class DetectionResponse implements Serializable {
        @SerializedName("model_version")
        private String modelVersion;

        @SerializedName("detections")
        private List<DetectionDto> detections;

        public String getModelVersion() {
            return modelVersion;
        }

        public List<DetectionDto> getDetections() {
            return detections;
        }
    }
}
