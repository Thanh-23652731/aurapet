package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.ApiResponse;
import com.aurapet.backend.application.service.CameraService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Camera trực tiếp.
 *   POST /api/v1/camera/frame   Raspberry Pi gửi ảnh JPEG (xác thực bằng header X-Camera-Key = CAMERA_KEY trong .env)
 *   GET  /api/v1/camera/frame   Web/App (cần đăng nhập) lấy khung mới hơn ?after=..., chờ tối đa ?wait= ms
 *   GET  /api/v1/camera/status  Trạng thái camera (cần đăng nhập)
 */
@RestController
@RequestMapping("/api/v1/camera")
public class CameraController {

    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

    private final CameraService camera;

    @Value("${aurapet.camera.key:}")
    private String cameraKey;

    public CameraController(CameraService camera) {
        this.camera = camera;
    }

    @PostMapping("/frame")
    public ResponseEntity<ApiResponse<Void>> upload(HttpServletRequest req,
                                                    @RequestHeader(value = "X-Camera-Key", required = false) String key,
                                                    @RequestHeader(value = "X-Cat-Present", required = false) String cat,
                                                    @RequestHeader(value = "X-Cat-Confidence", required = false) String conf)
            throws IOException {
        if (cameraKey == null || cameraKey.isBlank()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Máy chủ chưa đặt CAMERA_KEY trong .env nên chưa nhận hình từ camera");
        }
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), cameraKey.getBytes(StandardCharsets.UTF_8))) {
            return error(HttpStatus.FORBIDDEN, "Sai CAMERA_KEY");
        }
        long len = req.getContentLengthLong();
        if (len > MAX_FRAME_BYTES) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh quá lớn (tối đa 2 MB)");
        }
        byte[] data = readLimited(req.getInputStream());
        if (data == null) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh quá lớn (tối đa 2 MB)");
        }
        // Ảnh JPEG luôn bắt đầu bằng FF D8
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            return error(HttpStatus.BAD_REQUEST, "Dữ liệu không phải ảnh JPEG");
        }
        camera.putFrame(data, "true".equalsIgnoreCase(cat), parseConf(conf));
        return ResponseEntity.ok(ApiResponse.ok(null, "OK"));
    }

    @GetMapping("/frame")
    public ResponseEntity<byte[]> frame(@RequestParam(defaultValue = "0") long after,
                                        @RequestParam(defaultValue = "2000") long wait) throws InterruptedException {
        CameraService.Frame f = camera.await(after, wait);
        HttpHeaders h = headers(f);
        h.setCacheControl(CacheControl.noStore());
        if (f.jpeg() == null) {
            return new ResponseEntity<>(h, HttpStatus.NO_CONTENT);
        }
        h.setContentType(MediaType.IMAGE_JPEG);
        return new ResponseEntity<>(f.jpeg(), h, HttpStatus.OK);
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        CameraService.Frame f = camera.status();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("online", f.online());
        m.put("lastFrameAt", f.at());
        m.put("fps", Math.round(f.fps() * 10) / 10.0);
        m.put("catPresent", f.catPresent());
        m.put("confidence", f.confidence());
        m.put("keyConfigured", cameraKey != null && !cameraKey.isBlank());
        return ResponseEntity.ok(ApiResponse.ok(m, "OK"));
    }

    private static HttpHeaders headers(CameraService.Frame f) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Camera-Online", String.valueOf(f.online()));
        h.set("X-Frame-Seq", String.valueOf(f.seq()));
        if (f.at() != null) h.set("X-Frame-Time", String.valueOf(f.at().toEpochMilli()));
        h.set("X-Cat-Present", String.valueOf(f.catPresent()));
        h.set("X-Cat-Confidence", String.format(Locale.ROOT, "%.2f", f.confidence()));
        h.set("X-Camera-Fps", String.format(Locale.ROOT, "%.1f", f.fps()));
        return h;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_FRAME_BYTES + 1);
        return data.length > MAX_FRAME_BYTES ? null : data;
    }

    private static double parseConf(String v) {
        try {
            return v == null ? 0 : Math.max(0, Math.min(1, Double.parseDouble(v)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(ApiResponse.error(msg));
    }
}
