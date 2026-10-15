import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/pet.dart';
import '../../data/repositories/pet_repository.dart';

final petRepositoryProvider = Provider<PetRepository>((ref) {
  return PetRepository();
});

class PetListState {
  final bool isLoading;
  final List<Pet> pets;
  final String? searchQuery;
  final String? selectedBreed;
  final String? selectedGender;
  final String? errorMessage;

  const PetListState({
    this.isLoading = false,
    this.pets = const [],
    this.searchQuery,
    this.selectedBreed,
    this.selectedGender,
    this.errorMessage,
  });

  PetListState copyWith({
    bool? isLoading,
    List<Pet>? pets,
    String? searchQuery,
    String? selectedBreed,
    String? selectedGender,
    String? errorMessage,
  }) {
    return PetListState(
      isLoading: isLoading ?? this.isLoading,
      pets: pets ?? this.pets,
      searchQuery: searchQuery ?? this.searchQuery,
      selectedBreed: selectedBreed ?? this.selectedBreed,
      selectedGender: selectedGender ?? this.selectedGender,
      errorMessage: errorMessage,
    );
  }
}

class PetListNotifier extends Notifier<PetListState> {
  late final PetRepository _repository;

  @override
  PetListState build() {
    _repository = ref.watch(petRepositoryProvider);
    // Trigger initial load
    Future.microtask(() => loadPets());
    return const PetListState();
  }

  Future<void> loadPets() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final pets = await _repository.getPets(
        search: state.searchQuery,
        breed: state.selectedBreed,
        gender: state.selectedGender,
      );
      state = state.copyWith(isLoading: false, pets: pets);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
    }
  }

  void setSearchQuery(String query) {
    state = state.copyWith(searchQuery: query.trim().isEmpty ? null : query.trim());
    loadPets();
  }

  void setBreedFilter(String? breed) {
    state = state.copyWith(selectedBreed: breed);
    loadPets();
  }

  void setGenderFilter(String? gender) {
    state = state.copyWith(selectedGender: gender);
    loadPets();
  }

  Future<bool> createPet(Map<String, dynamic> petData) async {
    try {
      await _repository.createPet(petData);
      await loadPets();
      return true;
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
      return false;
    }
  }

  Future<bool> updatePet(String id, Map<String, dynamic> petData) async {
    try {
      await _repository.updatePet(id, petData);
      await loadPets();
      return true;
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
      return false;
    }
  }

  Future<bool> deletePet(String id) async {
    try {
      await _repository.deletePet(id);
      await loadPets();
      return true;
    } catch (e) {
      state = state.copyWith(errorMessage: e.toString().replaceAll('Exception: ', ''));
      return false;
    }
  }
}

final petListProvider = NotifierProvider<PetListNotifier, PetListState>(PetListNotifier.new);
