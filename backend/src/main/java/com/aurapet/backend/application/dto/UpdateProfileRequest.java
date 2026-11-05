package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/** Cập nhật hồ sơ: đổi tên và/hoặc đổi mật khẩu (cần mật khẩu hiện tại). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Size(min = 1, max = 100, message = "Họ tên từ 1 đến 100 ký tự")
    private String name;

    private String currentPassword;

    @Size(min = 6, max = 100, message = "Mật khẩu mới từ 6 đến 100 ký tự")
    private String newPassword;
}
