package com.aurapet.backend.application.dto;

import lombok.*;

import java.time.Instant;

/** Trạng thái thời gian thực của máy cho ăn (lấy từ telemetry MQTT, giữ trong RAM). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceStateDto {
    /** Backend đang kết nối được MQTT broker. */
    private boolean brokerConnected;
    /** ESP32 gửi telemetry trong 10 giây gần nhất. */
    private boolean online;
    private Instant lastSeen;

    private Double bowlWeightGram;
    private Integer foodLevelPercent;
    private Boolean lowFood;
    private Boolean catPresent;
    private Boolean feeding;
    private Integer feedCountSinceBoot;
    private String ip;
    private Integer wifiRssi;
    private String message;

    // ---- Bình nước bơm tuần hoàn (cảm biến siêu âm trên ESP32 máy dọn cát, topic PREFIX/water) ----
    /** ESP32 máy dọn cát gửi mực nước trong 30 giây gần nhất. */
    private boolean waterOnline;
    private Instant waterLastSeen;
    /** 0-100, null khi chưa có số liệu hoặc cảm biến lỗi. */
    private Integer waterPercent;
    private Double waterCm;
    /** Nước dưới ngưỡng, cần đổ thêm. */
    private boolean lowWater;
    private boolean waterSensorOk;

    // ---- Khay vệ sinh (cùng ESP32 máy dọn cát: online = waterOnline) ----
    /** idle | cat_inside | waiting | cleaning; null khi chưa có số liệu hoặc máy mất kết nối. */
    private String litterState;
    private Instant lastLitterVisitAt;
    private Integer lastLitterVisitSec;
    private String lastLitterVisitPetName;
    private Instant lastLitterCleanAt;

    private Instant lastFeedAt;
    private Integer lastFeedGram;
    private String lastFeedSource;

    private Instant cameraLastSeen;

    private DeviceSettingsDto settings;
}
