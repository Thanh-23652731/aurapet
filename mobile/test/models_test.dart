import 'package:aurapet_mobile/core/api.dart';
import 'package:aurapet_mobile/core/format.dart';
import 'package:aurapet_mobile/data/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Models', () {
    test('User parses role', () {
      final u = User.fromJson({'id': 'u1', 'name': 'An', 'email': 'a@b.com', 'role': 'ROLE_ADMIN'});
      expect(u.isAdmin, isTrue);
      expect(u.name, 'An');
    });

    test('Pet parses and serializes without id/owner', () {
      final p = Pet.fromJson({
        'id': 'p1', 'ownerId': 'u1', 'name': 'Luna', 'breed': 'Mèo ta', 'age': 2,
        'gender': 'FEMALE', 'neutered': true, 'weight': 4.25, 'imageUrl': '  ',
      });
      expect(p.weight, 4.25);
      expect(p.imageUrl, isNull);
      expect(p.toJson().containsKey('id'), isFalse);
      expect(p.toJson()['name'], 'Luna');
    });

    test('DeviceState parses telemetry and settings', () {
      final d = DeviceState.fromJson({
        'brokerConnected': true,
        'online': true,
        'bowlWeightGram': 12.5,
        'foodLevelPercent': 66,
        'feeding': false,
        'waterOnline': true,
        'waterPercent': 15,
        'lowWater': true,
        'waterSensorOk': true,
        'litterState': 'cat_inside',
        'lastLitterVisitAt': '2026-10-27T06:10:00Z',
        'lastLitterVisitSec': 95,
        'lastFeedAt': '2026-10-27T05:52:16Z',
        'settings': {'targetGram': 25, 'autoMode': true, 'scheduleTimes': ['07:00', '18:00']},
      });
      expect(d.online, isTrue);
      expect(d.waterOnline, isTrue);
      expect(d.waterPercent, 15);
      expect(d.lowWater, isTrue);
      expect(d.litterState, 'cat_inside');
      expect(d.lastLitterVisitSec, 95);
      expect(d.lastLitterCleanAt, isNull);
      expect(d.bowlWeightGram, 12.5);
      expect(d.settings.targetGram, 25);
      expect(d.settings.scheduleTimes, ['07:00', '18:00']);
      expect(d.lastFeedAt, isNotNull);
    });

    test('DeviceState tolerates missing fields', () {
      final d = DeviceState.fromJson({});
      expect(d.online, isFalse);
      expect(d.foodLevelPercent, -1);
      expect(d.waterPercent, -1);
      expect(d.waterOnline, isFalse);
      expect(d.settings.targetGram, 30);
    });

    test('DashboardStats parses days and alerts', () {
      final s = DashboardStats.fromJson({
        'todayFoodGram': 56,
        'feedingSessionsCount': 2,
        'last7Days': [
          {'date': '2026-10-26', 'gram': 120, 'meals': 4},
          {'date': '2026-10-27', 'gram': 56, 'meals': 2},
        ],
        'latestAlerts': [
          {'level': 'WARNING', 'message': 'Sắp hết hạt', 'createdAt': '2026-10-27T05:00:00Z'},
        ],
      });
      expect(s.last7Days.length, 2);
      expect(s.latestAlerts.first.level, 'WARNING');
    });

    test('HealthSummary parses notes', () {
      final h = HealthSummary.fromJson({
        'healthScore': 88, 'status': 'Tốt', 'dailyAvgFoodGram': 60.0, 'expectedDailyFoodGram': 64,
        'weeklyLitterVisits': 12, 'weightChangeKg': 0.03, 'notes': ['Lượng ăn ổn định'],
      });
      expect(h.healthScore, 88);
      expect(h.notes.single, 'Lượng ăn ổn định');
    });
  });

  group('Helpers', () {
    test('normalize server url', () {
      expect(ApiClient.normalize('192.168.1.5:8080/'), 'http://192.168.1.5:8080');
      expect(ApiClient.normalize('https://api.example.com//'), 'https://api.example.com');
    });

    test('time formatting', () {
      expect(two(7), '07');
      expect(hhmm(DateTime(2026, 1, 1, 7, 5)), '07:05');
      expect(weekday(DateTime(2026, 10, 26)), 'T2');
      expect(ago(DateTime.now()), 'vừa xong');
    });
  });
}
