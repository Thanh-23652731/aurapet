package com.aurapet.backend.application.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeightRecordDto {
    private UUID id;
    private UUID petId;
    private Double weight;
    private Instant recordedAt;
}
