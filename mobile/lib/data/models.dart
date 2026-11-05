// Model dữ liệu khớp với API backend AuraPet v2.

int _int(dynamic v, [int d = 0]) => v is num ? v.toInt() : int.tryParse('$v') ?? d;
double _dbl(dynamic v, [double d = 0]) => v is num ? v.toDouble() : double.tryParse('$v') ?? d;
bool _bool(dynamic v, [bool d = false]) => v is bool ? v : d;
DateTime? _date(dynamic v) => v == null ? null : DateTime.tryParse('$v')?.toLocal();
List<dynamic> _list(dynamic v) => v is List ? v : const [];
Map<String, dynamic> _map(dynamic v) => v is Map<String, dynamic> ? v : const {};
String? _nonEmpty(dynamic v) => (v is String && v.trim().isNotEmpty) ? v.trim() : null;

class User {
  final String id;
  final String name;
  final String email;
  final String role;

  const User({required this.id, required this.name, required this.email, required this.role});

  bool get isAdmin => role == 'ROLE_ADMIN';

  factory User.fromJson(Map<String, dynamic> j) => User(
        id: '${j['id'] ?? ''}',
        name: '${j['name'] ?? ''}',
        email: '${j['email'] ?? ''}',
        role: '${j['role'] ?? 'ROLE_USER'}',
      );
}

class Pet {
  final String id;
  final String ownerId;
  final String name;
  final String breed;
  final int age;
  final String gender; // MALE | FEMALE
  final bool neutered;
  final double weight;
  final String? imageUrl;

  const Pet({
    required this.id,
    required this.ownerId,
    required this.name,
    required this.breed,
    required this.age,
    required this.gender,
    required this.neutered,
    required this.weight,
    this.imageUrl,
  });

  bool get isMale => gender == 'MALE';

  factory Pet.fromJson(Map<String, dynamic> j) => Pet(
        id: '${j['id'] ?? ''}',
        ownerId: '${j['ownerId'] ?? ''}',
        name: '${j['name'] ?? ''}',
        breed: '${j['breed'] ?? ''}',
        age: _int(j['age']),
        gender: '${j['gender'] ?? 'FEMALE'}',
        neutered: _bool(j['neutered']),
        weight: _dbl(j['weight']),
        imageUrl: _nonEmpty(j['imageUrl']),
      );

  Map<String, dynamic> toJson() => {
        'name': name,
        'breed': breed,
        'age': age,
        'gender': gender,
        'neutered': neutered,
        'weight': weight,
        'imageUrl': imageUrl ?? '',
      };
}

class DeviceSettings {
  final int targetGram;
  final bool autoMode;
  final bool scheduleEnabled;
  final List<String> scheduleTimes;
  final String? feederPetId;
  final String? feederPetName;
  final bool pumpAuto;
  final int pumpSeconds;

  const DeviceSettings({
    this.targetGram = 30,
    this.autoMode = false,
    this.scheduleEnabled = false,
    this.scheduleTimes = const [],
    this.feederPetId,
    this.feederPetName,
    this.pumpAuto = false,
    this.pumpSeconds = 30,
  });

  factory DeviceSettings.fromJson(Map<String, dynamic> j) => DeviceSettings(
        targetGram: _int(j['targetGram'], 30),
        autoMode: _bool(j['autoMode']),
        scheduleEnabled: _bool(j['scheduleEnabled']),
        scheduleTimes: _list(j['scheduleTimes']).map((e) => '$e').toList(),
        feederPetId: j['feederPetId'] as String?,
        feederPetName: j['feederPetName'] as String?,
        pumpAuto: _bool(j['pumpAuto']),
        pumpSeconds: _int(j['pumpSeconds'], 30),
      );
}

class DeviceState {
  final bool brokerConnected;
  final bool online;
  final DateTime? lastSeen;
  final double bowlWeightGram;
  final int foodLevelPercent; // -1 = chưa đo được
  final bool lowFood;
  final bool catPresent;
  final bool feeding;
  final int feedCountSinceBoot;
  final String? ip;
  final int? wifiRssi;
  final bool pumpSupported;
  final bool pumpOn;
  final DateTime? lastFeedAt;
  final int? lastFeedGram;
  final String? lastFeedSource;
  final DeviceSettings settings;

  const DeviceState({
    this.brokerConnected = false,
    this.online = false,
    this.lastSeen,
    this.bowlWeightGram = 0,
    this.foodLevelPercent = -1,
    this.lowFood = false,
    this.catPresent = false,
    this.feeding = false,
    this.feedCountSinceBoot = 0,
    this.ip,
    this.wifiRssi,
    this.pumpSupported = false,
    this.pumpOn = false,
    this.lastFeedAt,
    this.lastFeedGram,
    this.lastFeedSource,
    this.settings = const DeviceSettings(),
  });

