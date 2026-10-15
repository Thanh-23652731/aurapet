import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/system_status.dart';
import '../../data/repositories/system_repository.dart';

final systemRepositoryProvider = Provider<SystemRepository>((ref) {
  return SystemRepository();
});

class SystemStatusState {
  final bool isLoading;
  final SystemStatus status;
  final String? message;
  final String? errorMessage;

  const SystemStatusState({
    this.isLoading = false,
    this.status = const SystemStatus(
      feeder: true,
      waterPump: true,
      litterCleaner: false,
      esp32: 'ONLINE',
      raspberryPi: 'ONLINE',
    ),
    this.message,
    this.errorMessage,
  });

  SystemStatusState copyWith({
    bool? isLoading,
    SystemStatus? status,
    String? message,
    String? errorMessage,
  }) {
    return SystemStatusState(
      isLoading: isLoading ?? this.isLoading,
      status: status ?? this.status,
      message: message,
      errorMessage: errorMessage,
    );
  }
}

class SystemStatusNotifier extends Notifier<SystemStatusState> {
  late final SystemRepository _repository;

  @override
  SystemStatusState build() {
    _repository = ref.watch(systemRepositoryProvider);
    Future.microtask(() => loadStatus());
    return const SystemStatusState();
  }

  Future<void> loadStatus() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final status = await _repository.getSystemStatus();
      state = state.copyWith(isLoading: false, status: status);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
    }
  }

  Future<void> dispenseFood({String? petId, int portionGram = 30}) async {
    try {
      final updated = await _repository.dispenseFood(petId: petId, portionGram: portionGram);
      state = state.copyWith(
        status: updated,
        message: 'Dispensed ${portionGram}g of food successfully',
      );
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
    }
  }

  Future<void> toggleWater(bool enabled) async {
    try {
      final updated = await _repository.setWaterPump(enabled);
      state = state.copyWith(
        status: updated,
        message: 'Water pump ${enabled ? "enabled" : "paused"}',
      );
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
    }
  }

  Future<void> cleanLitter() async {
    try {
      final updated = await _repository.triggerLitterAction('CLEAN');
      state = state.copyWith(
        status: updated,
        message: 'Auto litter cycle started',
      );
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
    }
  }
}

final systemStatusProvider = NotifierProvider<SystemStatusNotifier, SystemStatusState>(SystemStatusNotifier.new);
