package com.aurapet.backend.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodControlCommand {
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

    @JsonProperty("calories")
    private Integer calories;

    @JsonProperty("scaleWeightGram")
    private Integer scaleWeightGram;

    @JsonProperty("catDetected")
    private Boolean catDetected;

    @JsonProperty("scheduleTimes")
    private String scheduleTimes;

    private Integer foodLevelPercent;
}
