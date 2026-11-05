import '../core/api.dart';
import 'models.dart';

/// Tất cả lời gọi API của AuraPet ở một chỗ.
class AuraPetApi {
  final ApiClient client;
  const AuraPetApi(this.client);

  // ---- Tài khoản ----
  Future<(String, User)> login(String email, String password) async {
    final d = await client.post('/api/v1/auth/login', {'email': email.trim(), 'password': password});
    return ('${d['token']}', User.fromJson(Map<String, dynamic>.from(d['user'] as Map)));
  }

  Future<(String, User)> register(String name, String email, String password) async {
    final d = await client.post('/api/v1/auth/register', {'name': name.trim(), 'email': email.trim(), 'password': password});
    return ('${d['token']}', User.fromJson(Map<String, dynamic>.from(d['user'] as Map)));
  }

  Future<User> me() async => User.fromJson(Map<String, dynamic>.from(await client.get('/api/v1/auth/me') as Map));

  Future<User> updateProfile({String? name, String? currentPassword, String? newPassword}) async {
    final body = <String, dynamic>{
      if (name != null) 'name': name,
      if (currentPassword != null) 'currentPassword': currentPassword,
      if (newPassword != null) 'newPassword': newPassword,
    };
    return User.fromJson(Map<String, dynamic>.from(await client.put('/api/v1/auth/me', body) as Map));
  }

  // ---- Máy cho ăn ----
  DeviceState _state(dynamic d) => DeviceState.fromJson(Map<String, dynamic>.from(d as Map));

  Future<DeviceState> deviceState() async => _state(await client.get('/api/v1/device/state'));
  Future<DeviceState> feed(int gram) async => _state(await client.post('/api/v1/device/feed', {'gram': gram, 'source': 'App'}));
  Future<DeviceState> stop() async => _state(await client.post('/api/v1/device/stop'));
  Future<DeviceState> tare() async => _state(await client.post('/api/v1/device/tare'));
  Future<DeviceState> updateSettings(Map<String, dynamic> patch) async =>
      _state(await client.put('/api/v1/device/settings', patch));

  Future<List<FeedingLog>> feedings({int limit = 100}) async {
    final d = await client.get('/api/v1/device/feedings?limit=$limit') as List;
    return d.map((e) => FeedingLog.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<DashboardStats> dashboard() async =>
      DashboardStats.fromJson(Map<String, dynamic>.from(await client.get('/api/v1/dashboard/stats') as Map));

  // ---- Thú cưng ----
  Future<List<Pet>> pets({String? search}) async {
    final q = (search != null && search.trim().isNotEmpty) ? '&search=${Uri.encodeQueryComponent(search.trim())}' : '';
    final d = await client.get('/api/v1/pets?size=100$q') as Map;
    return (d['items'] as List).map((e) => Pet.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<void> savePet(Pet pet, {String? id}) async {
    if (id == null) {
      await client.post('/api/v1/pets', pet.toJson());
    } else {
      await client.put('/api/v1/pets/$id', pet.toJson());
    }
  }

  Future<void> deletePet(String id) async {
    await client.delete('/api/v1/pets/$id');
  }

  Future<HealthSummary> health(String petId) async =>
      HealthSummary.fromJson(Map<String, dynamic>.from(await client.get('/api/v1/health/$petId/summary') as Map));

  Future<List<DailyFood>> dailyFood(String petId) async {
    final d = await client.get('/api/v1/health/$petId/food/daily?days=7') as List;
    return d.map((e) => DailyFood.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<WeightPoint>> weights(String petId) async {
    final d = await client.get('/api/v1/health/$petId/weight') as List;
    return d.map((e) => WeightPoint.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  // ---- Bác sĩ AI ----
  Future<String> askAi(String message, {String? petId, List<Map<String, String>> history = const []}) async {
    final d = await client.post('/api/v1/ai/chat', {'message': message, 'petId': petId, 'history': history}) as Map;
    return '${d['reply']}';
  }

  Future<bool> aiConfigured() async => (await client.get('/api/v1/ai/status')) == true;
}