  factory DeviceState.fromJson(Map<String, dynamic> j) => DeviceState(
        brokerConnected: _bool(j['brokerConnected']),
        online: _bool(j['online']),
        lastSeen: _date(j['lastSeen']),
        bowlWeightGram: _dbl(j['bowlWeightGram']),
        foodLevelPercent: j['foodLevelPercent'] == null ? -1 : _int(j['foodLevelPercent'], -1),
        lowFood: _bool(j['lowFood']),
        catPresent: _bool(j['catPresent']),
        feeding: _bool(j['feeding']),
        feedCountSinceBoot: _int(j['feedCountSinceBoot']),
        ip: j['ip'] as String?,
        wifiRssi: j['wifiRssi'] == null ? null : _int(j['wifiRssi']),
        pumpSupported: _bool(j['pumpSupported']),
        pumpOn: _bool(j['pumpOn']),
        lastFeedAt: _date(j['lastFeedAt']),
        lastFeedGram: j['lastFeedGram'] == null ? null : _int(j['lastFeedGram']),
        lastFeedSource: j['lastFeedSource'] as String?,
        settings: DeviceSettings.fromJson(_map(j['settings'])),
      );
}

class FeedingLog {
  final String id;
  final String petName;
  final int foodGram;
  final String? source;
  final DateTime? timestamp;

  const FeedingLog({required this.id, required this.petName, required this.foodGram, this.source, this.timestamp});

  factory FeedingLog.fromJson(Map<String, dynamic> j) => FeedingLog(
        id: '${j['id'] ?? ''}',
        petName: '${j['petName'] ?? ''}',
        foodGram: _int(j['foodGram']),
        source: j['source'] as String?,
        timestamp: _date(j['timestamp']),
      );
}

class DailyFood {
  final DateTime date;
  final int gram;
  final int meals;

  const DailyFood({required this.date, required this.gram, required this.meals});

  factory DailyFood.fromJson(Map<String, dynamic> j) => DailyFood(
        date: DateTime.tryParse('${j['date']}') ?? DateTime.now(),
        gram: _int(j['gram']),
        meals: _int(j['meals']),
      );
}

class AlertItem {
  final String level; // INFO | WARNING | CRITICAL
  final String message;
  final DateTime? createdAt;

  const AlertItem({required this.level, required this.message, this.createdAt});

  factory AlertItem.fromJson(Map<String, dynamic> j) => AlertItem(
        level: '${j['level'] ?? 'INFO'}',
        message: '${j['message'] ?? ''}',
        createdAt: _date(j['createdAt']),
      );
}

class DashboardStats {
  final int totalPets;
  final int todayFoodGram;
  final int feedingSessionsCount;
  final List<DailyFood> last7Days;
  final List<AlertItem> latestAlerts;

  const DashboardStats({
    this.totalPets = 0,
    this.todayFoodGram = 0,
    this.feedingSessionsCount = 0,
    this.last7Days = const [],
    this.latestAlerts = const [],
  });

  factory DashboardStats.fromJson(Map<String, dynamic> j) => DashboardStats(
        totalPets: _int(j['totalPets']),
        todayFoodGram: _int(j['todayFoodGram']),
        feedingSessionsCount: _int(j['feedingSessionsCount']),
        last7Days: _list(j['last7Days']).map((e) => DailyFood.fromJson(_map(e))).toList(),
        latestAlerts: _list(j['latestAlerts']).map((e) => AlertItem.fromJson(_map(e))).toList(),
      );
}

class HealthSummary {
  final int healthScore;
  final String status;
  final double dailyAvgFoodGram;
  final int expectedDailyFoodGram;
  final int weeklyLitterVisits;
  final double weightChangeKg;
  final List<String> notes;

  const HealthSummary({
    required this.healthScore,
    required this.status,
    required this.dailyAvgFoodGram,
    required this.expectedDailyFoodGram,
    required this.weeklyLitterVisits,
    required this.weightChangeKg,
    required this.notes,
  });

  factory HealthSummary.fromJson(Map<String, dynamic> j) => HealthSummary(
        healthScore: _int(j['healthScore']),
        status: '${j['status'] ?? ''}',
        dailyAvgFoodGram: _dbl(j['dailyAvgFoodGram']),
        expectedDailyFoodGram: _int(j['expectedDailyFoodGram']),
        weeklyLitterVisits: _int(j['weeklyLitterVisits']),
        weightChangeKg: _dbl(j['weightChangeKg']),
        notes: _list(j['notes']).map((e) => '$e').toList(),
      );
}

class WeightPoint {
  final double weight;
  final DateTime? recordedAt;

  const WeightPoint({required this.weight, this.recordedAt});

  factory WeightPoint.fromJson(Map<String, dynamic> j) =>
      WeightPoint(weight: _dbl(j['weight']), recordedAt: _date(j['recordedAt']));
}
