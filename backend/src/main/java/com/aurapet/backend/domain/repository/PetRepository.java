package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PetRepository extends JpaRepository<Pet, UUID>, JpaSpecificationExecutor<Pet> {
    List<Pet> findByOwnerId(UUID ownerId);

    @Query("SELECT p FROM Pet p WHERE " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.breed) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:breed IS NULL OR LOWER(p.breed) = LOWER(:breed)) AND " +
           "(:gender IS NULL OR p.gender = :gender) AND " +
           "(:ownerId IS NULL OR p.ownerId = :ownerId)")
    Page<Pet> findWithFilters(
            @Param("search") String search,
            @Param("breed") String breed,
            @Param("gender") Gender gender,
            @Param("ownerId") UUID ownerId,
            Pageable pageable);
}
