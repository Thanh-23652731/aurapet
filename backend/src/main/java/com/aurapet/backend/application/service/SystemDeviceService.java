package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.domain.repository.SystemStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemDeviceService {

    private final SystemStatusRepository systemStatusRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final AlertRepository alertRepository;
    private final PetRepository petRepository;
    private final com.aurapet.backend.domain.repository.SystemFoodRepository systemFoodRepository;
    private final com.aurapet.backend.domain.repository.SystemWaterRepository systemWaterRepository;

    @Transactional
    public SystemStatus getOrCreateStatus() {
        return systemStatusRepository.findFirstByOrderByLastUpdatedDesc()
                .orElseGet(() -> systemStatusRepository.save(
                        SystemStatus.builder()
                                .feeder(true)
                                .waterPump(true)
                                .litterCleaner(false)
                                .esp32("ONLINE")
                                .raspberryPi("ONLINE")
                                .foodLevelPercent(85)
                                .waterLevelPercent(90)
                                .litterWastePercent(18)
                                .build()
                ));
    }

    @Transactional(readOnly = true)
    public SystemStatusDto getSystemStatus() {
        return mapToDto(getOrCreateStatus());
    }

    @Transactional(readOnly = true)
    public List<FeedingLogDto> getFeedingHistory() {
        List<FeedingLog> logs = feedingLogRepository.findAllByOrderByTimestampDesc();
        Map<UUID, String> petNameMap = petRepository.findAll().stream()
                .collect(Collectors.toMap(Pet::getId, Pet::getName, (a, b) -> a));
        return logs.stream().map(log -> FeedingLogDto.builder()
                .id(log.getId())
                .petId(log.getPetId())
                .petName(petNameMap.getOrDefault(log.getPetId(), "Unknown Pet"))
                .foodGram(log.getFoodGram())
                .timestamp(log.getTimestamp())
                .build()
        ).toList();
    }

    @Transactional
    public SystemStatusDto controlFeeder(FeederCommandRequest request) {
        SystemStatus status = getOrCreateStatus();

        // Simulate portion decrement
        int currentFood = status.getFoodLevelPercent() != null ? status.getFoodLevelPercent() : 85;
        int newFood = Math.max(0, currentFood - (request.getPortionGram() / 10));
        status.setFoodLevelPercent(newFood);
        status.setFeeder(true);
        systemStatusRepository.save(status);

        // Determine pet or default to first pet
        UUID targetPetId = request.getPetId();
        if (targetPetId == null) {
            List<Pet> pets = petRepository.findAll();
            if (!pets.isEmpty()) {
                targetPetId = pets.get(0).getId();
            }
        }

        if (targetPetId != null) {
            feedingLogRepository.save(
                    FeedingLog.builder()
                            .petId(targetPetId)
                            .foodGram(request.getPortionGram())
                            .timestamp(Instant.now())
                            .build()
            );

            petRepository.findById(targetPetId).ifPresent(pet -> {
                alertRepository.save(
                        Alert.builder()
                                .type(AlertType.FEEDING)
                                .level(AlertLevel.INFO)
                                .message("Dispensed " + request.getPortionGram() + "g of food for " + pet.getName())
                                .createdAt(Instant.now())
                                .build()
                );
            });
        }

        log.info("Feeder dispensed {}g of kibble", request.getPortionGram());
        return mapToDto(status);
    }

    @Transactional
    public SystemStatusDto controlWater(WaterCommandRequest request) {
        SystemStatus status = getOrCreateStatus();
        status.setWaterPump(request.getEnabled());
        systemStatusRepository.save(status);

        alertRepository.save(
                Alert.builder()
                        .type(AlertType.SYSTEM)
                        .level(AlertLevel.INFO)
                        .message("Circulating water fountain pump " + (request.getEnabled() ? "ENABLED" : "PAUSED"))
                        .createdAt(Instant.now())
                        .build()
        );

        log.info("Water fountain pump set to {}", request.getEnabled());
        return mapToDto(status);
    }

    @Transactional
    public SystemStatusDto controlLitter(LitterCommandRequest request) {
        SystemStatus status = getOrCreateStatus();
        
        if ("CLEAN".equalsIgnoreCase(request.getAction())) {
            status.setLitterCleaner(true);
            status.setLitterWastePercent(0); // Emptied / cleaned
            alertRepository.save(
                    Alert.builder()
                            .type(AlertType.LITTER)
                            .level(AlertLevel.INFO)
                            .message("Auto litter cleaning cycle initiated and completed successfully")
                            .createdAt(Instant.now())
                            .build()
            );
        } else if ("PAUSE".equalsIgnoreCase(request.getAction())) {
            status.setLitterCleaner(false);
        } else if ("RESET".equalsIgnoreCase(request.getAction())) {
            status.setLitterCleaner(false);
            status.setLitterWastePercent(5);
        }

        systemStatusRepository.save(status);
        log.info("Litter box command executed: {}", request.getAction());
        return mapToDto(status);
    }

    public SystemStatusDto mapToDto(SystemStatus status) {
        return SystemStatusDto.builder()
                .feeder(status.getFeeder())
                .waterPump(status.getWaterPump())
                .litterCleaner(status.getLitterCleaner())
                .esp32(status.getEsp32())
                .raspberryPi(status.getRaspberryPi())
                .foodLevelPercent(status.getFoodLevelPercent())
                .waterLevelPercent(status.getWaterLevelPercent())
                .litterWastePercent(status.getLitterWastePercent())
                .build();
    }

    @Transactional
    public SystemFoodDto getFoodStatus() {
        com.aurapet.backend.domain.model.SystemFood food = systemFoodRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> systemFoodRepository.save(
                        com.aurapet.backend.domain.model.SystemFood.builder()
                                .id(UUID.randomUUID().toString())
                                .deviceId("FOOD_DISPENSER_ESP32")
                                .autoMode(true)
                                .btnLed(false)
                                .ledGreen(true)
                                .ledRed(false)
                                .machines(false)
                                .percel(45)
                                .calories(160)
                                .foodLevelPercent(85)
                                .lastDispensed(Instant.now())
                                .build()
                ));
        return mapFoodToDto(food);
    }

    @Transactional
    public SystemFoodDto updateFoodStatus(FoodControlCommand command) {
        com.aurapet.backend.domain.model.SystemFood food = systemFoodRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> com.aurapet.backend.domain.model.SystemFood.builder()
                        .id(UUID.randomUUID().toString())
                        .deviceId("FOOD_DISPENSER_ESP32")
                        .build());

        if (command.getAuto() != null) food.setAutoMode(command.getAuto());
        if (command.getBtnLed() != null) food.setBtnLed(command.getBtnLed());
        if (command.getLedGreen() != null) food.setLedGreen(command.getLedGreen());
        if (command.getLedRed() != null) food.setLedRed(command.getLedRed());
        if (command.getPercel() != null) food.setPercel(command.getPercel());
        if (command.getCalories() != null) food.setCalories(command.getCalories());
        if (command.getFoodLevelPercent() != null) food.setFoodLevelPercent(command.getFoodLevelPercent());

        if (command.getScaleWeightGram() != null) food.setScaleWeightGram(command.getScaleWeightGram());
        if (command.getCatDetected() != null) food.setCatDetected(command.getCatDetected());
        if (command.getScheduleTimes() != null) food.setScheduleTimes(command.getScheduleTimes());

        if (command.getMachines() != null) {
            food.setMachines(command.getMachines());
            if (Boolean.TRUE.equals(command.getMachines())) {
                food.setLastDispensed(Instant.now());
                int currentLvl = food.getFoodLevelPercent() != null ? food.getFoodLevelPercent() : 85;
                int drop = Math.max(1, (food.getPercel() != null ? food.getPercel() : 45) / 10);
                food.setFoodLevelPercent(Math.max(0, currentLvl - drop));

                // Record feeding log
                List<Pet> pets = petRepository.findAll();
                UUID targetPetId = pets.isEmpty() ? null : pets.get(0).getId();
                if (targetPetId != null) {
                    feedingLogRepository.save(
                            FeedingLog.builder()
                                    .petId(targetPetId)
                                    .foodGram(food.getPercel() != null ? food.getPercel() : 45)
                                    .timestamp(Instant.now())
                                    .build()
                    );
                }
            }
        }

        systemFoodRepository.save(food);
        log.info("System Food updated: auto={}, btnLed={}, ledGreen={}, ledRed={}, machines={}, percel={}, cal={}, scaleWeight={}, catDetected={}",
                food.getAutoMode(), food.getBtnLed(), food.getLedGreen(), food.getLedRed(), food.getMachines(), food.getPercel(), food.getCalories(),
                food.getScaleWeightGram(), food.getCatDetected());
        return mapFoodToDto(food);
    }

    @Transactional
    public SystemWaterDto getWaterStatus() {
        com.aurapet.backend.domain.model.SystemWater water = systemWaterRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> systemWaterRepository.save(
                        com.aurapet.backend.domain.model.SystemWater.builder()
                                .id(UUID.randomUUID().toString())
                                .deviceId("WATER_PUMP_ESP32")
                                .autoMode(true)
                                .btnLed(false)
                                .ledGreen(true)
                                .ledRed(false)
                                .machines(true)
                                .percel(150)
                                .waterLevelPercent(90)
                                .catDetected(false)
                                .lastPumped(Instant.now())
                                .build()
                ));
        return mapWaterToDto(water);
    }

    @Transactional
    public SystemWaterDto updateWaterStatus(WaterControlCommand command) {
        com.aurapet.backend.domain.model.SystemWater water = systemWaterRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> com.aurapet.backend.domain.model.SystemWater.builder()
                        .id(UUID.randomUUID().toString())
                        .deviceId("WATER_PUMP_ESP32")
                        .build());

        if (command.getAuto() != null) water.setAutoMode(command.getAuto());
        if (command.getBtnLed() != null) water.setBtnLed(command.getBtnLed());
        if (command.getLedGreen() != null) water.setLedGreen(command.getLedGreen());
        if (command.getLedRed() != null) water.setLedRed(command.getLedRed());
        if (command.getPercel() != null) water.setPercel(command.getPercel());
        if (command.getWaterLevelPercent() != null) water.setWaterLevelPercent(command.getWaterLevelPercent());
        if (command.getCatDetected() != null) water.setCatDetected(command.getCatDetected());

        if (command.getMachines() != null) {
            water.setMachines(command.getMachines());
            if (Boolean.TRUE.equals(command.getMachines())) {
                water.setLastPumped(Instant.now());
            }
        }

        systemWaterRepository.save(water);
        log.info("System Water updated: auto={}, btnLed={}, ledGreen={}, ledRed={}, machines={}, percel={}, catDetected={}",
                water.getAutoMode(), water.getBtnLed(), water.getLedGreen(), water.getLedRed(), water.getMachines(), water.getPercel(), water.getCatDetected());
        return mapWaterToDto(water);
    }

    @Transactional
    public SystemFoodDto processFoodCycle(Boolean catDetected, Integer scaleWeightGram, Boolean manualButton) {
        com.aurapet.backend.domain.model.SystemFood food = systemFoodRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> com.aurapet.backend.domain.model.SystemFood.builder()
                        .id(UUID.randomUUID().toString())
                        .deviceId("FOOD_DISPENSER_ESP32")
                        .autoMode(true)
                        .scaleWeightGram(5)
                        .catDetected(false)
                        .build());

        if (catDetected != null) food.setCatDetected(catDetected);
        if (scaleWeightGram != null) food.setScaleWeightGram(scaleWeightGram);

        boolean turnOnStepMotor = false;

        // Flowchart Hinh 6 logic
        if (Boolean.TRUE.equals(food.getAutoMode())) {
            // Mode Auto = YES
            // Check time: in actual production, checks 7:00 AM, 11:30 AM, 6:00 PM
            if (Boolean.TRUE.equals(food.getCatDetected())) {
                // Cat Detection = YES
                int currentScale = food.getScaleWeightGram() != null ? food.getScaleWeightGram() : 0;
                if (currentScale < 10) {
                    // Check food on scale < 10 gram = YES
                    turnOnStepMotor = true;
                }
            }
        } else {
            // Mode Auto = NO
            if (Boolean.TRUE.equals(manualButton)) {
                // Turn on Button = YES
                turnOnStepMotor = true;
            }
        }

        food.setMachines(turnOnStepMotor);
        if (turnOnStepMotor) {
            food.setLastDispensed(Instant.now());
            food.setScaleWeightGram((food.getScaleWeightGram() != null ? food.getScaleWeightGram() : 0) + (food.getPercel() != null ? food.getPercel() : 45));
            int currentLvl = food.getFoodLevelPercent() != null ? food.getFoodLevelPercent() : 85;
            food.setFoodLevelPercent(Math.max(0, currentLvl - 5));

            List<Pet> pets = petRepository.findAll();
            UUID targetPetId = pets.isEmpty() ? null : pets.get(0).getId();
            if (targetPetId != null) {
                feedingLogRepository.save(
                        FeedingLog.builder()
                                .petId(targetPetId)
                                .foodGram(food.getPercel() != null ? food.getPercel() : 45)
                                .timestamp(Instant.now())
                                .build()
                );
            }
        }

        systemFoodRepository.save(food);
        return mapFoodToDto(food);
    }

    @Transactional
    public SystemWaterDto processWaterCycle(Boolean catDetected, Boolean manualButton) {
        com.aurapet.backend.domain.model.SystemWater water = systemWaterRepository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> com.aurapet.backend.domain.model.SystemWater.builder()
                        .id(UUID.randomUUID().toString())
                        .deviceId("WATER_PUMP_ESP32")
                        .autoMode(true)
                        .catDetected(false)
                        .build());

        if (catDetected != null) water.setCatDetected(catDetected);

        boolean turnOnPump = false;

        // Flowchart Hinh 4 logic
        if (Boolean.TRUE.equals(water.getAutoMode())) {
            // Mode Auto = YES
            if (Boolean.TRUE.equals(water.getCatDetected())) {
                // Cat Detection = YES -> Turn on the Pump
                turnOnPump = true;
            }
        } else {
            // Mode Auto = NO
            if (Boolean.TRUE.equals(manualButton)) {
                // Turn on Button = YES -> Turn on the Pump
                turnOnPump = true;
            }
        }

        water.setMachines(turnOnPump);
        if (turnOnPump) {
            water.setLastPumped(Instant.now());
        }

        systemWaterRepository.save(water);
        return mapWaterToDto(water);
    }

    public SystemFoodDto mapFoodToDto(com.aurapet.backend.domain.model.SystemFood food) {
        return SystemFoodDto.builder()
                .id(food.getId())
                .deviceId(food.getDeviceId())
                .auto(food.getAutoMode())
                .btnLed(food.getBtnLed())
                .ledGreen(food.getLedGreen())
                .ledRed(food.getLedRed())
                .machines(food.getMachines())
                .percel(food.getPercel())
                .calories(food.getCalories())
                .scaleWeightGram(food.getScaleWeightGram())
                .catDetected(food.getCatDetected())
                .scheduleTimes(food.getScheduleTimes())
                .foodLevelPercent(food.getFoodLevelPercent())
                .lastDispensed(food.getLastDispensed())
                .updatedAt(food.getUpdatedAt())
                .build();
    }

    public SystemWaterDto mapWaterToDto(com.aurapet.backend.domain.model.SystemWater water) {
        return SystemWaterDto.builder()
                .id(water.getId())
                .deviceId(water.getDeviceId())
                .auto(water.getAutoMode())
                .btnLed(water.getBtnLed())
                .ledGreen(water.getLedGreen())
                .ledRed(water.getLedRed())
                .machines(water.getMachines())
                .percel(water.getPercel())
                .catDetected(water.getCatDetected())
                .waterLevelPercent(water.getWaterLevelPercent())
                .lastPumped(water.getLastPumped())
                .updatedAt(water.getUpdatedAt())
                .build();
    }
}
