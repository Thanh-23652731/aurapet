class HealthSummary {
  final String petId;
  final String petName;
  final int healthScore;
  final String status;
  final double dailyAvgFoodGram;
  final int weeklyLitterVisits;
  final double weightChangeKg;
  final String aiRecommendation;

  const HealthSummary({
    required this.petId,
    required this.petName,
    required this.healthScore,
    required this.status,
    required this.dailyAvgFoodGram,
    required this.weeklyLitterVisits,
    required this.weightChangeKg,
    required this.aiRecommendation,
  });

  factory HealthSummary.fromJson(Map<String, dynamic> json) {
    return HealthSummary(
      petId: json['petId'] as String? ?? '',
      petName: json['petName'] as String? ?? '',
      healthScore: json['healthScore'] as int? ?? 90,
      status: json['status'] as String? ?? 'Optimal',
      dailyAvgFoodGram: (json['dailyAvgFoodGram'] as num?)?.toDouble() ?? 0.0,
      weeklyLitterVisits: json['weeklyLitterVisits'] as int? ?? 0,
      weightChangeKg: (json['weightChangeKg'] as num?)?.toDouble() ?? 0.0,
      aiRecommendation: json['aiRecommendation'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'petId': petId,
      'petName': petName,
      'healthScore': healthScore,
      'status': status,
      'dailyAvgFoodGram': dailyAvgFoodGram,
      'weeklyLitterVisits': weeklyLitterVisits,
      'weightChangeKg': weightChangeKg,
      'aiRecommendation': aiRecommendation,
    };
  }
}
