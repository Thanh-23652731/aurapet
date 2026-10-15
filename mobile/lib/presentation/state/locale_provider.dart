import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/localization/app_strings.dart';

class LocaleNotifier extends Notifier<AppLanguage> {
  @override
  AppLanguage build() => AppLanguage.vi; // Tiếng Việt là ngôn ngữ chính mặc định!

  void setLanguage(AppLanguage lang) => state = lang;

  void toggleLanguage() {
    state = state == AppLanguage.vi ? AppLanguage.en : AppLanguage.vi;
  }
}

final localeProvider = NotifierProvider<LocaleNotifier, AppLanguage>(LocaleNotifier.new);

final stringsProvider = Provider<AppStrings>((ref) {
  final lang = ref.watch(localeProvider);
  return AppStrings(lang);
});
