package com.comeforfood.saferoute.feature.shared;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DisplayText {
    private DisplayText() {}
    public static String time(String value) {
        if(value==null)return "Chưa có thời gian";
        try{return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("vi-VN")));}
        catch(java.time.format.DateTimeParseException ignored){return value;}
    }
    public static String status(String value) {
        if("VERIFIED".equals(value))return "Đã xác minh";
        if("REJECTED".equals(value))return "Từ chối";
        if("PENDING_VERIFICATION".equals(value))return "Chờ xác minh";
        return value==null?"Chưa có trạng thái":value;
    }
}
