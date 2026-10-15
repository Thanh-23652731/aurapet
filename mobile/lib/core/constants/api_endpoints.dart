import 'package:flutter/foundation.dart';

class ApiEndpoints {
  // Cấu hình URL Cloud Server / VPS (đổi URL này khi đã đưa lên Cloud/Render/Railway/VPS)
  // Ví dụ: static const String cloudBackendUrl = 'https://aurapet-api.onrender.com';
  //        static const String cloudBackendUrl = 'http://103.xxx.xxx.xxx:8080';
  static const String cloudBackendUrl = '';

  // URL tùy chỉnh do người dùng nhập trên giao diện app
  static String? customServerUrl;

  // Base URL auto-switch based on platform & environment
  static String get baseUrl {
    if (customServerUrl != null && customServerUrl!.trim().isNotEmpty) {
      return customServerUrl!.trim();
    }
    const fromEnv = String.fromEnvironment('BACKEND_URL');
    if (fromEnv.isNotEmpty) {
      return fromEnv;
    }
    if (cloudBackendUrl.isNotEmpty) {
      return cloudBackendUrl;
    }

    if (kIsWeb) {
      return 'http://localhost:8080';
    }
    // For desktop (Windows/macOS/Linux)
    if (defaultTargetPlatform == TargetPlatform.windows ||
        defaultTargetPlatform == TargetPlatform.macOS ||
        defaultTargetPlatform == TargetPlatform.linux) {
      return 'http://localhost:8080';
    }
    // For Android / iOS (real phone on LAN Wi-Fi or emulator)
    if (defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS) {
      return 'http://10.10.59.64:8080';
    }
    return 'http://localhost:8080';
  }

  // Auth Endpoints
  static const String login = '/api/v1/auth/login';
  static const String register = '/api/v1/auth/register';
  static const String me = '/api/v1/auth/me';

  // Pet Endpoints
  static const String pets = '/api/v1/pets';
  static String petById(String id) => '/api/v1/pets/$id';
  static String petImage(String id) => '/api/v1/pets/$id/image';

  // Dashboard
  static const String dashboardStats = '/api/v1/dashboard/stats';

  // Health Analytics
  static String healthSummary(String petId) => '/api/v1/health/$petId/summary';
  static String weightHistory(String petId) => '/api/v1/health/$petId/weight';
  static String foodHistory(String petId) => '/api/v1/health/$petId/food';
  static String litterHistory(String petId) => '/api/v1/health/$petId/litter';

  // IoT Device Endpoints
  static const String systemStatus = '/api/v1/system/status';
  static const String systemFeeder = '/api/v1/system/feeder';
  static const String systemFeederHistory = '/api/v1/system/feeder/history';
  static const String systemWater = '/api/v1/system/water';
  static const String systemLitter = '/api/v1/system/litter';
}
