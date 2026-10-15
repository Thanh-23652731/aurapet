package com.aurapet.backend.application.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedingLogDto {
    private UUID id;
    private UUID petId;
    private String petName;
    private Integer foodGram;
    private Instant timestamp;
}
