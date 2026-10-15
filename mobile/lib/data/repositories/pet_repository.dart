import 'package:dio/dio.dart';
import '../../core/constants/api_endpoints.dart';
import '../../core/network/api_response.dart';
import '../../core/network/dio_client.dart';
import '../models/pet.dart';

class PetRepository {
  final DioClient _dioClient;

  PetRepository({DioClient? dioClient}) : _dioClient = dioClient ?? DioClient();

  Future<List<Pet>> getPets({
    int page = 0,
    int size = 20,
    String? search,
    String? breed,
    String? gender,
    String? ownerId,
  }) async {
    try {
      final queryParams = <String, dynamic>{
        'page': page,
        'size': size,
      };
      if (search != null && search.isNotEmpty) queryParams['search'] = search;
      if (breed != null && breed.isNotEmpty) queryParams['breed'] = breed;
      if (gender != null && gender.isNotEmpty) queryParams['gender'] = gender;
      if (ownerId != null && ownerId.isNotEmpty) queryParams['ownerId'] = ownerId;

      final response = await _dioClient.dio.get(
        ApiEndpoints.pets,
        queryParameters: queryParams,
      );

      final apiResp = ApiResponse<Map<String, dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as Map<String, dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        final items = apiResp.data!['items'] as List<dynamic>? ?? [];
        return items.map((e) => Pet.fromJson(e as Map<String, dynamic>)).toList();
      }
      return [];
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to load pets';
      throw Exception(msg);
    }
  }

  Future<Pet> getPetById(String id) async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.petById(id));
      final apiResp = ApiResponse<Pet>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => Pet.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Pet not found');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to get pet';
      throw Exception(msg);
    }
  }

  Future<Pet> createPet(Map<String, dynamic> petData) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.pets,
        data: petData,
      );

      final apiResp = ApiResponse<Pet>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => Pet.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Failed to create pet');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to create pet';
      throw Exception(msg);
    }
  }

  Future<Pet> updatePet(String id, Map<String, dynamic> petData) async {
    try {
      final response = await _dioClient.dio.put(
        ApiEndpoints.petById(id),
        data: petData,
      );

      final apiResp = ApiResponse<Pet>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => Pet.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Failed to update pet');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to update pet';
      throw Exception(msg);
    }
  }

  Future<void> deletePet(String id) async {
    try {
      await _dioClient.dio.delete(ApiEndpoints.petById(id));
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to delete pet';
      throw Exception(msg);
    }
  }

  Future<Pet> updatePetImage(String id, String imageUrl) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.petImage(id),
        data: {'imageUrl': imageUrl},
      );

      final apiResp = ApiResponse<Pet>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => Pet.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception('Failed to update pet image');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to update image';
      throw Exception(msg);
    }
  }
}
