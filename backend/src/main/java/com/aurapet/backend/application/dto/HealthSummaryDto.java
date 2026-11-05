package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.List;
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
    private Integer expectedDailyFoodGram;
    private Integer weeklyLitterVisits;
    private Double weightChangeKg;
    private String aiRecommendation;
    private List<String> notes;
}
