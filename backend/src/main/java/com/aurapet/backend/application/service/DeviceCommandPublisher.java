package com.aurapet.backend.application.service;

import java.util.Map;

/** Cổng gửi lệnh xuống ESP32 (hiện thực bằng MQTT trong tầng infrastructure). */
public interface DeviceCommandPublisher {

    /** Kênh lệnh của ESP32 máy cho ăn: PREFIX/command. */
    String FEEDER_CHANNEL = "command";
    /** Kênh lệnh riêng của ESP32 máy dọn cát: PREFIX/litter/command (máy cho ăn không nhận lệnh này). */
    String LITTER_CHANNEL = "litter/command";

    boolean isConnected();

    /** Gửi một lệnh JSON tới PREFIX/{channel}. Trả về false nếu chưa kết nối broker. */
    boolean publish(String channel, Map<String, Object> command);

    /** Gửi lệnh cho máy cho ăn. */
    default boolean publishCommand(Map<String, Object> command) {
        return publish(FEEDER_CHANNEL, command);
    }
}
