package com.aurapet.backend.application.service;

/**
 * Một gói tin nhận từ thiết bị.
 * channel: "telemetry" | "log" | "status" | "vision" (phần đuôi topic sau prefix).
 */
public record DeviceMessageEvent(String channel, String payload) {
}
