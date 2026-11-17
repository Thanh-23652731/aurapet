package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.Alert;
import com.aurapet.backend.domain.model.AlertLevel;
import com.aurapet.backend.domain.model.AlertType;
import com.aurapet.backend.domain.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Ghi cảnh báo từ thiết bị. Lỗi CSDL không được làm hỏng luồng xử lý gói tin MQTT. */
@Slf4j
@Component
public class DeviceAlerts {

    private final AlertRepository alertRepository;

    public DeviceAlerts(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /** Cảnh báo chung của thiết bị: mọi người dùng đều thấy. */
    public void add(AlertType type, AlertLevel level, String message) {
        add(type, level, message, null);
    }

    /** Cảnh báo về một bé cụ thể: chỉ chủ của bé và admin thấy. */
    public void add(AlertType type, AlertLevel level, String message, UUID petId) {
        try {
            alertRepository.save(Alert.builder()
                    .type(type)
                    .level(level)
                    .message(message)
                    .createdAt(Instant.now())
                    .petId(petId)
                    .build());
        } catch (Exception e) {
            log.warn("[Device] Không lưu được cảnh báo: {}", e.getMessage());
        }
    }
}
