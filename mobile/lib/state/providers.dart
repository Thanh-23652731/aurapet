import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api.dart';
import '../data/aurapet_api.dart';
import '../data/models.dart';

/// Được override trong main() sau khi đọc bộ nhớ máy.
final apiClientProvider = Provider<ApiClient>((ref) => throw UnimplementedError('ApiClient chưa khởi tạo'));
final apiProvider = Provider<AuraPetApi>((ref) => AuraPetApi(ref.watch(apiClientProvider)));

// ============================================================ Phiên đăng nhập
class SessionState {
  final bool restoring;
  final bool busy;
  final User? user;
  final String? error;

  const SessionState({this.restoring = false, this.busy = false, this.user, this.error});

  bool get loggedIn => user != null;
}

class SessionNotifier extends Notifier<SessionState> {
  ApiClient get _client => ref.read(apiClientProvider);
  AuraPetApi get _api => ref.read(apiProvider);

  @override
  SessionState build() {
    _client.onUnauthorized = () => logout(message: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại');
    if (_client.token != null && _client.hasServer) {
      Future.microtask(_restore);
      return const SessionState(restoring: true);
    }
    return const SessionState();
  }

  Future<void> _restore() async {
    try {
      final user = await _api.me();
      state = SessionState(user: user);
    } catch (e) {
      final unauthorized = e is ApiException && e.unauthorized;
      if (unauthorized) await _client.setToken(null);
      state = SessionState(error: unauthorized ? null : e.toString());
    }
  }

  Future<void> login(String email, String password) => _auth(() => _api.login(email, password));

  Future<void> register(String name, String email, String password) => _auth(() => _api.register(name, email, password));

  Future<void> _auth(Future<(String, User)> Function() call) async {
    state = const SessionState(busy: true);
    try {
      final (token, user) = await call();
      await _client.setToken(token);
      state = SessionState(user: user);
    } catch (e) {
      state = SessionState(error: e.toString());
    }
  }

  void setUser(User user) => state = SessionState(user: user);

  Future<void> logout({String? message}) async {
    await _client.setToken(null);
    state = SessionState(error: message);
  }
}

final sessionProvider = NotifierProvider<SessionNotifier, SessionState>(SessionNotifier.new);

// ============================================================ Giao diện sáng/tối
class ThemeModeNotifier extends Notifier<ThemeMode> {
  @override
  ThemeMode build() => ThemeMode.system;

  void toggle(Brightness current) => state = current == Brightness.dark ? ThemeMode.light : ThemeMode.dark;
}

final themeModeProvider = NotifierProvider<ThemeModeNotifier, ThemeMode>(ThemeModeNotifier.new);

// ============================================================ Máy cho ăn (cập nhật 2 giây/lần)
class DeviceView {
  final DeviceState? device;
  final String? error;
  final DateTime? pendingUntil;

  const DeviceView({this.device, this.error, this.pendingUntil});

  bool get pending => pendingUntil != null && DateTime.now().isBefore(pendingUntil!) && !(device?.feeding ?? false);
}

class DeviceNotifier extends Notifier<DeviceView> {
  Timer? _timer;
  bool _disposed = false;

  AuraPetApi get _api => ref.read(apiProvider);

  @override
  DeviceView build() {
    _disposed = false;
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => refresh());
    ref.onDispose(() {
      _disposed = true;
      _timer?.cancel();
    });
    Future.microtask(refresh);
    return const DeviceView();
  }

  Future<void> refresh() async {
    try {
      final d = await _api.deviceState();
      if (_disposed) return;
      state = DeviceView(device: d, pendingUntil: d.feeding ? null : state.pendingUntil);
    } catch (e) {
      if (_disposed) return;
      state = DeviceView(device: state.device, error: e.toString(), pendingUntil: state.pendingUntil);
    }
  }

  /// Chạy một lệnh; trả về thông báo lỗi (null nếu thành công).
  Future<String?> _run(Future<DeviceState> Function() call, {bool feeding = false}) async {
    if (feeding) {
      state = DeviceView(device: state.device, pendingUntil: DateTime.now().add(const Duration(seconds: 5)));
    }
    try {
      final d = await call();
      if (!_disposed) state = DeviceView(device: d, pendingUntil: state.pendingUntil);
      if (feeding) Future.delayed(const Duration(milliseconds: 700), refresh);
      return null;
    } catch (e) {
      if (!_disposed) state = DeviceView(device: state.device);
      return e.toString();
    }
  }

  Future<String?> feed(int gram) => _run(() => _api.feed(gram), feeding: true);
  Future<String?> stop() => _run(_api.stop);
  Future<String?> tare() => _run(_api.tare);
  Future<String?> settings(Map<String, dynamic> patch) => _run(() => _api.updateSettings(patch));
}

final deviceProvider = NotifierProvider.autoDispose<DeviceNotifier, DeviceView>(DeviceNotifier.new);

// ============================================================ Thống kê (20 giây/lần)
class StatsNotifier extends Notifier<DashboardStats?> {
  Timer? _timer;
  bool _disposed = false;

  @override
  DashboardStats? build() {
    _disposed = false;
    _timer = Timer.periodic(const Duration(seconds: 20), (_) => refresh());
    ref.onDispose(() {
      _disposed = true;
      _timer?.cancel();
    });
    Future.microtask(refresh);
    return null;
  }

  Future<void> refresh() async {
    try {
      final s = await ref.read(apiProvider).dashboard();
      if (!_disposed) state = s;
    } catch (_) {
      // lỗi mạng đã hiện ở thanh trạng thái máy
    }
  }
}

final statsProvider = NotifierProvider.autoDispose<StatsNotifier, DashboardStats?>(StatsNotifier.new);

// ============================================================ Thú cưng
class PetsView {
  final bool loading;
  final List<Pet> pets;
  final String? error;

  const PetsView({this.loading = false, this.pets = const [], this.error});
}

class PetsNotifier extends Notifier<PetsView> {
  bool _disposed = false;
  String _search = '';

  @override
  PetsView build() {
    _disposed = false;
    ref.onDispose(() => _disposed = true);
    Future.microtask(reload);
    return const PetsView(loading: true);
  }

  Future<void> reload() async {
    try {
      final pets = await ref.read(apiProvider).pets(search: _search);
      if (!_disposed) state = PetsView(pets: pets);
    } catch (e) {
      if (!_disposed) state = PetsView(pets: state.pets, error: e.toString());
    }
  }

  void search(String q) {
    _search = q;
    reload();
  }
}

final petsProvider = NotifierProvider.autoDispose<PetsNotifier, PetsView>(PetsNotifier.new);
