class Alert {
  final String id;
  final String type; // HEALTH, FEEDING, LITTER, SYSTEM
  final String level; // INFO, WARNING, CRITICAL
  final String message;
  final DateTime createdAt;

  const Alert({
    required this.id,
    required this.type,
    required this.level,
    required this.message,
    required this.createdAt,
  });

  factory Alert.fromJson(Map<String, dynamic> json) {
    return Alert(
      id: json['id'] as String? ?? '',
      type: json['type'] as String? ?? 'SYSTEM',
      level: json['level'] as String? ?? 'INFO',
      message: json['message'] as String? ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'type': type,
      'level': level,
      'message': message,
      'createdAt': createdAt.toIso8601String(),
    };
  }
}
