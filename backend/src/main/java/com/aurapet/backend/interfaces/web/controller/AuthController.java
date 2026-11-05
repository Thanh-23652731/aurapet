package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.AuthService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng nhập, đăng ký, hồ sơ")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request), "Đăng nhập thành công"));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký (luôn tạo tài khoản ROLE_USER)")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request), "Đăng ký thành công"));
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin tài khoản hiện tại")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal CustomUserDetails user) {
        CurrentUser.of(user);
        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(user.getUsername()), "OK"));
    }

    @PutMapping("/me")
    @Operation(summary = "Đổi tên / đổi mật khẩu")
    public ResponseEntity<ApiResponse<UserDto>> updateMe(@AuthenticationPrincipal CustomUserDetails user,
                                                         @Valid @RequestBody UpdateProfileRequest request) {
        CurrentUser.of(user);
        return ResponseEntity.ok(ApiResponse.ok(authService.updateProfile(user.getUsername(), request), "Đã cập nhật hồ sơ"));
    }
}
