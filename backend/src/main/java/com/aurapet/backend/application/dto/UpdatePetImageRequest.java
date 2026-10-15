package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePetImageRequest {

    @NotBlank(message = "Image URL is required")
    private String imageUrl;
}
