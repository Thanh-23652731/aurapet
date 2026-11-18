package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.model.PetReidPhoto;
import com.aurapet.backend.domain.model.PetReidPhotoLimits;
import com.aurapet.backend.domain.repository.PetReidPhotoRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Đăng ký từng bé cho camera (Re-ID bằng Feature Embedding).
 *
 * Hai cách thêm ảnh mẫu:
 *   1) Chủ tải 5-10 ảnh của bé từ web.
 *   2) "Đưa mèo ra trước camera": web bật một phiên chụp; Pi thấy phiên này trong phản hồi của lần gửi khung hình
 *      kế tiếp (header X-Enroll-Pet), tự cắt con mèo từ khung hình gốc (không có khung vẽ) rồi gửi từng ảnh lên.
 * Pi định kỳ hỏi danh sách ảnh (version thay đổi thì tải ảnh mới), tính vector đặc trưng và báo lại ảnh nào dùng được.
 */
@Slf4j
@Service
public class ReidService {

    /** Phiên chụp tự huỷ nếu sau chừng này không xong (bé bỏ đi, camera tắt). */
    static final Duration CAPTURE_TIMEOUT = Duration.ofSeconds(120);
    static final int MAX_CAPTURE = 20;

    private final PetReidPhotoRepository photos;
    private final PetRepository pets;
    private final PetService petService;

    /** Phiên chụp đang chạy (mỗi lúc chỉ một, vì chỉ có một camera). */
    public record Capture(UUID petId, String petName, int target, int taken, Instant startedAt, Instant lastShotAt) {
        boolean expired() {
            Instant ref = lastShotAt != null ? lastShotAt : startedAt;
            return Duration.between(ref, Instant.now()).compareTo(CAPTURE_TIMEOUT) > 0;
        }

        public int left() {
            return Math.max(0, target - taken);
        }
    }

    private Capture capture;
    /** Kết quả lần chụp gần nhất cho từng bé (để web báo "đã xong" / "hết giờ"). */
    private final Map<UUID, String> lastCaptureResult = new HashMap<>();
    private volatile Instant piSyncAt;
    private volatile boolean piReady;
    private volatile String piModel;
    private volatile Set<String> piPets = Set.of();

    public ReidService(PetReidPhotoRepository photos, PetRepository pets, PetService petService) {
        this.photos = photos;
        this.pets = pets;
        this.petService = petService;
    }

    // ------------------------------------------------------------------ web (chủ nuôi)

