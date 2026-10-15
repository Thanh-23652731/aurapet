import 'package:dio/dio.dart';
import '../../core/constants/api_endpoints.dart';
import '../../core/network/api_response.dart';
import '../../core/network/dio_client.dart';
import '../models/dashboard_stats.dart';

class DashboardRepository {
  final DioClient _dioClient;

  DashboardRepository({DioClient? dioClient}) : _dioClient = dioClient ?? DioClient();

  Future<DashboardStats> getDashboardStats() async {
    try {
      final response = await _dioClient.dio.get(ApiEndpoints.dashboardStats);
      final apiResp = ApiResponse<DashboardStats>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => DashboardStats.fromJson(json as Map<String, dynamic>),
      );

      if (apiResp.success && apiResp.data != null) {
        return apiResp.data!;
      }
      throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Failed to fetch dashboard stats');
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Failed to load dashboard metrics';
      throw Exception(msg);
    }
  }
}
