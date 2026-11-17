package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint công khai để Web/App kiểm tra địa chỉ máy chủ có đúng là AuraPet không.
 * Kèm cờ demoData để giao diện biết có hiện nút "Dùng thử" hay không.
 */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    @Value("${aurapet.demo-data:false}")
    private boolean demoData;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> ping() {
        Map<String, Object> info = Map.of(
                "app", "aurapet",
                "version", "2.0",
                "demoData", demoData);
        return ResponseEntity.ok(ApiResponse.ok(info, "pong"));
    }
}
