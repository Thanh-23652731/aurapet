package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSummaryDto {
    private UUID petId;
    private String petName;
    private Integer healthScore;
    private String status;
    private Double dailyAvgFoodGram;
    private Integer weeklyLitterVisits;
    private Double weightChangeKg;
    private String aiRecommendation;
}
