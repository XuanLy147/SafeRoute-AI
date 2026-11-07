package com.comeforfood.saferoute.core.model;

/** Presentation-only labels. API codes and stored values stay unchanged. */
public final class HazardLabel {
    private HazardLabel() {}
    public static String display(String code) {
        if (code == null || code.trim().isEmpty()) return "Hư hỏng mặt đường";
        switch (code.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "D00": return "Nứt dọc";
            case "D10": return "Nứt ngang";
            case "D20": return "Nứt mạng nhện";
            case "D40": return "Ổ gà";
            case "REPAIR": return "Vết sửa chữa";
            default: return code;
        }
    }
}
