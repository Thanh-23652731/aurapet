package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.LitterResult;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LitterLogDto {
    private UUID id;
    private UUID petId;
    private LitterResult result;
    private String imageUrl;
    private Instant timestamp;
}
