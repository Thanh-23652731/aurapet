package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.HealthAnalyticsService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health Analytics", description = "Sức khoẻ và lịch sử của từng thú cưng")
public class HealthAnalyticsController {

    private final HealthAnalyticsService healthService;

    @GetMapping("/{petId}/summary")
    public ResponseEntity<ApiResponse<HealthSummaryDto>> summary(@PathVariable UUID petId,
                                                                 @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(healthService.getHealthSummary(petId, CurrentUser.of(user)), "OK"));
    }

    @GetMapping("/{petId}/weight")
    public ResponseEntity<ApiResponse<List<WeightRecordDto>>> weight(@PathVariable UUID petId,
                                                                     @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(healthService.getWeightHistory(petId, CurrentUser.of(user)), "OK"));
    }

    @GetMapping("/{petId}/food")
    public ResponseEntity<ApiResponse<List<FeedingLogDto>>> food(@PathVariable UUID petId,
                                                                 @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(healthService.getFoodHistory(petId, CurrentUser.of(user)), "OK"));
    }

    @GetMapping("/{petId}/food/daily")
    public ResponseEntity<ApiResponse<List<DailyFoodDto>>> foodDaily(@PathVariable UUID petId,
                                                                     @RequestParam(defaultValue = "7") int days,
                                                                     @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(healthService.getDailyFood(petId, days, CurrentUser.of(user)), "OK"));
    }

    @GetMapping("/{petId}/litter")
    public ResponseEntity<ApiResponse<List<LitterLogDto>>> litter(@PathVariable UUID petId,
                                                                  @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(healthService.getLitterHistory(petId, CurrentUser.of(user)), "OK"));
    }
}
