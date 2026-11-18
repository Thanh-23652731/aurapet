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
    /** Đang nhả: số gam lần này cần nhả và số gam cân đã tăng (null khi không nhả / firmware cũ). */
    private Integer feedGoalGram;
    private Double feedAddedGram;
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
    /** Nguồn số đo nước: "feeder" (cảm biến trên máy cho ăn) | "litter" (máy dọn cát) | null. */
    private String waterSource;

    // ---- Khay vệ sinh (cùng ESP32 máy dọn cát: online = waterOnline) ----
    /** idle | cat_inside | waiting | cleaning; null khi chưa có số liệu hoặc máy mất kết nối. */
    private String litterState;
    /** ESP32 máy dọn cát online (khác waterOnline khi số đo nước lấy từ máy cho ăn). */
    private boolean litterOnline;
    private Instant litterLastSeen;
    private Instant lastLitterVisitAt;
    private Integer lastLitterVisitSec;
    private String lastLitterVisitPetName;
    private Instant lastLitterCleanAt;

    private Instant lastFeedAt;
    private Integer lastFeedGram;
    /** Số gam đã yêu cầu ở lần cho ăn cuối (null nếu không biết, vd. sau khi khởi động lại máy chủ). */
    private Integer lastFeedTargetGram;
    private String lastFeedSource;

    private Instant cameraLastSeen;
    private String lastDetectedPetName;
    private Instant lastDetectedPetAt;

    private DeviceSettingsDto settings;
}
