package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.SystemDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "IoT Devices", description = "IoT actuator commands and hardware telemetry")
public class SystemDeviceController {

    private final SystemDeviceService systemDeviceService;

    @GetMapping("/status")
    @Operation(summary = "Get real-time system status and hardware telemetry")
    public ResponseEntity<ApiResponse<SystemStatusDto>> getSystemStatus() {
        SystemStatusDto status = systemDeviceService.getSystemStatus();
        return ResponseEntity.ok(ApiResponse.ok(status, "System telemetry retrieved successfully"));
    }

    @GetMapping("/feeder/history")
    @Operation(summary = "Get full feeding history across pets")
    public ResponseEntity<ApiResponse<List<FeedingLogDto>>> getFeedingHistory() {
        List<FeedingLogDto> history = systemDeviceService.getFeedingHistory();
        return ResponseEntity.ok(ApiResponse.ok(history, "Feeding history retrieved successfully"));
    }

    @PostMapping("/feeder")
    @Operation(summary = "Command smart feeder to dispense food")
    public ResponseEntity<ApiResponse<SystemStatusDto>> controlFeeder(@Valid @RequestBody FeederCommandRequest request) {
        SystemStatusDto status = systemDeviceService.controlFeeder(request);
        return ResponseEntity.ok(ApiResponse.ok(status, "Feeder dispense command executed successfully"));
    }

    @PostMapping("/water")
    @Operation(summary = "Command circulating water fountain pump")
    public ResponseEntity<ApiResponse<SystemStatusDto>> controlWater(@Valid @RequestBody WaterCommandRequest request) {
        SystemStatusDto status = systemDeviceService.controlWater(request);
        return ResponseEntity.ok(ApiResponse.ok(status, "Water fountain command executed successfully"));
    }

    @PostMapping("/litter")
    @Operation(summary = "Trigger auto litter box cleaning cycle")
    public ResponseEntity<ApiResponse<SystemStatusDto>> controlLitter(@Valid @RequestBody LitterCommandRequest request) {
        SystemStatusDto status = systemDeviceService.controlLitter(request);
        return ResponseEntity.ok(ApiResponse.ok(status, "Litter cleaner command executed successfully"));
    }

    @GetMapping("/food")
    @Operation(summary = "Get Food subsystem status (auto, btnLed, ledGreen, ledRed, machines, percel, calories)")
    public ResponseEntity<ApiResponse<SystemFoodDto>> getFoodStatus() {
        SystemFoodDto food = systemDeviceService.getFoodStatus();
        return ResponseEntity.ok(ApiResponse.ok(food, "Food subsystem status retrieved successfully"));
    }

    @PostMapping("/food")
    @Operation(summary = "Update Food subsystem control (auto, btnLed, ledGreen, ledRed, machines, percel, calories)")
    public ResponseEntity<ApiResponse<SystemFoodDto>> updateFoodStatus(@RequestBody FoodControlCommand command) {
        SystemFoodDto food = systemDeviceService.updateFoodStatus(command);
        return ResponseEntity.ok(ApiResponse.ok(food, "Food subsystem updated successfully"));
    }

    @GetMapping("/water")
    @Operation(summary = "Get Water subsystem status (auto, btnLed, ledGreen, ledRed, machines, percel)")
    public ResponseEntity<ApiResponse<SystemWaterDto>> getWaterStatus() {
        SystemWaterDto water = systemDeviceService.getWaterStatus();
        return ResponseEntity.ok(ApiResponse.ok(water, "Water subsystem status retrieved successfully"));
    }

    @PutMapping("/water")
    @Operation(summary = "Update Water subsystem control (auto, btnLed, ledGreen, ledRed, machines, percel)")
    public ResponseEntity<ApiResponse<SystemWaterDto>> updateWaterStatus(@RequestBody WaterControlCommand command) {
        SystemWaterDto water = systemDeviceService.updateWaterStatus(command);
        return ResponseEntity.ok(ApiResponse.ok(water, "Water subsystem updated successfully"));
    }

    @PostMapping("/food/flow-cycle")
    @Operation(summary = "Execute Food decision flowchart (Figure 6) with scale check and cat detection")
    public ResponseEntity<ApiResponse<SystemFoodDto>> processFoodFlowCycle(
            @RequestParam(required = false, defaultValue = "false") Boolean catDetected,
            @RequestParam(required = false, defaultValue = "5") Integer scaleWeightGram,
            @RequestParam(required = false, defaultValue = "false") Boolean manualButton) {
        SystemFoodDto food = systemDeviceService.processFoodCycle(catDetected, scaleWeightGram, manualButton);
        return ResponseEntity.ok(ApiResponse.ok(food, "Food flow cycle evaluated successfully"));
    }

    @PostMapping("/water/flow-cycle")
    @Operation(summary = "Execute Water decision flowchart (Figure 4) with pump and cat detection")
    public ResponseEntity<ApiResponse<SystemWaterDto>> processWaterFlowCycle(
            @RequestParam(required = false, defaultValue = "false") Boolean catDetected,
            @RequestParam(required = false, defaultValue = "false") Boolean manualButton) {
        SystemWaterDto water = systemDeviceService.processWaterCycle(catDetected, manualButton);
        return ResponseEntity.ok(ApiResponse.ok(water, "Water flow cycle evaluated successfully"));
    }
}
