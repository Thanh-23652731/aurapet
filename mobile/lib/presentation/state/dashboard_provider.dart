import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/dashboard_stats.dart';
import '../../data/repositories/dashboard_repository.dart';

final dashboardRepositoryProvider = Provider<DashboardRepository>((ref) {
  return DashboardRepository();
});

class DashboardNotifier extends AsyncNotifier<DashboardStats> {
  @override
  Future<DashboardStats> build() async {
    final repo = ref.watch(dashboardRepositoryProvider);
    return await repo.getDashboardStats();
  }

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(dashboardRepositoryProvider);
      return await repo.getDashboardStats();
    });
  }
}

final dashboardProvider = AsyncNotifierProvider<DashboardNotifier, DashboardStats>(DashboardNotifier.new);
