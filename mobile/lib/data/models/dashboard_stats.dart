import 'alert.dart';
import 'system_status.dart';

class DashboardStats {
  final int totalPets;
  final int todayFoodGram;
  final int feedingSessionsCount;
  final int litterUsageCount;
  final List<Alert> latestAlerts;
  final SystemStatus deviceStatus;

  const DashboardStats({
    required this.totalPets,
    required this.todayFoodGram,
    required this.feedingSessionsCount,
    required this.litterUsageCount,
    required this.latestAlerts,
    required this.deviceStatus,
  });

  factory DashboardStats.fromJson(Map<String, dynamic> json) {
    return DashboardStats(
      totalPets: json['totalPets'] as int? ?? 0,
      todayFoodGram: json['todayFoodGram'] as int? ?? 0,
      feedingSessionsCount: json['feedingSessionsCount'] as int? ?? 0,
      litterUsageCount: json['litterUsageCount'] as int? ?? 0,
      latestAlerts: (json['latestAlerts'] as List<dynamic>?)
              ?.map((e) => Alert.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
      deviceStatus: json['deviceStatus'] != null
          ? SystemStatus.fromJson(json['deviceStatus'] as Map<String, dynamic>)
          : const SystemStatus(
              feeder: true,
              waterPump: true,
              litterCleaner: false,
              esp32: 'ONLINE',
              raspberryPi: 'ONLINE',
            ),
    );
  }
}
