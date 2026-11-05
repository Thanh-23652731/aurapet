package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePetImageRequest {

    @NotBlank(message = "Vui lòng nhập đường dẫn ảnh")
    @Size(max = 1024)
    private String imageUrl;
}
