package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.DeviceSettingsDto;
import com.aurapet.backend.application.dto.UpdateDeviceSettingsRequest;
import com.aurapet.backend.domain.model.DeviceSettings;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.DeviceSettingsRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Cài đặt máy cho ăn (1 dòng duy nhất trong bảng device_settings), dùng chung cho
 * cho ăn, lịch hẹn giờ, camera và khay cát. Mọi thay đổi đi qua {@link #update} để
 * các luồng MQTT và REST không ghi đè lên nhau.
 */
@Service
public class DeviceSettingsService {

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final DeviceSettingsRepository settingsRepository;
    private final PetRepository petRepository;

    private volatile DeviceSettings cached;

    /** Những gì đã đổi và cần gửi xuống ESP32. */
    public record Change(boolean targetChanged, boolean autoChanged) {
    }

    public DeviceSettingsService(DeviceSettingsRepository settingsRepository, PetRepository petRepository) {
        this.settingsRepository = settingsRepository;
        this.petRepository = petRepository;
    }

    public DeviceSettings get() {
        DeviceSettings s = cached;
        if (s == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = settingsRepository.findById(DeviceSettings.SINGLETON_ID)
                            .orElseGet(() -> settingsRepository.save(defaultSettings()));
                }
                s = cached;
            }
        }
        return s;
    }

    public synchronized DeviceSettings update(Consumer<DeviceSettings> change) {
        DeviceSettings s = get();
        change.accept(s);
        cached = settingsRepository.save(s);
        return cached;
    }

    /** Kiểm tra quyền và dữ liệu TRƯỚC khi sửa, để yêu cầu sai không làm đổi một phần cài đặt. */
    public synchronized Change apply(UpdateDeviceSettingsRequest r, Actor actor) {
        if (r.getFeederPetId() != null) {
            Pet pet = petRepository.findById(r.getFeederPetId())
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy thú cưng được chọn cho máy cho ăn"));
            if (!actor.admin() && !pet.getOwnerId().equals(actor.id())) {
                throw new AccessDeniedException("Bạn chỉ có thể gán thú cưng của mình cho máy cho ăn");
            }
        }
        List<String> times = r.getScheduleTimes() != null ? normalizeTimes(r.getScheduleTimes()) : null;

        update(s -> {
            if (r.getTargetGram() != null) s.setTargetGram(FeederService.clampGram(r.getTargetGram()));
            if (r.getAutoMode() != null) s.setAutoMode(r.getAutoMode());
            if (r.getScheduleEnabled() != null) s.setScheduleEnabled(r.getScheduleEnabled());
            if (times != null) s.setScheduleTimes(String.join(",", times));
            if (r.getFeederPetId() != null) s.setFeederPetId(r.getFeederPetId());
        });
        return new Change(r.getTargetGram() != null, r.getAutoMode() != null);
    }

    /** Mèo được gán cho máy; nếu chưa gán (hoặc đã bị xoá) thì lấy con được tạo sớm nhất. */
    public Pet resolveFeederPet() {
        UUID id = get().getFeederPetId();
        if (id != null) {
            Optional<Pet> p = petRepository.findById(id);
            if (p.isPresent()) return p.get();
        }
        List<Pet> all = petRepository.findAllByOrderByCreatedAtAsc();
        return all.isEmpty() ? null : all.get(0);
    }

    public DeviceSettingsDto toDto() {
        DeviceSettings s = get();
        Pet pet = resolveFeederPet();
        return DeviceSettingsDto.builder()
                .targetGram(s.getTargetGram())
                .autoMode(s.getAutoMode())
                .scheduleEnabled(s.getScheduleEnabled())
                .scheduleTimes(splitTimes(s.getScheduleTimes()))
                .feederPetId(pet != null ? pet.getId() : null)
                .feederPetName(pet != null ? pet.getName() : null)
                .build();
    }

    static DeviceSettings defaultSettings() {
        return DeviceSettings.builder()
                .id(DeviceSettings.SINGLETON_ID)
                .targetGram(30)
                .autoMode(false)
                .scheduleEnabled(false)
                .scheduleTimes("07:00,12:00,18:00")
                .build();
    }

    static List<String> normalizeTimes(List<String> times) {
        TreeSet<String> out = new TreeSet<>();
        for (String raw : times) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.length() == 4 && t.charAt(1) == ':') t = "0" + t; // 7:00 -> 07:00
            if (!TIME_PATTERN.matcher(t).matches()) {
                throw new BadRequestException("Giờ không hợp lệ: \"" + raw + "\" (định dạng HH:mm, ví dụ 07:30)");
            }
            out.add(t);
        }
        return new ArrayList<>(out);
    }

    static List<String> splitTimes(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
