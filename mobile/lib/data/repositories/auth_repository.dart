import 'package:dio/dio.dart';
import '../../core/constants/api_endpoints.dart';
import '../../core/network/api_response.dart';
import '../../core/network/dio_client.dart';
import '../models/user.dart';

class AuthRepository {
  final DioClient _dioClient;

  AuthRepository({DioClient? dioClient}) : _dioClient = dioClient ?? DioClient();

  Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.login,
        data: {
          'email': email.trim(),
          'password': password,
        },
      );

      final apiResp = ApiResponse<Map<String, dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as Map<String, dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        final token = apiResp.data!['token'] as String;
        _dioClient.setAuthToken(token);
        final userJson = apiResp.data!['user'] as Map<String, dynamic>;
        return {
          'token': token,
          'user': User.fromJson(userJson),
        };
      } else {
        throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Login failed');
      }
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout ||
          e.type == DioExceptionType.connectionError) {
        throw Exception('Không thể kết nối tới máy chủ (${ApiEndpoints.baseUrl}). Vui lòng đảm bảo điện thoại và máy tính kết nối chung mạng Wi-Fi và file start_aurapet.bat đang chạy.');
      }
      final msg = e.response?.data?['message'] ?? e.message ?? 'Authentication error';
      throw Exception(msg);
    }
  }

  Future<Map<String, dynamic>> register(String name, String email, String password, {String role = 'ROLE_USER'}) async {
    try {
      final response = await _dioClient.dio.post(
        ApiEndpoints.register,
        data: {
          'name': name.trim(),
          'email': email.trim(),
          'password': password,
          'role': role,
        },
      );

      final apiResp = ApiResponse<Map<String, dynamic>>.fromJson(
        response.data as Map<String, dynamic>,
        (json) => json as Map<String, dynamic>,
      );

      if (apiResp.success && apiResp.data != null) {
        final token = apiResp.data!['token'] as String;
        _dioClient.setAuthToken(token);
        final userJson = apiResp.data!['user'] as Map<String, dynamic>;
        return {
          'token': token,
          'user': User.fromJson(userJson),
        };
      } else {
        throw Exception(apiResp.message.isNotEmpty ? apiResp.message : 'Registration failed');
      }
    } on DioException catch (e) {
      final msg = e.response?.data?['message'] ?? e.message ?? 'Registration error';
      throw Exception(msg);
    }
  }

  void logout() {
    _dioClient.clearAuthToken();
  }
}
