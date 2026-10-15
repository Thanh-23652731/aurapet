class Pet {
  final String id;
  final String ownerId;
  final String name;
  final String breed;
  final int age;
  final String gender; // MALE or FEMALE
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

  factory Pet.fromJson(Map<String, dynamic> json) {
    return Pet(
      id: json['id'] as String? ?? '',
      ownerId: json['ownerId'] as String? ?? '',
      name: json['name'] as String? ?? '',
      breed: json['breed'] as String? ?? '',
      age: json['age'] as int? ?? 0,
      gender: json['gender'] as String? ?? 'FEMALE',
      neutered: json['neutered'] as bool? ?? false,
      weight: (json['weight'] as num?)?.toDouble() ?? 0.0,
      imageUrl: json['imageUrl'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'ownerId': ownerId,
      'name': name,
      'breed': breed,
      'age': age,
      'gender': gender,
      'neutered': neutered,
      'weight': weight,
      'imageUrl': imageUrl,
    };
  }

  Pet copyWith({
    String? id,
    String? ownerId,
    String? name,
    String? breed,
    int? age,
    String? gender,
    bool? neutered,
    double? weight,
    String? imageUrl,
  }) {
    return Pet(
      id: id ?? this.id,
      ownerId: ownerId ?? this.ownerId,
      name: name ?? this.name,
      breed: breed ?? this.breed,
      age: age ?? this.age,
      gender: gender ?? this.gender,
      neutered: neutered ?? this.neutered,
      weight: weight ?? this.weight,
      imageUrl: imageUrl ?? this.imageUrl,
    );
  }
}
