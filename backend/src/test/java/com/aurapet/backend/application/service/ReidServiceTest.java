package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.model.PetReidPhoto;
import com.aurapet.backend.domain.repository.PetReidPhotoRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReidServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    private PetReidPhotoRepository photos;
    private PetRepository pets;
    private ReidService reid;
    private final UUID owner = UUID.randomUUID();
    private final Actor actor = new Actor(owner, false);
    private Pet mimi;
    private final List<PetReidPhoto> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        photos = mock(PetReidPhotoRepository.class);
        pets = mock(PetRepository.class);
        PetService petService = mock(PetService.class);
        mimi = Pet.builder().id(UUID.randomUUID()).ownerId(owner).name("Mimi").build();
        when(petService.requireAccessible(eqId(mimi.getId()), any())).thenReturn(mimi);
        when(pets.existsById(mimi.getId())).thenReturn(true);
        when(pets.findAll()).thenReturn(List.of(mimi));
        when(photos.save(any())).thenAnswer(inv -> {
            PetReidPhoto p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setCreatedAt(Instant.now());
            saved.add(p);
            return p;
        });
        when(photos.countByPetId(any())).thenAnswer(inv -> (long) saved.size());
        when(photos.findInfoByPetId(any())).thenAnswer(inv -> infos());
        when(photos.findAllInfo()).thenAnswer(inv -> infos());
        reid = new ReidService(photos, pets, petService);
    }

    private static UUID eqId(UUID id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }

    private List<PetReidPhotoRepository.Info> infos() {
        return saved.stream().map(p -> new PetReidPhotoRepository.Info(p.getId(), p.getPetId(), p.getSource(),
                p.getUsable(), p.getCreatedAt())).toList();
    }

    @Test
    void uploadRejectsNonJpeg() {
        assertThrows(BadRequestException.class, () -> reid.addUpload(mimi.getId(), new byte[]{1, 2, 3, 4}, actor));
        Map<String, Object> st = reid.addUpload(mimi.getId(), JPEG, actor);
        assertEquals(1, st.get("count"));
        assertEquals(PetReidPhoto.SOURCE_UPLOAD, saved.get(0).getSource());
    }

    @Test
    void captureFlowCountsShotsThenEnds() {
        reid.startCapture(mimi.getId(), 3, actor);
        ReidService.Capture c = reid.currentCapture();
        assertNotNull(c);
        assertEquals(3, c.left());
        assertEquals(2, reid.addCameraShot(mimi.getId(), JPEG));
        assertEquals(1, reid.addCameraShot(mimi.getId(), JPEG));
        assertEquals(0, reid.addCameraShot(mimi.getId(), JPEG));
        assertNull(reid.currentCapture(), "chụp đủ thì phiên kết thúc");
        assertEquals(0, reid.addCameraShot(mimi.getId(), JPEG), "hết phiên thì không nhận thêm ảnh");
        assertEquals(3, saved.size());
        assertTrue(saved.stream().allMatch(p -> PetReidPhoto.SOURCE_CAMERA.equals(p.getSource())));
        @SuppressWarnings("unchecked")
        Map<String, Object> cap = (Map<String, Object>) reid.status(mimi.getId(), actor).get("capture");
        assertEquals("done", cap.get("lastResult"));
    }

    @Test
    void shotForOtherPetIgnored() {
        reid.startCapture(mimi.getId(), 2, actor);
        assertEquals(0, reid.addCameraShot(UUID.randomUUID(), JPEG));
        assertTrue(saved.isEmpty());
    }

    @Test
    void galleryVersionChangesWithPhotos() {
        String v0 = (String) reid.gallery().get("version");
        reid.addUpload(mimi.getId(), JPEG, actor);
        Map<String, Object> g = reid.gallery();
        assertNotEquals(v0, g.get("version"));
        List<?> list = (List<?>) g.get("pets");
        assertEquals(1, list.size());
        assertEquals("Mimi", ((Map<?, ?>) list.get(0)).get("name"));
    }

    @Test
    void reportMarksLearned() {
        reid.addUpload(mimi.getId(), JPEG, actor);
        UUID id = saved.get(0).getId();
        reid.report(true, "dinov2_vits14", List.of("Mimi"), Map.of(id.toString(), true, "not-a-uuid", false));
        verify(photos).updateUsable(id, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> cam = (Map<String, Object>) reid.status(mimi.getId(), actor).get("camera");
        assertEquals(true, cam.get("learned"));
        assertEquals(true, cam.get("ready"));
    }

    @Test
    void otherOwnerCannotStartCapture() {
        PetService ps = mock(PetService.class);
        when(ps.requireAccessible(any(), any())).thenThrow(new AccessDeniedException("x"));
        ReidService r = new ReidService(photos, pets, ps);
        assertThrows(AccessDeniedException.class, () -> r.startCapture(mimi.getId(), 5, new Actor(UUID.randomUUID(), false)));
    }
}
