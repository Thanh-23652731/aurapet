package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.CreatePetRequest;
import com.aurapet.backend.application.dto.PetDto;
import com.aurapet.backend.application.dto.UpdatePetRequest;
import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private com.aurapet.backend.domain.repository.UserRepository userRepository;

    @InjectMocks
    private PetService petService;

    private Pet samplePet;
    private UUID petId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        petId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        samplePet = Pet.builder()
                .id(petId)
                .ownerId(ownerId)
                .name("Luna")
                .breed("British Shorthair")
                .age(2)
                .gender(Gender.FEMALE)
                .neutered(true)
                .weight(4.25)
                .imageUrl("https://example.com/luna.jpg")
                .build();
    }

    @Test
    @DisplayName("Should return PetDto when pet exists")
    void testGetPetByIdSuccess() {
        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));

        PetDto result = petService.getPetById(petId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Luna");
        assertThat(result.getBreed()).isEqualTo("British Shorthair");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when pet does not exist")
    void testGetPetByIdNotFound() {
        when(petRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> petService.getPetById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pet not found");
    }

    @Test
    @DisplayName("Should create pet successfully")
    void testCreatePet() {
        CreatePetRequest request = CreatePetRequest.builder()
                .name("Milo")
                .breed("Scottish Fold")
                .age(1)
                .gender(Gender.MALE)
                .neutered(true)
                .weight(3.8)
                .build();

        when(petRepository.save(any(Pet.class))).thenAnswer(invocation -> {
            Pet p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PetDto created = petService.createPet(request, ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("Milo");
        assertThat(created.getBreed()).isEqualTo("Scottish Fold");
        verify(petRepository, times(1)).save(any(Pet.class));
    }

    @Test
    @DisplayName("Should update pet fields")
    void testUpdatePet() {
        UpdatePetRequest updateRequest = UpdatePetRequest.builder()
                .name("Luna Updated")
                .weight(4.5)
                .build();

        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));
        when(petRepository.save(any(Pet.class))).thenReturn(samplePet);

        PetDto updated = petService.updatePet(petId, updateRequest);

        assertThat(updated.getName()).isEqualTo("Luna Updated");
        assertThat(updated.getWeight()).isEqualTo(4.5);
    }
}
