package com.aurapet.backend.infrastructure.persistence;

import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Tạo dữ liệu mẫu khi CSDL còn trống (tắt bằng AURAPET_DEMO_DATA=false).
 * Tài khoản: admin@aurapet.com / Admin@123, user@aurapet.com / User@123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;
    private final AlertRepository alertRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${aurapet.demo-data:true}")
    private boolean demoData;

    @Override
    public void run(String... args) {
        if (!demoData || userRepository.count() > 0) {
            return;
        }
        log.info("Tạo dữ liệu mẫu AuraPet...");

        User admin = userRepository.save(User.builder()
                .name("Quản trị viên")
                .email("admin@aurapet.com")
                .password(passwordEncoder.encode("Admin@123"))
                .role(Role.ROLE_ADMIN)
                .build());
        User user = userRepository.save(User.builder()
                .name("Người dùng demo")
                .email("user@aurapet.com")
                .password(passwordEncoder.encode("User@123"))
                .role(Role.ROLE_USER)
                .build());

        Pet luna = petRepository.save(Pet.builder()
                .ownerId(admin.getId()).name("Luna").breed("Anh lông ngắn").age(2)
                .gender(Gender.FEMALE).neutered(true).weight(4.25)
                .imageUrl("https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=400&q=70")
                .build());
        Pet milo = petRepository.save(Pet.builder()
                .ownerId(admin.getId()).name("Milo").breed("Scottish Fold").age(1)
                .gender(Gender.MALE).neutered(true).weight(3.80)
                .imageUrl("https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=400&q=70")
                .build());
        Pet oliver = petRepository.save(Pet.builder()
                .ownerId(user.getId()).name("Oliver").breed("Maine Coon").age(3)
                .gender(Gender.MALE).neutered(true).weight(7.65)
                .imageUrl("https://images.unsplash.com/photo-1533738363-b7f9aef128ce?auto=format&fit=crop&w=400&q=70")
                .build());

        Instant now = Instant.now();
        for (int i = 5; i >= 0; i--) {
            Instant time = now.minus(i * 7L, ChronoUnit.DAYS);
            weightRecordRepository.save(WeightRecord.builder().petId(luna.getId()).weight(4.10 + (5 - i) * 0.03).recordedAt(time).build());
            weightRecordRepository.save(WeightRecord.builder().petId(milo.getId()).weight(3.60 + (5 - i) * 0.04).recordedAt(time).build());
            weightRecordRepository.save(WeightRecord.builder().petId(oliver.getId()).weight(7.45 + (5 - i) * 0.04).recordedAt(time).build());
        }

        for (int day = 1; day <= 6; day++) {
            Instant d = now.minus(day, ChronoUnit.DAYS);
            feed(luna, 30, d.minus(9, ChronoUnit.HOURS));
            feed(luna, 30, d.minus(3, ChronoUnit.HOURS));
            feed(milo, 28, d.minus(8, ChronoUnit.HOURS));
            feed(oliver, 55, d.minus(6, ChronoUnit.HOURS));
            litter(luna, d.minus(5, ChronoUnit.HOURS));
            litter(luna, d.minus(1, ChronoUnit.HOURS));
            litter(milo, d.minus(4, ChronoUnit.HOURS));
        }

        alertRepository.save(Alert.builder().type(AlertType.SYSTEM).level(AlertLevel.INFO)
                .message("Hệ thống AuraPet đã khởi tạo dữ liệu mẫu").createdAt(now).build());

        log.info("Đã tạo dữ liệu mẫu: 2 tài khoản, 3 thú cưng.");
    }

    private void feed(Pet pet, int gram, Instant at) {
        feedingLogRepository.save(FeedingLog.builder().petId(pet.getId()).foodGram(gram).source("Dữ liệu mẫu").timestamp(at).build());
    }

    private void litter(Pet pet, Instant at) {
        litterLogRepository.save(LitterLog.builder().petId(pet.getId()).result(LitterResult.NORMAL).timestamp(at).build());
    }
}
