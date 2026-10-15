package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {
    private Integer totalPets;
    private Integer todayFoodGram;
    private Integer feedingSessionsCount;
    private Integer litterUsageCount;
    private List<AlertDto> latestAlerts;
    private SystemStatusDto deviceStatus;
}