    public Map<String, Object> status(UUID petId, Actor actor) {
        Pet pet = petService.requireAccessible(petId, actor);
        List<Map<String, Object>> list = new ArrayList<>();
        int usable = 0;
        int rejected = 0;
        for (PetReidPhotoRepository.Info i : photos.findInfoByPetId(petId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id());
            m.put("source", i.source());
            m.put("usable", i.usable());
            m.put("createdAt", i.createdAt());
            list.add(m);
            if (Boolean.TRUE.equals(i.usable())) usable++;
            if (Boolean.FALSE.equals(i.usable())) rejected++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("photos", list);
        m.put("count", list.size());
        m.put("usable", usable);
        m.put("rejected", rejected);
        m.put("minRecommended", PetReidPhotoLimits.MIN_RECOMMENDED);
        m.put("max", PetReidPhotoLimits.MAX_PER_PET);
        Capture c = currentCapture();
        if (c != null && c.petId().equals(petId)) {
            m.put("capture", Map.of("active", true, "target", c.target(), "taken", c.taken(), "startedAt", c.startedAt()));
        } else {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("active", false);
            cm.put("busyWith", c != null ? c.petName() : null);
            synchronized (this) {
                cm.put("lastResult", lastCaptureResult.get(petId));
            }
            m.put("capture", cm);
        }
        Map<String, Object> pi = new LinkedHashMap<>();
        pi.put("lastSyncAt", piSyncAt);
        pi.put("ready", piReady);
        pi.put("model", piModel);
        pi.put("learned", pet.getName() != null && piPets.contains(pet.getName().toLowerCase(Locale.ROOT)));
        m.put("camera", pi);
        return m;
    }

    @Transactional
    public Map<String, Object> addUpload(UUID petId, byte[] jpeg, Actor actor) {
        petService.requireAccessible(petId, actor);
        save(petId, jpeg, PetReidPhoto.SOURCE_UPLOAD);
        return status(petId, actor);
    }

    @Transactional(readOnly = true)
    public byte[] photo(UUID petId, UUID photoId, Actor actor) {
        petService.requireAccessible(petId, actor);
        PetReidPhoto p = photos.findById(photoId).filter(x -> x.getPetId().equals(petId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh"));
        return p.getJpeg();
    }

    @Transactional
    public Map<String, Object> deletePhoto(UUID petId, UUID photoId, Actor actor) {
        petService.requireAccessible(petId, actor);
        PetReidPhoto p = photos.findById(photoId).filter(x -> x.getPetId().equals(petId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh"));
        photos.delete(p);
        return status(petId, actor);
    }

    @Transactional
    public Map<String, Object> clear(UUID petId, Actor actor) {
        petService.requireAccessible(petId, actor);
        photos.deleteByPetId(petId);
        return status(petId, actor);
    }

    public Map<String, Object> startCapture(UUID petId, int count, Actor actor) {
        Pet pet = petService.requireAccessible(petId, actor);
        int room = PetReidPhotoLimits.MAX_PER_PET - (int) photos.countByPetId(petId);
        if (room <= 0) {
            throw new BadRequestException("Bé đã có đủ " + PetReidPhotoLimits.MAX_PER_PET + " ảnh mẫu. Xoá bớt ảnh cũ trước khi chụp thêm.");
        }
        int n = Math.max(1, Math.min(Math.min(count, MAX_CAPTURE), room));
        synchronized (this) {
            Capture c = currentCapture();
            if (c != null && !c.petId().equals(petId)) {
                throw new BadRequestException("Camera đang chụp cho " + c.petName() + ". Chờ xong hoặc huỷ phiên đó trước.");
            }
            capture = new Capture(petId, pet.getName(), n, 0, Instant.now(), null);
            lastCaptureResult.remove(petId);
        }
        log.info("[Re-ID] Bắt đầu chụp {} ảnh cho {}", n, pet.getName());
        return status(petId, actor);
    }

    public Map<String, Object> cancelCapture(UUID petId, Actor actor) {
        petService.requireAccessible(petId, actor);
        synchronized (this) {
            if (capture != null && capture.petId().equals(petId)) {
                lastCaptureResult.put(petId, "cancelled");
                capture = null;
            }
        }
        return status(petId, actor);
    }

    // ------------------------------------------------------------------ Raspberry Pi (X-Camera-Key)

    /** Phiên chụp đang chờ Pi (null = không có). Hết giờ thì tự huỷ. */
    public synchronized Capture currentCapture() {
        if (capture != null && capture.expired()) {
            log.info("[Re-ID] Hết giờ chụp cho {} ({}/{} ảnh)", capture.petName(), capture.taken(), capture.target());
            lastCaptureResult.put(capture.petId(), capture.taken() > 0 ? "partial" : "timeout");
            capture = null;
        }
        return capture;
    }

    /** Pi gửi một ảnh đã cắt sẵn con mèo trong phiên chụp. Trả về số ảnh còn phải chụp (0 = xong / không có phiên). */
    @Transactional
    public int addCameraShot(UUID petId, byte[] jpeg) {
        synchronized (this) {
            Capture c = currentCapture();
            if (c == null || !c.petId().equals(petId)) return 0;
            if (!pets.existsById(petId)) {
                capture = null;
                return 0;
            }
            save(petId, jpeg, PetReidPhoto.SOURCE_CAMERA);
            int taken = c.taken() + 1;
            if (taken >= c.target()) {
                capture = null;
                lastCaptureResult.put(petId, "done");
                log.info("[Re-ID] Đã chụp xong {} ảnh cho {}", taken, c.petName());
                return 0;
            }
            capture = new Capture(c.petId(), c.petName(), c.target(), taken, c.startedAt(), Instant.now());
            return c.target() - taken;
        }
    }

    /**
     * Danh sách ảnh mẫu cho Pi: {"version":"...", "pets":[{"petId","name","photos":[{"id","source"}]}]}.
     * version đổi khi thêm/xoá ảnh hoặc đổi tên bé -> Pi mới tải lại.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> gallery() {
        Map<UUID, Pet> byId = new HashMap<>();
        for (Pet p : pets.findAll()) byId.put(p.getId(), p);
        Map<UUID, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        StringBuilder sig = new StringBuilder();
        for (PetReidPhotoRepository.Info i : photos.findAllInfo()) {
            Pet p = byId.get(i.petId());
            if (p == null) continue;
            grouped.computeIfAbsent(i.petId(), k -> new ArrayList<>()).add(Map.of("id", i.id(), "source", i.source()));
            sig.append(i.id()).append(';');
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<UUID, List<Map<String, Object>>> e : grouped.entrySet()) {
            Pet p = byId.get(e.getKey());
            sig.append(p.getId()).append('=').append(p.getName()).append(';');
            list.add(Map.of("petId", p.getId(), "name", p.getName(), "photos", e.getValue()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", sha1(sig.toString()));
        m.put("pets", list);
        return m;
    }

    @Transactional(readOnly = true)
    public byte[] photoForCamera(UUID photoId) {
        return photos.findById(photoId).map(PetReidPhoto::getJpeg)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh"));
    }

    /** Pi báo kết quả học: {"ready":true,"model":"dinov2_vits14","pets":["Mimi"],"photos":{"<id>":true,"<id>":false}}. */
    @Transactional
    public void report(boolean ready, String model, Collection<String> learnedPets, Map<String, Boolean> result) {
        piSyncAt = Instant.now();
        piReady = ready;
        piModel = model;
        Set<String> names = new HashSet<>();
        if (learnedPets != null) for (String n : learnedPets) if (n != null) names.add(n.toLowerCase(Locale.ROOT));
        piPets = names;
        if (result == null) return;
        for (Map.Entry<String, Boolean> e : result.entrySet()) {
            try {
                photos.updateUsable(UUID.fromString(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) {
                // id không hợp lệ: bỏ qua
            }
        }
    }

    // ------------------------------------------------------------------

    private void save(UUID petId, byte[] jpeg, String source) {
        if (jpeg == null || jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            throw new BadRequestException("Ảnh phải là JPEG");
        }
        if (jpeg.length > PetReidPhotoLimits.MAX_BYTES) {
            throw new BadRequestException("Ảnh quá lớn (tối đa 1 MB)");
        }
        if (photos.countByPetId(petId) >= PetReidPhotoLimits.MAX_PER_PET) {
            throw new BadRequestException("Mỗi bé tối đa " + PetReidPhotoLimits.MAX_PER_PET + " ảnh mẫu");
        }
        photos.save(PetReidPhoto.builder().petId(petId).jpeg(jpeg).source(source).build());
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 10);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
