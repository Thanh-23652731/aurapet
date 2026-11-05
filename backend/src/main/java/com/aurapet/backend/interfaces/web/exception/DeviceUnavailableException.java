package com.aurapet.backend.interfaces.web.exception;

/** Máy cho ăn offline hoặc server chưa kết nối MQTT broker -> HTTP 409. */
public class DeviceUnavailableException extends RuntimeException {
    public DeviceUnavailableException(String message) {
        super(message);
    }
}
