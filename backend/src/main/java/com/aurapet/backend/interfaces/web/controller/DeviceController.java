package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.DashboardService;
import com.aurapet.backend.application.service.DeviceService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API duy nhất để điều khiển máy cho ăn thật (Web và App dùng chung).
 * Mọi lệnh đi qua MQTT tới ESP32; nếu máy offline sẽ trả HTTP 409 kèm thông báo rõ ràng.
 */
@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
@Tag(name = "Device", description = "Điều khiển máy cho ăn ESP32 qua MQTT")
public class DeviceController {

    private final DeviceService deviceService;
    private final DashboardService dashboardService;

    @GetMapping("/state")
    @Operation(summary = "Trạng thái thời gian thực (gọi 2 giây/lần)")
    public ResponseEntity<ApiResponse<DeviceStateDto>> state() {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.getState(), "OK"));
    }

    @PostMapping("/feed")
    @Operation(summary = "Cho ăn ngay")
    public ResponseEntity<ApiResponse<DeviceStateDto>> feed(@Valid @RequestBody(required = false) FeedRequest request) {
        FeedRequest r = request != null ? request : new FeedRequest();
        return ResponseEntity.ok(ApiResponse.ok(deviceService.feed(r.getGram(), r.getSource()), "Đã gửi lệnh cho ăn"));
    }

    @PostMapping("/stop")
    @Operation(summary = "Dừng động cơ khẩn cấp")
    public ResponseEntity<ApiResponse<DeviceStateDto>> stop() {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.stop(), "Đã gửi lệnh dừng"));
    }

    @PostMapping("/tare")
    @Operation(summary = "Trừ bì cân về 0g (khay phải trống)")
    public ResponseEntity<ApiResponse<DeviceStateDto>> tare() {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.tare(), "Đã gửi lệnh trừ bì"));
    }

    @PostMapping("/pump")
    @Operation(summary = "Bật/tắt bơm nước (nếu đã lắp)")
    public ResponseEntity<ApiResponse<DeviceStateDto>> pump(@Valid @RequestBody PumpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.pump(request.getOn(), request.getSeconds()),
                Boolean.TRUE.equals(request.getOn()) ? "Đã bật bơm" : "Đã tắt bơm"));
    }

    @PutMapping("/settings")
    @Operation(summary = "Cập nhật cài đặt (khẩu phần, tự động, lịch hẹn giờ, mèo của máy, bơm)")
    public ResponseEntity<ApiResponse<DeviceStateDto>> settings(@Valid @RequestBody UpdateDeviceSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.updateSettings(request), "Đã lưu cài đặt"));
    }

    @GetMapping("/feedings")
    @Operation(summary = "Lịch sử cho ăn gần đây")
    public ResponseEntity<ApiResponse<List<FeedingLogDto>>> feedings(@RequestParam(defaultValue = "50") int limit,
                                                                     @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.recentFeedings(CurrentUser.of(user), limit), "OK"));
    }
}
