package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.AiChatRequest;
import com.aurapet.backend.application.dto.AiChatResponse;
import com.aurapet.backend.application.dto.ApiResponse;
import com.aurapet.backend.application.service.GeminiAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Server-side Gemini AI chat proxy for pet health consultation")
public class AiChatController {

    private final GeminiAiService geminiAiService;

    @PostMapping("/chat")
    @Operation(summary = "Send a message to the AI Vet Doctor (Gemini proxy)")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(@RequestBody AiChatRequest request) {
        AiChatResponse response = geminiAiService.chat(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "AI response generated"));
    }

    @GetMapping("/status")
    @Operation(summary = "Check if Gemini AI is configured on the server")
    public ResponseEntity<ApiResponse<Boolean>> getStatus() {
        boolean configured = geminiAiService.isConfigured();
        return ResponseEntity.ok(ApiResponse.ok(configured,
                configured ? "Gemini AI is configured" : "Gemini AI key not set on server"));
    }
}
