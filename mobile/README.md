# AuraPet – app Flutter

Điều khiển máy cho ăn thật qua backend AuraPet (cùng API với web dashboard).

```bash
flutter pub get
flutter run                     # Android / iOS / Windows / Chrome
flutter test
```

- `lib/core` – API client (lưu token + địa chỉ máy chủ), theme, tìm máy chủ qua UDP
- `lib/data` – model và lời gọi API
- `lib/state` – Riverpod: phiên đăng nhập, trạng thái máy (2 giây/lần), thống kê, thú cưng
- `lib/ui` – màn hình: Điều khiển, Thú cưng, Lịch sử, Bác sĩ AI, Tài khoản
