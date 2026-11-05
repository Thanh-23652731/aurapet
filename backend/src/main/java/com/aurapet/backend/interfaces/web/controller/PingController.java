package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint công khai để Web/App kiểm tra địa chỉ máy chủ có đúng là AuraPet không. */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> ping() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("app", "aurapet", "version", "2.0"), "pong"));
    }
}
