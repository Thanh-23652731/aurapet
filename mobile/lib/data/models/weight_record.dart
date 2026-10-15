class WeightRecord {
  final String id;
  final String petId;
  final double weight;
  final DateTime recordedAt;

  const WeightRecord({
    required this.id,
    required this.petId,
    required this.weight,
    required this.recordedAt,
  });

  factory WeightRecord.fromJson(Map<String, dynamic> json) {
    return WeightRecord(
      id: json['id'] as String? ?? '',
      petId: json['petId'] as String? ?? '',
      weight: (json['weight'] as num?)?.toDouble() ?? 0.0,
      recordedAt: json['recordedAt'] != null
          ? DateTime.tryParse(json['recordedAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'petId': petId,
      'weight': weight,
      'recordedAt': recordedAt.toIso8601String(),
    };
  }
}
