package com.aurapet.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String message;
    private String petName;
    private String petWeight;
    private String petCalories;
    private String petFood;
    private String litterStatus;
}
