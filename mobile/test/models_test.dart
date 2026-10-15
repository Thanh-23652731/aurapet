import 'package:flutter_test/flutter_test.dart';
import 'package:aurapet_mobile/data/models/user.dart';
import 'package:aurapet_mobile/data/models/pet.dart';
import 'package:aurapet_mobile/data/models/system_status.dart';
import 'package:aurapet_mobile/data/models/feeding_log.dart';
import 'package:aurapet_mobile/data/models/litter_log.dart';
import 'package:aurapet_mobile/data/models/alert.dart';
import 'package:aurapet_mobile/data/models/health_summary.dart';

void main() {
  group('OpenAPI Schema Models Serialization Tests', () {
    test('User model parses correctly', () {
      final json = {
        'id': 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'name': 'Alex Johnson',
        'email': 'admin@aurapet.com',
        'role': 'ROLE_ADMIN',
      };

      final user = User.fromJson(json);
      expect(user.id, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11');
      expect(user.name, 'Alex Johnson');
      expect(user.email, 'admin@aurapet.com');
      expect(user.isAdmin, isTrue);
    });

    test('Pet model parses and serializes correctly', () {
      final json = {
        'id': 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'ownerId': 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
        'name': 'Luna',
        'breed': 'British Shorthair',
        'age': 2,
        'gender': 'FEMALE',
        'neutered': true,
        'weight': 4.25,
        'imageUrl': 'https://example.com/luna.jpg',
      };

      final pet = Pet.fromJson(json);
      expect(pet.name, 'Luna');
      expect(pet.breed, 'British Shorthair');
      expect(pet.gender, 'FEMALE');
      expect(pet.neutered, isTrue);
      expect(pet.weight, 4.25);

      final outJson = pet.toJson();
      expect(outJson['name'], 'Luna');
      expect(outJson['weight'], 4.25);
    });

    test('SystemStatus parses correctly', () {
      final json = {
        'feeder': true,
        'waterPump': true,
        'litterCleaner': false,
        'esp32': 'ONLINE',
        'raspberryPi': 'ONLINE',
        'foodLevelPercent': 80,
        'waterLevelPercent': 85,
        'litterWastePercent': 20,
      };

      final status = SystemStatus.fromJson(json);
      expect(status.feeder, isTrue);
      expect(status.waterPump, isTrue);
      expect(status.litterCleaner, isFalse);
      expect(status.esp32, 'ONLINE');
      expect(status.raspberryPi, 'ONLINE');
    });

    test('FeedingLog parses ISO-8601 timestamp', () {
      final json = {
        'id': 'c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
        'petId': 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'foodGram': 45,
        'timestamp': '2026-09-14T08:30:00Z',
      };

      final log = FeedingLog.fromJson(json);
      expect(log.foodGram, 45);
      expect(log.timestamp.year, 2026);
    });

    test('LitterLog parses results correctly', () {
      final json = {
        'id': 'd1eebc99-9c0b-4ef8-bb6d-6bb9bd380a44',
        'petId': 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'result': 'NORMAL',
        'timestamp': '2026-09-14T09:15:00Z',
      };

      final log = LitterLog.fromJson(json);
      expect(log.result, 'NORMAL');
    });

    test('Alert parses level and message', () {
      final json = {
        'id': 'e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
        'type': 'FEEDING',
        'level': 'INFO',
        'message': 'Dispensed food successfully',
        'createdAt': '2026-09-14T08:30:00Z',
      };

      final alert = Alert.fromJson(json);
      expect(alert.level, 'INFO');
      expect(alert.type, 'FEEDING');
      expect(alert.message, 'Dispensed food successfully');
    });

    test('HealthSummary parses Edge AI recommendation', () {
      final json = {
        'petId': 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
        'petName': 'Luna',
        'healthScore': 96,
        'status': 'Optimal',
        'dailyAvgFoodGram': 120.5,
        'weeklyLitterVisits': 21,
        'weightChangeKg': 0.05,
        'aiRecommendation': 'Vital indicators are in prime zone.',
      };

      final summary = HealthSummary.fromJson(json);
      expect(summary.healthScore, 96);
      expect(summary.status, 'Optimal');
      expect(summary.dailyAvgFoodGram, 120.5);
    });
  });
}
