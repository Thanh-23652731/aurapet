class FeedingLog {
  final String id;
  final String petId;
  final String? petName;
  final int foodGram;
  final DateTime timestamp;

  const FeedingLog({
    required this.id,
    required this.petId,
    this.petName,
    required this.foodGram,
    required this.timestamp,
  });

  factory FeedingLog.fromJson(Map<String, dynamic> json) {
    return FeedingLog(
      id: json['id'] as String? ?? '',
      petId: json['petId'] as String? ?? '',
      petName: json['petName'] as String?,
      foodGram: json['foodGram'] as int? ?? 0,
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'petId': petId,
      if (petName != null) 'petName': petName,
      'foodGram': foodGram,
      'timestamp': timestamp.toIso8601String(),
    };
  }
}
