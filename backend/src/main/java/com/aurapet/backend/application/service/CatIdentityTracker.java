package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.PetRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Nhớ bé mèo mà camera vừa nhận ra (trong 60 giây) để lần nhả hạt kế tiếp
 * được ghi nhật ký cho đúng bé đó. Camera ghi vào, máy cho ăn đọc ra.
 */
@Component
public class CatIdentityTracker {

    static final Duration HOLD = Duration.ofSeconds(60);

    private final PetRepository petRepository;

    private UUID petId;
    private String petName;
    private Instant expiresAt = Instant.EPOCH;

    public CatIdentityTracker(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public synchronized void remember(Pet pet) {
        petId = pet.getId();
        petName = pet.getName();
        expiresAt = Instant.now().plus(HOLD);
    }

    /** Tên bé đang được nhận diện, null nếu không có hoặc đã quá 60 giây. */
    public synchronized String currentName() {
        return Instant.now().isBefore(expiresAt) ? petName : null;
    }

    /** Lấy bé vừa nhận diện cho 1 lần nhả hạt rồi xoá, để lần sau không bị gán nhầm. */
    public synchronized Optional<Pet> consume() {
        UUID id = Instant.now().isBefore(expiresAt) ? petId : null;
        petId = null;
        petName = null;
        expiresAt = Instant.EPOCH;
        return id == null ? Optional.empty() : petRepository.findById(id);
    }
}
