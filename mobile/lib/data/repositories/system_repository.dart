import 'package:dio/dio.dart';
import '../../core/constants/api_endpoints.dart';
import '../../core/network/api_response.dart';
import '../../core/network/dio_client.dart';
import '../models/feeding_log.dart';
import '../models/system_status.dart';

class SystemRepository {
  final DioClient _dioClient;

  SystemRepository({DioClient? dioClient}) : _dioClient = dioClient ?? DioClient();

  Future<SystemStatus> getSystemStatus() async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.systemStatus);
      final apiResp = ApiResponse<SystemStatus>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => SystemStatus.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Failed to fetch status');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'System status error';
      throw Exception(msg);
    }
  }

  Future<SystemStatus> dispenseFood({String? petId, int portionGram = 30}) async {
    try {
      final payload = <String, dynamic>{'portionGram': portionGram};
      if (petId != null) payload['petId'] = petId;

      final response = await _dioClient.dio.post(
        ApiEndpoints.systemFeeder,
        data: payload,
      );

      final apiResp = ApiResponse<SystemStatus>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => SystemStatus.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message);
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Feeder command failed';
      throw Exception(msg);
    }
  }

  Future<SystemStatus> setWaterPump(bool enabled) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.systemWater,
        data: {'enabled': enabled},
      );

      final apiResp = ApiResponse<SystemStatus>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => SystemStatus.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message);
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Water command failed';
      throw Exception(msg);
    }
  }

  Future<SystemStatus> triggerLitterAction(String action) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.systemLitter,
        data: {'action': action},
      );

      final apiResp = ApiResponse<SystemStatus>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => SystemStatus.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message);
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Litter command failed';
      throw Exception(msg);
    }
  }

  Future<List<FeedingLog>> getFeedingHistory() async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.systemFeederHistory);
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
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to load feeding history';
      throw Exception(msg);
    }
  }
}
