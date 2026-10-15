package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterCommandRequest {

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;
}
