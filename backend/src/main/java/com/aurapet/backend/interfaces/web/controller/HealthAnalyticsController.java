package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.HealthAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health Analytics", description = "Pet health telemetry, AI analysis, and historical logs")
public class HealthAnalyticsController {

    private final HealthAnalyticsService healthAnalyticsService;

    @GetMapping("/{petId}/summary")
    @Operation(summary = "Get AI Health Analytics Summary for a pet")
    public ResponseEntity<ApiResponse<HealthSummaryDto>> getHealthSummary(@PathVariable UUID petId) {
        HealthSummaryDto summary = healthAnalyticsService.getHealthSummary(petId);
        return ResponseEntity.ok(ApiResponse.ok(summary, "AI health summary generated successfully"));
    }

    @GetMapping("/{petId}/weight")
    @Operation(summary = "Retrieve weight historical telemetry")
    public ResponseEntity<ApiResponse<List<WeightRecordDto>>> getWeightHistory(@PathVariable UUID petId) {
        List<WeightRecordDto> history = healthAnalyticsService.getWeightHistory(petId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Weight history retrieved successfully"));
    }

    @GetMapping("/{petId}/food")
    @Operation(summary = "Retrieve food intake history")
    public ResponseEntity<ApiResponse<List<FeedingLogDto>>> getFoodHistory(@PathVariable UUID petId) {
        List<FeedingLogDto> history = healthAnalyticsService.getFoodHistory(petId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Food intake history retrieved successfully"));
    }

    @GetMapping("/{petId}/litter")
    @Operation(summary = "Retrieve litter box usage logs")
    public ResponseEntity<ApiResponse<List<LitterLogDto>>> getLitterHistory(@PathVariable UUID petId) {
        List<LitterLogDto> history = healthAnalyticsService.getLitterHistory(petId);
        return ResponseEntity.ok(ApiResponse.ok(history, "Litter history retrieved successfully"));
    }
}
