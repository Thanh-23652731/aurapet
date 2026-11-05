package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.Pet;
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
import java.util.*;

/**
 * Bác sĩ AI: proxy phía server tới Google Gemini.
 * API key chỉ nằm trên server (biến GEMINI_API_KEY), không bao giờ gửi xuống trình duyệt/app.
 * Ngữ cảnh (thông tin mèo, lượng ăn, trạng thái máy) được backend tự lấy từ CSDL.
 */
@Slf4j
@Service
public class GeminiAiService {

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final PetService petService;
    private final HealthAnalyticsService healthService;
    private final DeviceService deviceService;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${aurapet.gemini.api-key:}")
    private String apiKey;

    @Value("${aurapet.gemini.model:gemini-3.5-flash}")
    private String primaryModel;

    @Value("${aurapet.gemini.fallback-model:gemini-flash-lite-latest}")
    private String fallbackModel;

    public GeminiAiService(PetService petService, HealthAnalyticsService healthService,
                           DeviceService deviceService, ObjectMapper mapper) {
        this.petService = petService;
        this.healthService = healthService;
        this.deviceService = deviceService;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public AiChatResponse chat(AiChatRequest request, Actor actor) {
        String context = buildContext(request.getPetId(), actor);
        if (!isConfigured()) {
            return fallback(request.getMessage(), context);
        }
        for (String model : List.of(primaryModel, fallbackModel)) {
            try {
                String reply = callGemini(model, context, request);
                if (reply != null && !reply.isBlank()) {
                    return AiChatResponse.builder().reply(reply.trim()).model(model).build();
                }
            } catch (Exception e) {
                log.warn("[Gemini] Model {} lỗi: {}", model, e.getMessage());
            }
        }
        return fallback(request.getMessage(), context);
    }

    private String callGemini(String model, String context, AiChatRequest request) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.getHistory() != null) {
            for (AiChatTurn turn : request.getHistory()) {
                if (turn == null || turn.getText() == null || turn.getText().isBlank()) continue;
                String role = "model".equalsIgnoreCase(turn.getRole()) ? "model" : "user";
                contents.add(Map.of("role", role, "parts", List.of(Map.of("text", turn.getText()))));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", request.getMessage()))));

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt(context)))),
                "contents", contents,
                // Model Gemini 3.x "suy nghĩ" trước khi trả lời và token suy nghĩ tính vào maxOutputTokens:
                // để 800 thì gần hết bị suy nghĩ chiếm, câu trả lời bị cắt. Giảm mức suy nghĩ và nới giới hạn.
                "generationConfig", Map.of(
                        "maxOutputTokens", 2048,
                        "temperature", 0.6,
                        "thinkingConfig", Map.of("thinkingLevel", "low")));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_BASE + model + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey) // không đặt key trong URL để tránh lộ qua log
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        JsonNode root = mapper.readTree(res.body());
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
            sb.append(part.path("text").asText(""));
        }
        return sb.toString();
    }

    private String systemPrompt(String context) {
        return """
                Bạn là "Bác sĩ AI" của hệ thống chăm sóc mèo AuraPet.
                Trả lời bằng tiếng Việt, ngắn gọn (tối đa khoảng 150 chữ), thân thiện, dùng gạch đầu dòng khi liệt kê.
                Dựa vào dữ liệu thật dưới đây. Nếu có dấu hiệu nguy hiểm (bỏ ăn > 24 giờ, nôn liên tục, khó thở, tiểu ra máu...)
                hãy khuyên đưa mèo đi bác sĩ thú y ngay. Không chẩn đoán chắc chắn, không kê thuốc người.

                DỮ LIỆU HIỆN TẠI:
                """ + context;
    }

    private String buildContext(UUID petId, Actor actor) {
        StringBuilder sb = new StringBuilder();
        try {
            if (petId != null) {
                Pet pet = petService.requireAccessible(petId, actor);
                sb.append(String.format("- Mèo: %s, giống %s, %d tuổi, %s, %s, nặng %.2f kg%n",
                        pet.getName(), pet.getBreed(), pet.getAge(),
                        pet.getGender() != null && pet.getGender().name().equals("MALE") ? "đực" : "cái",
                        Boolean.TRUE.equals(pet.getNeutered()) ? "đã triệt sản" : "chưa triệt sản",
                        pet.getWeight()));
                HealthSummaryDto h = healthService.getHealthSummary(petId, actor);
                sb.append(String.format("- Ăn trung bình 7 ngày: %.0f g/ngày (khuyến nghị ~%d g). Điểm sức khoẻ: %d (%s)%n",
                        h.getDailyAvgFoodGram(), h.getExpectedDailyFoodGram(), h.getHealthScore(), h.getStatus()));
                sb.append("- Đi vệ sinh 7 ngày: ").append(h.getWeeklyLitterVisits()).append(" lần\n");
            }
        } catch (Exception e) {
            log.debug("[Gemini] Không lấy được dữ liệu mèo: {}", e.getMessage());
        }
        DeviceStateDto d = deviceService.getState();
        sb.append("- Máy cho ăn: ").append(d.isOnline() ? "đang online" : "offline");
        if (d.isOnline()) {
            sb.append(String.format(", khay còn %.0f g, thùng chứa %d%%", d.getBowlWeightGram(), d.getFoodLevelPercent()));
        }
        sb.append(", khẩu phần cài đặt ").append(d.getSettings().getTargetGram()).append(" g\n");
        sb.append("- Bình nước (bơm tuần hoàn): ");
        if (!d.isWaterOnline()) sb.append("chưa có số liệu cảm biến\n");
        else if (d.getWaterPercent() == null) sb.append("cảm biến đang lỗi\n");
        else sb.append("còn ").append(d.getWaterPercent()).append('%').append(d.isLowWater() ? " (SẮP CẠN, cần đổ thêm)" : "").append('\n');
        sb.append("- Giờ hiện tại: ").append(DeviceService.nowHhMm()).append('\n');
        return sb.toString();
    }

    /** Trả lời đơn giản khi chưa cấu hình Gemini, vẫn dựa trên dữ liệu thật. */
    private AiChatResponse fallback(String message, String context) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String reply;
        if (m.contains("ăn") || m.contains("khẩu phần") || m.contains("hạt")) {
            reply = "Mèo trưởng thành thường cần khoảng 15g hạt khô cho mỗi kg cân nặng mỗi ngày, chia 2–3 bữa. "
                    + "Bạn có thể bật Lịch hẹn giờ để máy tự cho ăn đúng giờ.";
        } else if (m.contains("nước") || m.contains("uống")) {
            reply = "Mèo cần khoảng 40–60 ml nước cho mỗi kg cân nặng mỗi ngày. Nước chảy (đài phun) giúp mèo uống nhiều hơn.";
        } else {
            reply = "Mình là Bác sĩ AI của AuraPet. Bạn có thể hỏi về khẩu phần ăn, nước uống, cân nặng hoặc thói quen của bé.";
        }
        reply += "\n\nDữ liệu mình đang thấy:\n" + context
                + "\n(Chế độ trả lời cơ bản — thêm GEMINI_API_KEY vào file .env để dùng Gemini.)";
        return AiChatResponse.builder().reply(reply).model("local-fallback").build();
    }
}
