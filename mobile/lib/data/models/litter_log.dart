class LitterLog {
  final String id;
  final String petId;
  final String result; // NORMAL, ABNORMAL, DIARRHEA, CONSTIPATION
  final String? imageUrl;
  final DateTime timestamp;

  const LitterLog({
    required this.id,
    required this.petId,
    required this.result,
    this.imageUrl,
    required this.timestamp,
  });

  factory LitterLog.fromJson(Map<String, dynamic> json) {
    return LitterLog(
      id: json['id'] as String? ?? '',
      petId: json['petId'] as String? ?? '',
      result: json['result'] as String? ?? 'NORMAL',
      imageUrl: json['imageUrl'] as String?,
      timestamp: json['timestamp'] != null
          ? DateTime.tryParse(json['timestamp'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'petId': petId,
      'result': result,
      'imageUrl': imageUrl,
      'timestamp': timestamp.toIso8601String(),
    };
  }
}
