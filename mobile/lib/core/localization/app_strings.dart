enum AppLanguage { vi, en }

class AppStrings {
  final AppLanguage lang;

  const AppStrings(this.lang);

  bool get isVi => lang == AppLanguage.vi;

  // App & Navigation
  String get appTitle => 'Smart Pet Care';
  String get appSubtitle => isVi
      ? 'Chăm Sóc Thú Cưng Thông Minh'
      : 'Smart Pet Care Ecosystem';
  String get navDashboard => isVi ? 'Tổng quan' : 'Dashboard';
  String get navPets => isVi ? 'Thú cưng' : 'Pets';
  String get navDevices => isVi ? 'Thiết bị' : 'Devices';
  String get navAccount => isVi ? 'Tài khoản' : 'Account';

  // Auth
  String get loginTitle => isVi ? 'Đăng nhập tài khoản' : 'Sign In';
  String get loginSubtitle => isVi
      ? 'Chào mừng bạn quay lại hệ sinh thái AuraPet'
      : 'Welcome back to AuraPet ecosystem';
  String get emailLabel => 'Email';
  String get passwordLabel => isVi ? 'Mật khẩu' : 'Password';
  String get loginButton => isVi ? 'Đăng nhập' : 'Sign In';
  String get quickDemoLogin => isVi ? 'Đăng nhập nhanh Demo:' : 'Quick Demo Access:';
  String get demoAdmin => 'Demo Admin';
  String get demoUser => 'Demo User';
  String get invalidCredentials => isVi ? 'Email hoặc mật khẩu không chính xác' : 'Invalid email or password';

  // Dashboard
  String get dashboardTitle => isVi ? 'Bảng điều khiển' : 'Dashboard';
  String get dashboardSubtitle => isVi ? 'Tổng quan sức khỏe & thiết bị' : 'Health overview & devices';
  String get activePets => isVi ? 'Bé mèo đang nuôi' : 'Active Pets';
  String get todayKibble => isVi ? 'Khẩu phần hôm nay' : "Today's Kibble";
  String get feedingSessions => isVi ? 'Lần cho ăn' : 'Feeding Sessions';
  String get litterCleaned => isVi ? 'Lần dọn vệ sinh' : 'Litter Cleaned';
  String get recentAlerts => isVi ? 'Cảnh báo gần đây' : 'Recent Telemetry Alerts';
  String get noAlerts => isVi ? 'Không có cảnh báo nào' : 'All systems normal. No alerts.';

  // Pet List
  String get petsTitle => isVi ? 'Danh sách thú cưng' : 'Your Companions';
  String get searchPets => isVi ? 'Tìm kiếm mèo theo tên, giống...' : 'Search pets by name, breed...';
  String get addNewPet => isVi ? 'Thêm thú cưng' : 'Add Companion';
  String get allBreeds => isVi ? 'Tất cả giống' : 'All Breeds';
  String get male => isVi ? 'Đực' : 'Male';
  String get female => isVi ? 'Cái' : 'Female';
  String get neutered => isVi ? 'Đã triệt sản' : 'Sterilized';
  String get intact => isVi ? 'Chưa triệt sản' : 'Intact';
  String get ageYears => isVi ? 'tuổi' : 'years';
  String get noPetsFound => isVi ? 'Chưa có thú cưng nào' : 'No Companions Found';

  // Pet Detail
  String get petProfile => isVi ? 'Hồ sơ thú cưng' : 'Companion Profile';
  String get healthScore => isVi ? 'Điểm sức khỏe AI' : 'AI Health Score';
  String get weightTrend => isVi ? 'Biểu đồ cân nặng' : 'Weight Trend';
  String get recentFeedingLogs => isVi ? 'Lịch sử cho ăn gần đây' : 'Recent Feeding Logs';
  String get seeAll => isVi ? 'Xem tất cả' : 'See All';
  String get noFeedingLogs => isVi ? 'Chưa có nhật ký cho ăn' : 'No Feeding Logs';
  String get aiRecommendations => isVi ? 'Đánh giá & Khuyến nghị Edge AI' : 'Edge AI Clinical Insights';

