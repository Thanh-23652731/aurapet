package com.aurapet.backend.application.service;

import java.util.Map;

/** Cổng gửi lệnh xuống ESP32 (hiện thực bằng MQTT trong tầng infrastructure). */
public interface DeviceCommandPublisher {

    boolean isConnected();

    /** Gửi một lệnh JSON tới topic command. Trả về false nếu chưa kết nối broker. */
    boolean publishCommand(Map<String, Object> command);
}
