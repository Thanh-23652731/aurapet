package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.ApiResponse;
import com.aurapet.backend.application.dto.DashboardStatsDto;
import com.aurapet.backend.application.service.DashboardService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Số liệu tổng hợp")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> stats(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboardStats(CurrentUser.of(user)), "OK"));
    }
}
