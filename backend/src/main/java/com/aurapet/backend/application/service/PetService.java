package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý thú cưng. Mọi thao tác đều kiểm tra quyền sở hữu:
 * người dùng thường chỉ xem/sửa/xoá mèo của mình, admin xem được tất cả.
 */
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;

    @Transactional(readOnly = true)
    public PetPageData getPets(int page, int size, String search, String breed, String genderStr,
                               UUID ownerId, Actor actor) {
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.ASC, "createdAt"));

        Gender gender = null;
        if (genderStr != null && !genderStr.isBlank()) {
            try {
                gender = Gender.valueOf(genderStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // giá trị lạ -> bỏ lọc
            }
        }
        UUID ownerFilter = actor.admin() ? ownerId : actor.id();

        Page<Pet> petPage = petRepository.findWithFilters(blankToNull(search), blankToNull(breed), gender, ownerFilter, pageable);

        return PetPageData.builder()
                .items(petPage.getContent().stream().map(this::mapToDto).toList())
                .page(PageMetadata.builder()
                        .page(petPage.getNumber())
                        .size(petPage.getSize())
                        .totalElements(petPage.getTotalElements())
                        .totalPages(petPage.getTotalPages())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public PetDto getPet(UUID id, Actor actor) {
        return mapToDto(requireAccessible(id, actor));
    }

    @Transactional
    public PetDto createPet(CreatePetRequest request, Actor actor) {
        UUID ownerId = (actor.admin() && request.getOwnerId() != null) ? request.getOwnerId() : actor.id();
        Pet pet = Pet.builder()
                .ownerId(ownerId)
                .name(request.getName().trim())
                .breed(request.getBreed().trim())
                .age(request.getAge())
                .gender(request.getGender())
                .neutered(request.getNeutered())
                .weight(request.getWeight())
                .imageUrl(blankToNull(request.getImageUrl()))
                .build();
        return mapToDto(petRepository.save(pet));
    }

    @Transactional
    public PetDto updatePet(UUID id, UpdatePetRequest request, Actor actor) {
        Pet pet = requireAccessible(id, actor);
        if (request.getName() != null) pet.setName(request.getName().trim());
        if (request.getBreed() != null) pet.setBreed(request.getBreed().trim());
        if (request.getAge() != null) pet.setAge(request.getAge());
        if (request.getGender() != null) pet.setGender(request.getGender());
        if (request.getNeutered() != null) pet.setNeutered(request.getNeutered());
        if (request.getWeight() != null) pet.setWeight(request.getWeight());
        if (request.getImageUrl() != null) pet.setImageUrl(blankToNull(request.getImageUrl()));
        return mapToDto(petRepository.save(pet));
    }

    @Transactional
    public void deletePet(UUID id, Actor actor) {
        Pet pet = requireAccessible(id, actor);
        petRepository.delete(pet);
    }

    @Transactional
    public PetDto updatePetImage(UUID id, String imageUrl, Actor actor) {
        Pet pet = requireAccessible(id, actor);
        pet.setImageUrl(blankToNull(imageUrl));
        return mapToDto(petRepository.save(pet));
    }

    /** Lấy pet và kiểm tra quyền; ném 404 nếu không có, 403 nếu không phải chủ. */
    public Pet requireAccessible(UUID id, Actor actor) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thú cưng (Pet not found): " + id));
        if (!actor.admin() && !pet.getOwnerId().equals(actor.id())) {
            throw new AccessDeniedException("Bạn không có quyền với thú cưng này");
        }
        return pet;
    }

    /** Danh sách mèo mà người dùng được phép xem. */
    public List<Pet> accessiblePets(Actor actor) {
        return actor.admin()
                ? petRepository.findAllByOrderByCreatedAtAsc()
                : petRepository.findByOwnerIdOrderByCreatedAtAsc(actor.id());
    }

    public PetDto mapToDto(Pet pet) {
        return PetDto.builder()
                .id(pet.getId())
                .ownerId(pet.getOwnerId())
                .name(pet.getName())
                .breed(pet.getBreed())
                .age(pet.getAge())
                .gender(pet.getGender())
                .neutered(pet.getNeutered())
                .weight(pet.getWeight())
                .imageUrl(pet.getImageUrl())
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }
}
