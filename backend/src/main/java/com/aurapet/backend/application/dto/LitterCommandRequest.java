package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LitterCommandRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "CLEAN|RESET|PAUSE", message = "Action must be CLEAN, RESET, or PAUSE")
    private String action;
}
