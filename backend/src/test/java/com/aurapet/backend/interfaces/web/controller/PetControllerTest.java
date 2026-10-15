package com.aurapet.backend.interfaces.web.controller;

import com.aurapet.backend.application.dto.CreatePetRequest;
import com.aurapet.backend.domain.model.Gender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin@aurapet.com", roles = {"ADMIN"})
    @DisplayName("Should retrieve pets list with pagination")
    void testGetPetsList() throws Exception {
        mockMvc.perform(get("/api/v1/pets")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @WithMockUser(username = "admin@aurapet.com", roles = {"ADMIN"})
    @DisplayName("Should create pet and return 201 Created")
    void testCreatePetSuccess() throws Exception {
        CreatePetRequest request = CreatePetRequest.builder()
                .name("Kira")
                .breed("Siamese")
                .age(2)
                .gender(Gender.FEMALE)
                .neutered(true)
                .weight(3.4)
                .build();

        mockMvc.perform(post("/api/v1/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Kira"))
                .andExpect(jsonPath("$.data.breed").value("Siamese"));
    }

    @Test
    @WithMockUser(username = "admin@aurapet.com", roles = {"ADMIN"})
    @DisplayName("Should reject pet creation with missing mandatory fields")
    void testCreatePetValidationError() throws Exception {
        CreatePetRequest request = CreatePetRequest.builder()
                .name("") // blank name
                .build();

        mockMvc.perform(post("/api/v1/pets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
