package com.aurapet.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemWaterDto {
    private String id;
    private String deviceId;

    @JsonProperty("auto")
    private Boolean auto;

    @JsonProperty("btnLed")
    private Boolean btnLed;

    @JsonProperty("ledGreen")
    private Boolean ledGreen;

    @JsonProperty("ledRed")
    private Boolean ledRed;

    @JsonProperty("machines")
    private Boolean machines;

    @JsonProperty("percel")
    private Integer percel;

    @JsonProperty("catDetected")
    private Boolean catDetected;

    private Integer waterLevelPercent;
    private Instant lastPumped;
    private Instant updatedAt;
}
