package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @Test
    @DisplayName("Should save and find Pet by ID")
    void testSaveAndFindById() {
        Pet pet = Pet.builder()
                .ownerId(UUID.randomUUID())
                .name("Bella")
                .breed("Persian")
                .age(3)
                .gender(Gender.FEMALE)
                .neutered(true)
                .weight(3.9)
                .imageUrl("https://example.com/bella.jpg")
                .build();

        Pet saved = petRepository.save(pet);
        assertThat(saved.getId()).isNotNull();

        Pet found = petRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Bella");
        assertThat(found.getBreed()).isEqualTo("Persian");
    }

    @Test
    @DisplayName("Should filter pets by breed and gender")
    void testFindWithFilters() {
        UUID ownerId = UUID.randomUUID();
        petRepository.save(Pet.builder()
                .ownerId(ownerId)
                .name("Simba")
                .breed("Bengal")
                .age(2)
                .gender(Gender.MALE)
                .neutered(false)
                .weight(5.2)
                .build());

        Page<Pet> page = petRepository.findWithFilters("Simba", "Bengal", Gender.MALE, ownerId, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Simba");
    }
}
