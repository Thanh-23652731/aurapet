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
import org.springframework.security.access.AccessDeniedException;

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

    @InjectMocks
    private PetService petService;

    private Pet samplePet;
    private UUID petId;
    private Actor owner;

    @BeforeEach
    void setUp() {
        petId = UUID.randomUUID();
        owner = new Actor(UUID.randomUUID(), false);
        samplePet = Pet.builder()
                .id(petId).ownerId(owner.id()).name("Luna").breed("Anh lông ngắn")
                .age(2).gender(Gender.FEMALE).neutered(true).weight(4.25)
                .build();
    }

    @Test
    @DisplayName("Chủ sở hữu xem được thú cưng")
    void ownerCanRead() {
        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));
        PetDto dto = petService.getPet(petId, owner);
        assertThat(dto.getName()).isEqualTo("Luna");
    }

    @Test
    @DisplayName("Người khác bị từ chối (403)")
    void strangerIsDenied() {
        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));
        Actor stranger = new Actor(UUID.randomUUID(), false);
        assertThatThrownBy(() -> petService.getPet(petId, stranger)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> petService.deletePet(petId, stranger)).isInstanceOf(AccessDeniedException.class);
        verify(petRepository, never()).delete(any(Pet.class));
    }

    @Test
    @DisplayName("Admin xem được mọi thú cưng")
    void adminCanRead() {
        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));
        assertThat(petService.getPet(petId, new Actor(UUID.randomUUID(), true)).getName()).isEqualTo("Luna");
    }

    @Test
    @DisplayName("Không tồn tại -> ResourceNotFoundException")
    void notFound() {
        when(petRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> petService.getPet(UUID.randomUUID(), owner))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pet not found");
    }

    @Test
    @DisplayName("Người dùng thường không thể tạo mèo đứng tên người khác")
    void createAlwaysOwnedByActor() {
        when(petRepository.save(any(Pet.class))).thenAnswer(inv -> {
            Pet p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        CreatePetRequest request = CreatePetRequest.builder()
                .ownerId(UUID.randomUUID()) // cố gán cho người khác
                .name("Milo").breed("Scottish Fold").age(1).gender(Gender.MALE).neutered(true).weight(3.8)
                .build();
        PetDto created = petService.createPet(request, owner);
        assertThat(created.getOwnerId()).isEqualTo(owner.id());
    }

    @Test
    @DisplayName("Cập nhật từng trường")
    void updatePet() {
        when(petRepository.findById(petId)).thenReturn(Optional.of(samplePet));
        when(petRepository.save(any(Pet.class))).thenAnswer(inv -> inv.getArgument(0));
        PetDto updated = petService.updatePet(petId, UpdatePetRequest.builder().name("Luna 2").weight(4.5).build(), owner);
        assertThat(updated.getName()).isEqualTo("Luna 2");
        assertThat(updated.getWeight()).isEqualTo(4.5);
        assertThat(updated.getBreed()).isEqualTo("Anh lông ngắn");
    }
}
