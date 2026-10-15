import 'package:dio/dio.dart';
import '../../core/constants/api_endpoints.dart';
import '../../core/network/api_response.dart';
import '../../core/network/dio_client.dart';
import '../models/feeding_log.dart';
import '../models/health_summary.dart';
import '../models/litter_log.dart';
import '../models/weight_record.dart';

class HealthRepository {
  final DioClient _dioClient;

  HealthRepository({DioClient? dioClient}) : _dioClient = dioClient ?? DioClient();

  Future<HealthSummary> getHealthSummary(String petId) async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.healthSummary(petId));
      final apiResp = ApiResponse<HealthSummary>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => HealthSummary.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Failed to fetch health summary');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Health summary error';
      throw Exception(msg);
    }
  }

  Future<List<WeightRecord>> getWeightHistory(String petId) async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.weightHistory(petId));
      final apiResp = ApiResponse<List<dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as List<dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!
            .map((e) => WeightRecord.fromJson(e as Map<String, dynamic>))
            .toList();
      }
      return [];
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Weight history error';
      throw Exception(msg);
    }
  }

  Future<List<FeedingLog>> getFoodHistory(String petId) async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.foodHistory(petId));
      final apiResp = ApiResponse<List<dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as List<dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!
            .map((e) => FeedingLog.fromJson(e as Map<String, dynamic>))
            .toList();
      }
      return [];
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Food history error';
      throw Exception(msg);
    }
  }

  Future<List<LitterLog>> getLitterHistory(String petId) async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.litterHistory(petId));
      final apiResp = ApiResponse<List<dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as List<dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!
            .map((e) => LitterLog.fromJson(e as Map<String, dynamic>))
            .toList();
      }
      return [];
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Litter history error';
      throw Exception(msg);
    }
  }
}
