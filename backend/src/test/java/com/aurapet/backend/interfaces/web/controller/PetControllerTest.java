package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.model.User;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private UserRepository userRepository;

    private Pet adminPet() {
        User admin = userRepository.findByEmail("admin@aurapet.com").orElseThrow();
        return petRepository.findByOwnerIdOrderByCreatedAtAsc(admin.getId()).get(0);
    }

    @Test
    @WithUserDetails("admin@aurapet.com")
    @DisplayName("Admin xem được danh sách thú cưng")
    void listPets() throws Exception {
        mockMvc.perform(get("/api/v1/pets").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @WithUserDetails("admin@aurapet.com")
    @DisplayName("Tạo thú cưng trả về 201")
    void createPet() throws Exception {
        mockMvc.perform(post("/api/v1/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Kira", "breed", "Xiêm", "age", 2,
                                "gender", "FEMALE", "neutered", true, "weight", 3.4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Kira"));
    }

    @Test
    @WithUserDetails("admin@aurapet.com")
    @DisplayName("Thiếu trường bắt buộc trả về 400")
    void createPetValidation() throws Exception {
        mockMvc.perform(post("/api/v1/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @WithUserDetails("user@aurapet.com")
    @DisplayName("Người dùng thường KHÔNG xem/sửa/xoá được mèo của người khác")
    void ownershipIsEnforced() throws Exception {
        Pet pet = adminPet();
        mockMvc.perform(get("/api/v1/pets/" + pet.getId())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/pets/" + pet.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/health/" + pet.getId() + "/summary")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("user@aurapet.com")
    @DisplayName("Danh sách của người dùng thường chỉ gồm mèo của họ")
    void userSeesOnlyOwnPets() throws Exception {
        User user = userRepository.findByEmail("user@aurapet.com").orElseThrow();
        mockMvc.perform(get("/api/v1/pets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].ownerId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo(user.getId().toString()))));
    }
}
