package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeederCommandRequest {

    private UUID petId;

    @NotNull(message = "Portion gram is required")
    @Min(value = 5, message = "Portion must be at least 5 grams")
    @Max(value = 150, message = "Portion cannot exceed 150 grams")
    private Integer portionGram;
}
