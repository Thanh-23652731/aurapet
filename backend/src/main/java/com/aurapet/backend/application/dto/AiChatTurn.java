package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatTurn {
    /** "user" hoặc "model". */
    private String role;

    @Size(max = 4000)
    private String text;
}
