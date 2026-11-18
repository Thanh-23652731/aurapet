package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.ApiResponse;
import com.aurapet.backend.application.service.ReidService;
import com.aurapet.backend.domain.model.PetReidPhotoLimits;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Đăng ký nhận diện từng bé (Re-ID) từ web/app: tải ảnh mẫu hoặc bật phiên "đưa mèo ra trước camera".
 *   GET    /api/v1/pets/{id}/reid                       trạng thái: ảnh mẫu, phiên chụp, camera đã học chưa
 *   POST   /api/v1/pets/{id}/reid/photos                thân = ảnh JPEG (web đã thu nhỏ)
 *   GET    /api/v1/pets/{id}/reid/photos/{photoId}      xem ảnh
 *   DELETE /api/v1/pets/{id}/reid/photos/{photoId}      xoá một ảnh;  DELETE .../photos  xoá hết
 *   POST   /api/v1/pets/{id}/reid/capture  {"count":10} bật phiên chụp;  DELETE .../capture  huỷ
 */
@RestController
@RequestMapping("/api/v1/pets/{id}/reid")
@Tag(name = "Re-ID", description = "Đăng ký ảnh mẫu để camera nhận ra từng bé")
public class ReidController {

    private final ReidService reid;

    public ReidController(ReidService reid) {
        this.reid = reid;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable UUID id,
                                                                   @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reid.status(id, CurrentUser.of(user)), "OK"));
    }

    @PostMapping(value = "/photos", consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@PathVariable UUID id, HttpServletRequest req,
                                                                   @AuthenticationPrincipal CustomUserDetails user)
            throws IOException {
        byte[] data = readJpeg(req);
        return ResponseEntity.ok(ApiResponse.ok(reid.addUpload(id, data, CurrentUser.of(user)), "Đã thêm ảnh"));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId,
                                        @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(reid.photo(id, photoId, CurrentUser.of(user)));
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable UUID id, @PathVariable UUID photoId,
                                                                   @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reid.deletePhoto(id, photoId, CurrentUser.of(user)), "Đã xoá ảnh"));
    }

    @DeleteMapping("/photos")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clear(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reid.clear(id, CurrentUser.of(user)), "Đã xoá tất cả ảnh mẫu"));
    }

    @PostMapping("/capture")
    public ResponseEntity<ApiResponse<Map<String, Object>>> capture(@PathVariable UUID id,
                                                                    @RequestBody(required = false) Map<String, Object> body,
                                                                    @AuthenticationPrincipal CustomUserDetails user) {
        int count = 10;
        if (body != null && body.get("count") instanceof Number n) count = n.intValue();
        return ResponseEntity.ok(ApiResponse.ok(reid.startCapture(id, count, CurrentUser.of(user)),
                "Đưa bé ra trước camera, camera sẽ tự chụp"));
    }

    @DeleteMapping("/capture")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancel(@PathVariable UUID id,
                                                                   @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reid.cancelCapture(id, CurrentUser.of(user)), "Đã dừng chụp"));
    }

    static byte[] readJpeg(HttpServletRequest req) throws IOException {
        if (req.getContentLengthLong() > PetReidPhotoLimits.MAX_BYTES) {
            throw new BadRequestException("Ảnh quá lớn (tối đa 1 MB)");
        }
        byte[] data = req.getInputStream().readNBytes(PetReidPhotoLimits.MAX_BYTES + 1);
        if (data.length > PetReidPhotoLimits.MAX_BYTES) {
            throw new BadRequestException("Ảnh quá lớn (tối đa 1 MB)");
        }
        return data;
    }
}
