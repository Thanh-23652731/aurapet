package com.aurapet.backend.infrastructure.persistence;

import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;
    private final AlertRepository alertRepository;
    private final SystemStatusRepository systemStatusRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already initialized.");
            return;
        }

        log.info("Initializing demo data for AuraPet ecosystem...");

        // 1. Users
        User admin = userRepository.save(User.builder()
                .name("Alex Johnson (Admin)")
                .email("admin@aurapet.com")
                .password(passwordEncoder.encode("Admin@123"))
                .role(Role.ROLE_ADMIN)
                .build());

        User user = userRepository.save(User.builder()
                .name("Sarah Davis")
                .email("user@aurapet.com")
                .password(passwordEncoder.encode("User@123"))
                .role(Role.ROLE_USER)
                .build());

        // 2. Pets
        Pet luna = petRepository.save(Pet.builder()
                .ownerId(admin.getId())
                .name("Luna")
                .breed("British Shorthair")
                .age(2)
                .gender(Gender.FEMALE)
                .neutered(true)
                .weight(4.25)
                .imageUrl("https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?auto=format&fit=crop&w=600&q=80")
                .build());

        Pet milo = petRepository.save(Pet.builder()
                .ownerId(admin.getId())
                .name("Milo")
                .breed("Scottish Fold")
                .age(1)
                .gender(Gender.MALE)
                .neutered(true)
                .weight(3.80)
                .imageUrl("https://images.unsplash.com/photo-1573865526739-10659fec78a5?auto=format&fit=crop&w=600&q=80")
                .build());

        Pet oliver = petRepository.save(Pet.builder()
                .ownerId(user.getId())
                .name("Oliver")
                .breed("Maine Coon")
                .age(3)
                .gender(Gender.MALE)
                .neutered(true)
                .weight(7.65)
                .imageUrl("https://images.unsplash.com/photo-1533738363-b7f9aef128ce?auto=format&fit=crop&w=600&q=80")
                .build());

        // 3. Weight History
        Instant now = Instant.now();
        for (int i = 5; i >= 0; i--) {
            Instant time = now.minus(i * 7, ChronoUnit.DAYS);
            weightRecordRepository.save(WeightRecord.builder()
                    .petId(luna.getId())
                    .weight(4.10 + (5 - i) * 0.03)
                    .recordedAt(time)
                    .build());
            weightRecordRepository.save(WeightRecord.builder()
                    .petId(milo.getId())
                    .weight(3.60 + (5 - i) * 0.04)
                    .recordedAt(time)
                    .build());
            weightRecordRepository.save(WeightRecord.builder()
                    .petId(oliver.getId())
                    .weight(7.45 + (5 - i) * 0.04)
                    .recordedAt(time)
                    .build());
        }

        // 4. Feeding Logs (Today & Past Days)
        feedingLogRepository.save(FeedingLog.builder().petId(luna.getId()).foodGram(45).timestamp(now.minus(4, ChronoUnit.HOURS)).build());
        feedingLogRepository.save(FeedingLog.builder().petId(luna.getId()).foodGram(40).timestamp(now.minus(1, ChronoUnit.HOURS)).build());
        feedingLogRepository.save(FeedingLog.builder().petId(milo.getId()).foodGram(35).timestamp(now.minus(5, ChronoUnit.HOURS)).build());
        feedingLogRepository.save(FeedingLog.builder().petId(milo.getId()).foodGram(35).timestamp(now.minus(2, ChronoUnit.HOURS)).build());
        feedingLogRepository.save(FeedingLog.builder().petId(oliver.getId()).foodGram(85).timestamp(now.minus(3, ChronoUnit.HOURS)).build());

        for (int day = 1; day <= 6; day++) {
            Instant dayTime = now.minus(day, ChronoUnit.DAYS);
            feedingLogRepository.save(FeedingLog.builder().petId(luna.getId()).foodGram(45).timestamp(dayTime.minus(8, ChronoUnit.HOURS)).build());
            feedingLogRepository.save(FeedingLog.builder().petId(luna.getId()).foodGram(45).timestamp(dayTime.minus(1, ChronoUnit.HOURS)).build());
            feedingLogRepository.save(FeedingLog.builder().petId(milo.getId()).foodGram(40).timestamp(dayTime.minus(7, ChronoUnit.HOURS)).build());
            feedingLogRepository.save(FeedingLog.builder().petId(oliver.getId()).foodGram(80).timestamp(dayTime.minus(6, ChronoUnit.HOURS)).build());
        }

        // 5. Litter Logs
        litterLogRepository.save(LitterLog.builder().petId(luna.getId()).result(LitterResult.NORMAL).imageUrl("https://images.unsplash.com/photo-1574158622682-e40e69881006?auto=format&fit=crop&w=400&q=80").timestamp(now.minus(6, ChronoUnit.HOURS)).build());
        litterLogRepository.save(LitterLog.builder().petId(luna.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(now.minus(2, ChronoUnit.HOURS)).build());
        litterLogRepository.save(LitterLog.builder().petId(milo.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(now.minus(5, ChronoUnit.HOURS)).build());
        litterLogRepository.save(LitterLog.builder().petId(oliver.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(now.minus(3, ChronoUnit.HOURS)).build());

        for (int day = 1; day <= 6; day++) {
            Instant dayTime = now.minus(day, ChronoUnit.DAYS);
            litterLogRepository.save(LitterLog.builder().petId(luna.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(dayTime.minus(5, ChronoUnit.HOURS)).build());
            litterLogRepository.save(LitterLog.builder().petId(luna.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(dayTime.minus(1, ChronoUnit.HOURS)).build());
            litterLogRepository.save(LitterLog.builder().petId(milo.getId()).result(LitterResult.NORMAL).imageUrl(null).timestamp(dayTime.minus(4, ChronoUnit.HOURS)).build());
        }

        // 6. Alerts
        alertRepository.save(Alert.builder()
                .type(AlertType.FEEDING)
                .level(AlertLevel.INFO)
                .message("Luna consumed 45g of dry food from Smart Feeder #1")
                .createdAt(now.minus(4, ChronoUnit.HOURS))
                .build());

        alertRepository.save(Alert.builder()
                .type(AlertType.LITTER)
                .level(AlertLevel.INFO)
                .message("Auto litter cleaning cycle completed successfully. Zero clumping anomalies detected.")
                .createdAt(now.minus(2, ChronoUnit.HOURS))
                .build());

        alertRepository.save(Alert.builder()
                .type(AlertType.HEALTH)
                .level(AlertLevel.INFO)
                .message("Edge AI Health Score for Luna calculated at 96/100 (Optimal).")
                .createdAt(now.minus(1, ChronoUnit.HOURS))
                .build());

        alertRepository.save(Alert.builder()
                .type(AlertType.SYSTEM)
                .level(AlertLevel.INFO)
                .message("ESP32 telemetry heartbeat normal (Ping: 14ms, Signal: -52dBm)")
                .createdAt(now.minus(15, ChronoUnit.MINUTES))
                .build());

        // 7. SystemStatus
        systemStatusRepository.save(SystemStatus.builder()
                .feeder(true)
                .waterPump(true)
                .litterCleaner(false)
                .esp32("ONLINE")
                .raspberryPi("ONLINE")
                .foodLevelPercent(78)
                .waterLevelPercent(88)
                .litterWastePercent(24)
                .build());

        log.info("Demo data initialized successfully: 2 users, 3 pets, logs, and telemetry ready.");
    }
}
