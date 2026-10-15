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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final com.aurapet.backend.domain.repository.UserRepository userRepository;

    @Transactional(readOnly = true)
    public PetPageData getPets(int page, int size, String search, String breed, String genderStr, UUID ownerId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Gender gender = null;
        if (genderStr != null && !genderStr.trim().isEmpty()) {
            try {
                gender = Gender.valueOf(genderStr.toUpperCase().trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String searchFilter = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String breedFilter = (breed != null && !breed.trim().isEmpty()) ? breed.trim() : null;

        Page<Pet> petPage = petRepository.findWithFilters(searchFilter, breedFilter, gender, ownerId, pageable);

        List<PetDto> items = petPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        PageMetadata pageMetadata = PageMetadata.builder()
                .page(petPage.getNumber())
                .size(petPage.getSize())
                .totalElements(petPage.getTotalElements())
                .totalPages(petPage.getTotalPages())
                .build();

        return PetPageData.builder()
                .items(items)
                .page(pageMetadata)
                .build();
    }

    @Transactional(readOnly = true)
    public PetDto getPetById(UUID id) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with ID: " + id));
        return mapToDto(pet);
    }

    @Transactional
    public PetDto createPet(CreatePetRequest request, UUID defaultOwnerId) {
        UUID ownerId = request.getOwnerId() != null ? request.getOwnerId() : defaultOwnerId;
        if (ownerId == null) {
            ownerId = userRepository.findAll().stream()
                    .findFirst()
                    .map(com.aurapet.backend.domain.model.User::getId)
                    .orElse(UUID.randomUUID());
        }

        Pet pet = Pet.builder()
                .ownerId(ownerId)
                .name(request.getName().trim())
                .breed(request.getBreed().trim())
                .age(request.getAge())
                .gender(request.getGender())
                .neutered(request.getNeutered())
                .weight(request.getWeight())
                .imageUrl(request.getImageUrl())
                .build();

        Pet saved = petRepository.save(pet);
        return mapToDto(saved);
    }

    @Transactional
    public PetDto updatePet(UUID id, UpdatePetRequest request) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with ID: " + id));

        if (request.getName() != null) pet.setName(request.getName().trim());
        if (request.getBreed() != null) pet.setBreed(request.getBreed().trim());
        if (request.getAge() != null) pet.setAge(request.getAge());
        if (request.getGender() != null) pet.setGender(request.getGender());
        if (request.getNeutered() != null) pet.setNeutered(request.getNeutered());
        if (request.getWeight() != null) pet.setWeight(request.getWeight());
        if (request.getImageUrl() != null) pet.setImageUrl(request.getImageUrl().trim());

        Pet updated = petRepository.save(pet);
        return mapToDto(updated);
    }

    @Transactional
    public void deletePet(UUID id) {
        if (id != null && petRepository.existsById(id)) {
            petRepository.deleteById(id);
        }
    }

    @Transactional
    public PetDto updatePetImage(UUID id, String imageUrl) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with ID: " + id));
        pet.setImageUrl(imageUrl);
        return mapToDto(petRepository.save(pet));
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
}
