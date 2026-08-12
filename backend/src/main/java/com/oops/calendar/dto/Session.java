package com.oops.calendar.dto;

/**
 * 财报发布时段:
 * BMO = before market open(盘前)
 * AMC = after market close(盘后)
 * DNH = during normal hours(盘中)
 * UNKNOWN = 待定
 */
public enum Session {
    BMO("盘前"),
    AMC("盘后"),
    DNH("盘中"),
    UNKNOWN("待定");

    private final String label;

    Session(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 将 FMP time 字段映射为 Session。
     */
    public static Session fromFmpTime(String time) {
        if (time == null) {
            return UNKNOWN;
        }
        switch (time.trim().toLowerCase()) {
            case "bmo":
            case "before-market":
                return BMO;
            case "amc":
            case "after-market":
                return AMC;
            case "dnh":
            case "during":
                return DNH;
            default:
                return UNKNOWN;
        }
    }
}
