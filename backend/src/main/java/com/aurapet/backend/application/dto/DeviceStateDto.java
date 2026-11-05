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

    private Boolean pumpSupported;
    private Boolean pumpOn;

    private Instant lastFeedAt;
    private Integer lastFeedGram;
    private String lastFeedSource;

    private Instant cameraLastSeen;

    private DeviceSettingsDto settings;
}
