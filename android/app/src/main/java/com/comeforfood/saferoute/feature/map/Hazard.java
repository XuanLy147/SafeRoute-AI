package com.comeforfood.saferoute.feature.map;

import com.comeforfood.saferoute.feature.shared.Point;
import java.util.Objects;

/** Presentation model. TV2 maps its approved reports DTO here. */
public final class Hazard {
    public enum Type {
        D00("Nứt dọc"), D10("Nứt ngang"), D20("Nứt mạng nhện"), CRACK("Nứt đường"), D40("Ổ gà"),
        REPAIR("Vết sửa chữa"), MANUAL("Nguy cơ nhập thủ công");
        public final String label;
        Type(String label) { this.label = label; }
    }
    public enum Status { PENDING_VERIFICATION, VERIFIED, REJECTED }
    public final String id, title, description;
    public final Point point;
    public final Type type;
    public final Status status;
    public Hazard(String id, String title, String description, Point point, Type type, Status status) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Missing report id");
        this.id = id; this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.point = Objects.requireNonNull(point); this.type = Objects.requireNonNull(type);
        this.status = Objects.requireNonNull(status);
    }
}
