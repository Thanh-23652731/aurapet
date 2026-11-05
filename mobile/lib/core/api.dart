import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Lỗi trả về từ API, có sẵn thông báo tiếng Việt để hiện cho người dùng.
class ApiException implements Exception {
  final String message;
  final int? status;
  const ApiException(this.message, [this.status]);

  bool get unauthorized => status == 401;

  @override
  String toString() => message;
}

/// Client HTTP duy nhất của app: lưu địa chỉ máy chủ + token vào bộ nhớ máy.
class ApiClient {
  static const _kBase = 'aurapet_api';
  static const _kToken = 'aurapet_token';

  /// Có thể đặt sẵn khi build: flutter run --dart-define=BACKEND_URL=https://...
  static const String _envBase = String.fromEnvironment('BACKEND_URL');

  final Dio _dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 8),
    receiveTimeout: const Duration(seconds: 15),
    headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
  ));

  SharedPreferences? _prefs;
  String baseUrl = '';
  String? token;

  /// Gọi khi phiên đăng nhập hết hạn (HTTP 401).
  VoidCallback? onUnauthorized;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    baseUrl = _prefs?.getString(_kBase) ?? (_envBase.isNotEmpty ? _envBase : _defaultBase());
    token = _prefs?.getString(_kToken);
  }

  static String _defaultBase() {
    if (kIsWeb) {
      final origin = Uri.base.origin;
      // Khi app web được backend phục vụ trực tiếp thì dùng luôn địa chỉ đó
      if (!Uri.base.host.endsWith('github.io')) return origin;
      return '';
    }
    if (defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080'; // máy ảo Android
    return 'http://localhost:8080';
  }

  bool get hasServer => baseUrl.isNotEmpty;

  Future<void> setBaseUrl(String url) async {
    baseUrl = normalize(url);
    await _prefs?.setString(_kBase, baseUrl);
  }

  Future<void> setToken(String? value) async {
    token = value;
    if (value == null) {
      await _prefs?.remove(_kToken);
    } else {
      await _prefs?.setString(_kToken, value);
    }
  }

  static String normalize(String url) {
    var u = url.trim();
    if (u.isEmpty) return u;
    if (!u.startsWith('http://') && !u.startsWith('https://')) u = 'http://$u';
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    return u;
  }

  /// Kiểm tra một địa chỉ có phải máy chủ AuraPet không.
  Future<bool> ping(String url, {Duration timeout = const Duration(seconds: 3)}) async {
    try {
      final res = await Dio(BaseOptions(connectTimeout: timeout, receiveTimeout: timeout))
          .get<dynamic>('${normalize(url)}/api/v1/ping');
      final data = res.data;
      return data is Map && data['data'] is Map && data['data']['app'] == 'aurapet';
    } catch (_) {
      return false;
    }
  }

  Future<dynamic> get(String path) => _send('GET', path);
  Future<dynamic> post(String path, [Object? body]) => _send('POST', path, body ?? const {});
  Future<dynamic> put(String path, Object body) => _send('PUT', path, body);
  Future<dynamic> delete(String path) => _send('DELETE', path);

  Future<dynamic> _send(String method, String path, [Object? body]) async {
    if (!hasServer) throw const ApiException('Chưa cấu hình địa chỉ máy chủ');
    try {
      final res = await _dio.request<dynamic>(
        '$baseUrl$path',
        data: body,
        options: Options(
          method: method,
          headers: token != null ? {'Authorization': 'Bearer $token'} : null,
        ),
      );
      final json = res.data;
      if (json is Map && json['success'] == false) {
        throw ApiException('${json['message'] ?? 'Có lỗi xảy ra'}', res.statusCode);
      }
      return json is Map ? json['data'] : json;
    } on DioException catch (e) {
      final status = e.response?.statusCode;
      final data = e.response?.data;
      if (status == 401 && token != null) {
        onUnauthorized?.call();
      }
      if (data is Map && data['message'] != null) {
        throw ApiException('${data['message']}', status);
      }
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.connectionError ||
          e.type == DioExceptionType.receiveTimeout) {
        throw ApiException('Không kết nối được máy chủ $baseUrl. Kiểm tra Wi-Fi và địa chỉ máy chủ.', status);
      }
      throw ApiException('Lỗi kết nối (${status ?? e.type.name})', status);
    }
  }
}
