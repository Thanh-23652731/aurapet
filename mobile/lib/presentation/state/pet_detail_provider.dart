import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/feeding_log.dart';
import '../../data/models/health_summary.dart';
import '../../data/models/litter_log.dart';
import '../../data/models/pet.dart';
import '../../data/models/weight_record.dart';
import '../../data/repositories/health_repository.dart';
import 'pet_provider.dart';

final healthRepositoryProvider = Provider<HealthRepository>((ref) {
  return HealthRepository();
});

class PetDetailBundle {
  final Pet pet;
  final HealthSummary healthSummary;
  final List<WeightRecord> weightHistory;
  final List<FeedingLog> foodHistory;
  final List<LitterLog> litterHistory;

  const PetDetailBundle({
    required this.pet,
    required this.healthSummary,
    required this.weightHistory,
    required this.foodHistory,
    required this.litterHistory,
  });
}

final petDetailBundleProvider = FutureProvider.family<PetDetailBundle, String>((ref, petId) async {
  final petRepo = ref.watch(petRepositoryProvider);
  final healthRepo = ref.watch(healthRepositoryProvider);

  final pet = await petRepo.getPetById(petId);
  final healthSummary = await healthRepo.getHealthSummary(petId);
  final weightHistory = await healthRepo.getWeightHistory(petId);
  final foodHistory = await healthRepo.getFoodHistory(petId);
  final litterHistory = await healthRepo.getLitterHistory(petId);

  return PetDetailBundle(
    pet: pet,
    healthSummary: healthSummary,
    weightHistory: weightHistory,
    foodHistory: foodHistory,
    litterHistory: litterHistory,
  );
});
