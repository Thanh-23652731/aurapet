package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.AlertLevel;
import com.aurapet.backend.domain.model.AlertType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDto {
    private UUID id;
    private AlertType type;
    private AlertLevel level;
    private String message;
    private Instant createdAt;
}
