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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
@Tag(name = "Pets", description = "Pet management endpoints")
public class PetController {

    private final PetService petService;

    @GetMapping
    @Operation(summary = "List pets with pagination, search, and filtering")
    public ResponseEntity<ApiResponse<PetPageData>> getPets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) UUID ownerId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        UUID effectiveOwnerId = ownerId;
        if (currentUser != null) {
            boolean isAdmin = currentUser.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                // Người dùng thông thường chỉ xem được thú cưng do chính họ sở hữu
                effectiveOwnerId = currentUser.getId();
            }
        }

        PetPageData data = petService.getPets(page, size, search, breed, gender, effectiveOwnerId);
        return ResponseEntity.ok(ApiResponse.ok(data, "Pets retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve pet by UUID")
    public ResponseEntity<ApiResponse<PetDto>> getPetById(@PathVariable UUID id) {
        PetDto pet = petService.getPetById(id);
        return ResponseEntity.ok(ApiResponse.ok(pet, "Pet details retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Register a new pet")
    public ResponseEntity<ApiResponse<PetDto>> createPet(
            @Valid @RequestBody CreatePetRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UUID ownerId = currentUser != null ? currentUser.getId() : null;
        PetDto pet = petService.createPet(request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pet, "Pet created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update existing pet")
    public ResponseEntity<ApiResponse<PetDto>> updatePet(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePetRequest request) {
        PetDto updated = petService.updatePet(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Pet updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete pet")
    public ResponseEntity<ApiResponse<Void>> deletePet(@PathVariable UUID id) {
        petService.deletePet(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Pet deleted successfully"));
    }

    @PostMapping("/{id}/image")
    @Operation(summary = "Update pet profile image")
    public ResponseEntity<ApiResponse<PetDto>> updatePetImage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePetImageRequest request) {
        PetDto updated = petService.updatePetImage(id, request.getImageUrl());
        return ResponseEntity.ok(ApiResponse.ok(updated, "Pet image updated successfully"));
    }
}
