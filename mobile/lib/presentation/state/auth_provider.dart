import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/user.dart';
import '../../data/repositories/auth_repository.dart';

class AuthState {
  final bool isLoading;
  final User? user;
  final String? token;
  final String? errorMessage;

  const AuthState({
    this.isLoading = false,
    this.user,
    this.token,
    this.errorMessage,
  });

  bool get isAuthenticated => token != null && token!.isNotEmpty && user != null;
  bool get isAdmin => user?.isAdmin ?? false;

  AuthState copyWith({
    bool? isLoading,
    User? user,
    String? token,
    String? errorMessage,
  }) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      user: user ?? this.user,
      token: token ?? this.token,
      errorMessage: errorMessage,
    );
  }
}

class AuthNotifier extends Notifier<AuthState> {
  late final AuthRepository _repository;

  @override
  AuthState build() {
    _repository = ref.watch(authRepositoryProvider);
    return const AuthState();
  }

  Future<bool> login(String email, String password) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final res = await _repository.login(email, password);
      state = state.copyWith(
        isLoading: false,
        token: res['token'] as String,
        user: res['user'] as User,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> register(String name, String email, String password, {String role = 'ROLE_USER'}) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final res = await _repository.register(name, email, password, role: role);
      state = state.copyWith(
        isLoading: false,
        token: res['token'] as String,
        user: res['user'] as User,
      );
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString().replaceAll('Exception: ', ''),
      );
      return false;
    }
  }

  void logout() {
    _repository.logout();
    state = const AuthState();
  }

  void updateProfile({String? name, String? email}) {
    if (state.user != null) {
      final updatedUser = state.user!.copyWith(name: name, email: email);
      state = state.copyWith(user: updatedUser);
    }
  }

  Future<bool> demoLogin({bool isAdmin = true}) async {
    if (isAdmin) {
      return await login('admin@aurapet.com', 'Admin@123');
    } else {
      return await login('user@aurapet.com', 'User@123');
    }
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository();
});

final authProvider = NotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);
