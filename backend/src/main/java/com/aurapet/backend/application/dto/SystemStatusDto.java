package com.aurapet.backend.application.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatusDto {
    private Boolean feeder;
    private Boolean waterPump;
    private Boolean litterCleaner;
    private String esp32;
    private String raspberryPi;
    private Integer foodLevelPercent;
    private Integer waterLevelPercent;
    private Integer litterWastePercent;
}
