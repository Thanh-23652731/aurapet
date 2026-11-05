package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.AiChatRequest;
import com.aurapet.backend.application.dto.AiChatResponse;
import com.aurapet.backend.application.dto.ApiResponse;
import com.aurapet.backend.application.service.GeminiAiService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Bác sĩ AI (Gemini, key giữ trên server)")
public class AiChatController {

    private final GeminiAiService geminiAiService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request,
                                                            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(geminiAiService.chat(request, CurrentUser.of(user)), "OK"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Boolean>> status() {
        boolean ok = geminiAiService.isConfigured();
        return ResponseEntity.ok(ApiResponse.ok(ok, ok ? "Đã cấu hình Gemini" : "Chưa có GEMINI_API_KEY — dùng chế độ cơ bản"));
    }
}
