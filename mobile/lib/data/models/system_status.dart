class SystemStatus {
  final bool feeder;
  final bool waterPump;
  final bool litterCleaner;
  final String esp32; // ONLINE, OFFLINE, BUSY
  final String raspberryPi; // ONLINE, OFFLINE, BUSY
  final int foodLevelPercent;
  final int waterLevelPercent;
  final int litterWastePercent;

  const SystemStatus({
    required this.feeder,
    required this.waterPump,
    required this.litterCleaner,
    required this.esp32,
    required this.raspberryPi,
    this.foodLevelPercent = 80,
    this.waterLevelPercent = 85,
    this.litterWastePercent = 20,
  });

  factory SystemStatus.fromJson(Map<String, dynamic> json) {
    return SystemStatus(
      feeder: json['feeder'] as bool? ?? true,
      waterPump: json['waterPump'] as bool? ?? true,
      litterCleaner: json['litterCleaner'] as bool? ?? false,
      esp32: json['esp32'] as String? ?? 'ONLINE',
      raspberryPi: json['raspberryPi'] as String? ?? 'ONLINE',
      foodLevelPercent: json['foodLevelPercent'] as int? ?? 80,
      waterLevelPercent: json['waterLevelPercent'] as int? ?? 85,
      litterWastePercent: json['litterWastePercent'] as int? ?? 20,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'feeder': feeder,
      'waterPump': waterPump,
      'litterCleaner': litterCleaner,
      'esp32': esp32,
      'raspberryPi': raspberryPi,
      'foodLevelPercent': foodLevelPercent,
      'waterLevelPercent': waterLevelPercent,
      'litterWastePercent': litterWastePercent,
    };
  }

  SystemStatus copyWith({
    bool? feeder,
    bool? waterPump,
    bool? litterCleaner,
    String? esp32,
    String? raspberryPi,
    int? foodLevelPercent,
    int? waterLevelPercent,
    int? litterWastePercent,
  }) {
    return SystemStatus(
      feeder: feeder ?? this.feeder,
      waterPump: waterPump ?? this.waterPump,
      litterCleaner: litterCleaner ?? this.litterCleaner,
      esp32: esp32 ?? this.esp32,
      raspberryPi: raspberryPi ?? this.raspberryPi,
      foodLevelPercent: foodLevelPercent ?? this.foodLevelPercent,
      waterLevelPercent: waterLevelPercent ?? this.waterLevelPercent,
      litterWastePercent: litterWastePercent ?? this.litterWastePercent,
    );
  }
}