  // System & Devices
  String get devicesTitle => isVi ? 'Điều khiển thiết bị IoT' : 'IoT Hardware Control';
  String get edgeNodesTelemetry => isVi ? 'Trạng thái vi điều khiển' : 'Edge Nodes Telemetry';
  String get esp32Node => 'Hệ vi xử lý ESP32';
  String get rpiNode => 'Module Camera AI Pi 5';
  String get feederTitle => isVi ? 'Khay ăn thông minh #1' : 'Smart Pet Feeder #1';
  String get feederSubtitle => isVi ? 'Định lượng nhả hạt servo chính xác' : 'Servo Precision Dispenser';
  String get hopperCapacity => isVi ? 'Sức chứa khay thức ăn' : 'Food Hopper Capacity';
  String get dispensePortion => isVi ? 'Khẩu phần nhả:' : 'Dispense Portion:';
  String get dispenseButton => isVi ? 'Nhả thức ăn' : 'Dispense Portion';
  String get viewFeedingHistory => isVi ? 'Xem lịch sử cho ăn' : 'View Feeding History';
  String get waterPumpTitle => isVi ? 'Máy lọc nước tuần hoàn' : 'Circulating Water Fountain';
  String get waterLevel => isVi ? 'Mức nước hiện tại' : 'Reservoir Water Level';
  String get pumpActive => isVi ? 'Máy bơm đang chạy' : 'Pump Active';
  String get pumpPaused => isVi ? 'Máy bơm tạm dừng' : 'Pump Paused';
  String get litterBoxTitle => isVi ? 'Khay vệ sinh tự động' : 'Smart Auto Litter Box';
  String get wasteDrawer => isVi ? 'Dung tích ngăn chứa chất thải' : 'Waste Drawer Capacity';
  String get triggerCleaning => isVi ? 'Bắt đầu dọn dẹp' : 'Trigger Cleaning Cycle';
  String get ready => isVi ? 'SẴN SÀNG' : 'READY';
  String get offline => isVi ? 'NGOẠI TUYẾN' : 'OFFLINE';

  // Feeding History
  String get feedingHistoryTitle => isVi ? 'Lịch sử cho ăn' : 'Feeding History';
  String get feedingHistorySubtitle => isVi ? 'Nhật ký cho ăn tự động & thủ công' : 'Automated & manual kibble logs';
  String get todayIntake => isVi ? 'Khẩu phần hôm nay' : "Today's Intake";
  String get todaySessions => isVi ? 'Số bữa hôm nay' : 'Sessions Today';
  String get avgPortion => isVi ? 'Trung bình/bữa' : 'Avg Portion';
  String get filterByPet => isVi ? 'Lọc theo bé mèo' : 'Filter by Pet';
  String get allPets => isVi ? 'Tất cả mèo' : 'All Pets';
  String get allTime => isVi ? 'Tất cả' : 'All Time';
  String get today => isVi ? 'Hôm nay' : 'Today';
  String get past7Days => isVi ? '7 ngày qua' : 'Past 7 Days';
  String get feedingRecords => isVi ? 'Bản ghi cho ăn' : 'Feeding Records';
  String get clearFilter => isVi ? 'Bỏ lọc' : 'Clear Filter';
  String get noFeedingRecords => isVi ? 'Không có bản ghi nào' : 'No Feeding Records';
  String get completed => isVi ? 'HOÀN THÀNH' : 'COMPLETED';

  // Profile & Settings
  String get profileSettings => isVi ? 'Tài khoản & Cài đặt' : 'Profile & Settings';
  String get preferences => isVi ? 'Tùy chỉnh hệ thống' : 'Preferences';
  String get appearance => isVi ? 'Giao diện' : 'Appearance';
  String get lightMode => isVi ? 'Chế độ sáng' : 'Light Mode';
  String get darkMode => isVi ? 'Chế độ tối' : 'Dark Mode';
  String get language => isVi ? 'Ngôn ngữ (Language)' : 'Language';
  String get vietnamese => 'Tiếng Việt (Mặc định)';
  String get english => 'English';
  String get pushNotifications => isVi ? 'Thông báo đẩy' : 'Push Notifications';
  String get pushNotificationsDesc => isVi ? 'Cảnh báo cho ăn & sức khỏe đang bật' : 'Feeding and health alerts enabled';
  String get signOut => isVi ? 'Đăng xuất' : 'Sign Out';
  String get cancel => isVi ? 'Hủy' : 'Cancel';
  String get save => isVi ? 'Lưu' : 'Save';
}
