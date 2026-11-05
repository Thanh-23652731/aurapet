package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.application.service.PetService;
import com.aurapet.backend.infrastructure.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
@Tag(name = "Pets", description = "Quản lý thú cưng (có kiểm tra quyền sở hữu)")
public class PetController {

    private final PetService petService;

    @GetMapping
    @Operation(summary = "Danh sách thú cưng (người dùng thường chỉ thấy mèo của mình)")
    public ResponseEntity<ApiResponse<PetPageData>> getPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) UUID ownerId,
            @AuthenticationPrincipal CustomUserDetails user) {
        PetPageData data = petService.getPets(page, size, search, breed, gender, ownerId, CurrentUser.of(user));
        return ResponseEntity.ok(ApiResponse.ok(data, "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PetDto>> getPet(@PathVariable UUID id,
                                                      @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(petService.getPet(id, CurrentUser.of(user)), "OK"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PetDto>> createPet(@Valid @RequestBody CreatePetRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(petService.createPet(request, CurrentUser.of(user)), "Đã thêm thú cưng"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PetDto>> updatePet(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdatePetRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(petService.updatePet(id, request, CurrentUser.of(user)), "Đã cập nhật"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePet(@PathVariable UUID id,
                                                       @AuthenticationPrincipal CustomUserDetails user) {
        petService.deletePet(id, CurrentUser.of(user));
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xoá thú cưng"));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ApiResponse<PetDto>> updatePetImage(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdatePetImageRequest request,
                                                              @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(
                petService.updatePetImage(id, request.getImageUrl(), CurrentUser.of(user)), "Đã cập nhật ảnh"));
    }
}
