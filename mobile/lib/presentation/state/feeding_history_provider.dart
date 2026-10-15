import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/feeding_log.dart';
import '../../data/repositories/system_repository.dart';
import 'system_provider.dart';

enum FeedingTimeFilter { all, today, past7Days }

class FeedingHistoryState {
  final bool isLoading;
  final List<FeedingLog> allLogs;
  final String? selectedPetId;
  final FeedingTimeFilter timeFilter;
  final String? errorMessage;

  const FeedingHistoryState({
    this.isLoading = false,
    this.allLogs = const [],
    this.selectedPetId,
    this.timeFilter = FeedingTimeFilter.all,
    this.errorMessage,
  });

  FeedingHistoryState copyWith({
    bool? isLoading,
    List<FeedingLog>? allLogs,
    String? selectedPetId,
    bool clearSelectedPetId = false,
    FeedingTimeFilter? timeFilter,
    String? errorMessage,
  }) {
    return FeedingHistoryState(
      isLoading: isLoading ?? this.isLoading,
      allLogs: allLogs ?? this.allLogs,
      selectedPetId: clearSelectedPetId ? null : (selectedPetId ?? this.selectedPetId),
      timeFilter: timeFilter ?? this.timeFilter,
      errorMessage: errorMessage,
    );
  }

  List<FeedingLog> get filteredLogs {
    return allLogs.where((log) {
      if (selectedPetId != null && log.petId != selectedPetId) {
        return false;
      }
      final now = DateTime.now();
      if (timeFilter == FeedingTimeFilter.today) {
        final startOfToday = DateTime(now.year, now.month, now.day);
        return log.timestamp.isAfter(startOfToday);
      } else if (timeFilter == FeedingTimeFilter.past7Days) {
        final sevenDaysAgo = now.subtract(const Duration(days: 7));
        return log.timestamp.isAfter(sevenDaysAgo);
      }
      return true;
    }).toList();
  }

  int get totalFoodGramsToday {
    final now = DateTime.now();
    final startOfToday = DateTime(now.year, now.month, now.day);
    return allLogs
        .where((l) => l.timestamp.isAfter(startOfToday))
        .fold(0, (sum, l) => sum + l.foodGram);
  }

  int get todaySessionsCount {
    final now = DateTime.now();
    final startOfToday = DateTime(now.year, now.month, now.day);
    return allLogs.where((l) => l.timestamp.isAfter(startOfToday)).length;
  }

  int get averagePortionGrams {
    if (allLogs.isEmpty) return 0;
    final total = allLogs.fold(0, (sum, l) => sum + l.foodGram);
    return (total / allLogs.length).round();
  }
}

class FeedingHistoryNotifier extends Notifier<FeedingHistoryState> {
  late final SystemRepository _repository;

  @override
  FeedingHistoryState build() {
    _repository = ref.watch(systemRepositoryProvider);
    Future.microtask(() => loadHistory());
    return const FeedingHistoryState();
  }

  Future<void> loadHistory() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final logs = await _repository.getFeedingHistory();
      state = state.copyWith(isLoading: false, allLogs: logs);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
    }
  }

  void selectPet(String? petId) {
    if (petId == null) {
      state = state.copyWith(clearSelectedPetId: true);
    } else {
      state = state.copyWith(selectedPetId: petId);
    }
  }

  void setTimeFilter(FeedingTimeFilter filter) {
    state = state.copyWith(timeFilter: filter);
  }
}

final feedingHistoryProvider =
    NotifierProvider<FeedingHistoryNotifier, FeedingHistoryState>(FeedingHistoryNotifier.new);
