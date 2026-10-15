package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.AiChatRequest;
import com.aurapet.backend.application.dto.AiChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Server-side Gemini AI proxy.
 * Keeps the API key on the backend — never exposed to the browser.
 */
@Service
@Slf4j
public class GeminiAiService {

    @Value("${aurapet.gemini.api-key:}")
    private String geminiApiKey;

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final List<String> MODEL_CANDIDATES = List.of(
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-pro"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private String cachedModel = null;

    public boolean isConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (!isConfigured()) {
            return fallbackResponse(request);
        }

        String systemPrompt = buildSystemPrompt(request);
        try {
            String model = resolveModel();
            String url = GEMINI_BASE + "/" + model + ":generateContent?key=" + geminiApiKey;

            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "contents", List.of(
                            Map.of("role", "user",
                                    "parts", List.of(Map.of("text", request.getMessage())))
                    ),
                    "generationConfig", Map.of(
                            "maxOutputTokens", 1024,
                            "temperature", 0.7
                    )
            );

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> httpRes = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpRes.statusCode() == 200) {
                JsonNode root = mapper.readTree(httpRes.body());
                String text = root.at("/candidates/0/content/parts/0/text").asText("");
                return AiChatResponse.builder()
                        .reply(text.isBlank() ? "Xin lỗi, mình không thể phân tích lúc này." : text)
                        .model(model)
                        .build();
            } else {
                log.warn("Gemini API error {}: {}", httpRes.statusCode(), httpRes.body());
                return fallbackResponse(request);
            }
        } catch (Exception e) {
            log.error("Gemini call failed", e);
            return fallbackResponse(request);
        }
    }

    private String resolveModel() {
        if (cachedModel != null) return cachedModel;

        for (String model : MODEL_CANDIDATES) {
            try {
                String url = GEMINI_BASE + "/" + model + ":generateContent?key=" + geminiApiKey;
                Map<String, Object> ping = Map.of(
                        "contents", List.of(Map.of("role", "user",
                                "parts", List.of(Map.of("text", "ping")))),
                        "generationConfig", Map.of("maxOutputTokens", 5)
                );
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(ping)))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    cachedModel = model;
                    log.info("Resolved Gemini model: {}", model);
                    return model;
                }
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("No compatible Gemini model found");
    }

    private String buildSystemPrompt(AiChatRequest req) {
        return String.format("""
                Bạn là "Bác Sĩ AI" chuyên chăm sóc sức khỏe mèo trong hệ thống AuraPet Smart Pet Care.
                Dữ liệu cảm biến hiện tại:
                - Tên mèo: %s
                - Cân nặng: %s
                - Nhu cầu calo: %s
                - Lượng thức ăn/ngày: %s
                - Khay cát: %s
                Hãy trả lời ngắn gọn, chuyên nghiệp, bằng tiếng Việt.
                Đưa ra lời khuyên dựa trên dữ liệu cảm biến thực tế.
                """,
                req.getPetName() != null ? req.getPetName() : "Chưa rõ",
                req.getPetWeight() != null ? req.getPetWeight() : "Chưa rõ",
                req.getPetCalories() != null ? req.getPetCalories() : "Chưa rõ",
                req.getPetFood() != null ? req.getPetFood() : "Chưa rõ",
                req.getLitterStatus() != null ? req.getLitterStatus() : "Chưa rõ"
        );
    }

    private AiChatResponse fallbackResponse(AiChatRequest request) {
        String msg = request.getMessage() != null ? request.getMessage().toLowerCase() : "";
        String reply;

        if (msg.contains("sức khỏe") || msg.contains("đánh giá")) {
            reply = String.format("Dựa trên dữ liệu hiện tại, bé %s nặng %s đang ở mức thể trạng cần theo dõi. "
                            + "Nên duy trì khẩu phần ăn đều đặn và cân lại sau 1 tuần.",
                    request.getPetName(), request.getPetWeight());
        } else if (msg.contains("khẩu phần") || msg.contains("ăn")) {
            reply = String.format("Với cân nặng %s, bé %s cần khoảng %s mỗi ngày, chia đều 2-3 bữa. "
                            + "Nên chọn hạt có hàm lượng protein ≥ 30%%.",
                    request.getPetWeight(), request.getPetName(), request.getPetFood());
        } else if (msg.contains("nước") || msg.contains("uống")) {
            reply = "Mèo cần uống 40-60ml nước/kg cân nặng mỗi ngày. Đài phun nước tuần hoàn giúp mèo uống nhiều hơn.";
        } else {
            reply = String.format("Mình là Bác Sĩ AI của bé %s. Bạn có thể hỏi về sức khỏe, khẩu phần ăn, "
                    + "lượng nước uống, hoặc tình trạng khay cát.", request.getPetName());
        }

        return AiChatResponse.builder()
                .reply(reply)
                .model("local-fallback")
                .build();
    }
}
