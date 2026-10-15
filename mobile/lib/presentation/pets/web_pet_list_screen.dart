import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/constants/app_colors.dart';
import '../../core/widgets/web_header.dart';
import '../../data/models/pet.dart';
import '../state/pet_provider.dart';

class SelectedPetNotifier extends Notifier<Pet?> {
  @override
  Pet? build() => null;
  void select(Pet? pet) => state = pet;
}

final selectedPetProvider = NotifierProvider<SelectedPetNotifier, Pet?>(SelectedPetNotifier.new);

class WebPetListScreen extends ConsumerStatefulWidget {
  final Function(int targetTab) onNavigateTab;

  const WebPetListScreen({super.key, required this.onNavigateTab});

  @override
  ConsumerState<WebPetListScreen> createState() => _WebPetListScreenState();
}

class _WebPetListScreenState extends ConsumerState<WebPetListScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final petState = ref.watch(petListProvider);
    final pets = petState.pets;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return RefreshIndicator(
      onRefresh: () => ref.read(petListProvider.notifier).loadPets(),
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Watermark Web Header
            const WebHeader(
              watermark: 'LOVE PET',
              titlePrefix: 'DANH SÁCH',
              titleHighlight: 'THÚ CƯNG',
            ),
            const SizedBox(height: 8),

            // Search Bar
            Container(
              decoration: BoxDecoration(
                color: isDark ? const Color(0xFF1E1E24) : Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isDark ? const Color(0xFF2E2E38) : const Color(0xFFE2E8F0),
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.04),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: TextField(
                controller: _searchController,
                onChanged: (val) {
                  ref.read(petListProvider.notifier).setSearchQuery(val);
                },
                decoration: InputDecoration(
                  hintText: 'Tìm kiếm bé mèo theo tên, giống loài...',
                  hintStyle: GoogleFonts.nunito(
                    fontSize: 14,
                    color: isDark ? const Color(0xFF8E8E98) : const Color(0xFF94A3B8),
                  ),
                  prefixIcon: const Icon(Icons.search_rounded, color: AppColors.primary),
                  suffixIcon: _searchController.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear_rounded, size: 18),
                          onPressed: () {
                            _searchController.clear();
                            ref.read(petListProvider.notifier).setSearchQuery('');
                          },
                        )
                      : null,
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(vertical: 14),
                ),
              ),
            ),
            const SizedBox(height: 20),

            // 2-Column Responsive Grid
            if (petState.isLoading && pets.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(color: AppColors.primary),
                ),
              )
            else
              GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  crossAxisSpacing: 14,
                  mainAxisSpacing: 16,
                  childAspectRatio: 0.62,
                ),
                itemCount: pets.length + 1, // + 1 for Add Pet Card
                itemBuilder: (context, index) {
                  if (index < pets.length) {
                    final pet = pets[index];
                    return _PetGridCard(
                      pet: pet,
                      onTap: () {
                        ref.read(selectedPetProvider.notifier).select(pet);
                        widget.onNavigateTab(2); // Go to Pet Details
                      },
                      onEdit: () => _showEditPetModal(context, pet),
                      onDelete: () => _confirmDeletePet(context, pet),
                    );
                  } else {
                    return _AddPetGridCard(
                      onTap: () => _showAddPetModal(context),
                    );
                  }
                },
              ),
            const SizedBox(height: 90), // Space for floating elements
          ],
        ),
      ),
    );
  }

  void _confirmDeletePet(BuildContext context, Pet pet) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            const Icon(Icons.warning_amber_rounded, color: AppColors.danger),
            const SizedBox(width: 8),
            Text('Xác nhận xóa', style: GoogleFonts.quicksand(fontWeight: FontWeight.bold)),
          ],
        ),
        content: Text(
          'Bạn có chắc chắn muốn xóa bé mèo "${pet.name}" khỏi hệ thống Smart Pet Care?',
          style: GoogleFonts.nunito(fontSize: 14),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: Text('HỦY', style: GoogleFonts.quicksand(fontWeight: FontWeight.w700)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.danger,
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: () async {
              Navigator.of(ctx).pop();
              final success = await ref.read(petListProvider.notifier).deletePet(pet.id);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success ? 'Đã xóa bé ${pet.name}!' : 'Lỗi khi xóa thú cưng'),
                    backgroundColor: success ? AppColors.success : AppColors.danger,
                  ),
                );
              }
            },
            child: Text('XÓA', style: GoogleFonts.quicksand(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }

  void _showAddPetModal(BuildContext context) {
    _showPetFormDialog(context: context, isEdit: false);
  }

  void _showEditPetModal(BuildContext context, Pet pet) {
    _showPetFormDialog(context: context, isEdit: true, existingPet: pet);
  }

  void _showPetFormDialog({
    required BuildContext context,
    required bool isEdit,
    Pet? existingPet,
  }) {
    final nameCtrl = TextEditingController(text: existingPet?.name ?? '');
    final breedCtrl = TextEditingController(text: existingPet?.breed ?? 'Mèo ta (Normal)');
    final ageCtrl = TextEditingController(text: existingPet != null ? '${existingPet.age}' : '1');
    final weightCtrl = TextEditingController(text: existingPet != null ? '${existingPet.weight}' : '3.5');
    final imageCtrl = TextEditingController(text: existingPet?.imageUrl ?? 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=500&auto=format&fit=crop&q=60');
    String selectedGender = existingPet?.gender ?? 'FEMALE';
    bool isNeutered = existingPet?.neutered ?? true;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (modalCtx, setModalState) {
          final isDark = Theme.of(ctx).brightness == Brightness.dark;
          return Container(
            padding: EdgeInsets.only(
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
              top: 24,
              left: 20,
              right: 20,
            ),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF1E1E24) : Colors.white,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        isEdit ? 'Chỉnh Sửa Bé Mèo' : 'Thêm Thú Cưng Mới',
                        style: GoogleFonts.quicksand(
                          fontSize: 20,
                          fontWeight: FontWeight.w800,
                          color: AppColors.primary,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () => Navigator.of(ctx).pop(),
                      ),
                    ],
                  ),
                  const Divider(),
                  const SizedBox(height: 12),

                  TextField(
                    controller: nameCtrl,
                    decoration: const InputDecoration(labelText: 'Tên bé mèo *', prefixIcon: Icon(Icons.pets)),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: breedCtrl,
                    decoration: const InputDecoration(labelText: 'Giống loài', prefixIcon: Icon(Icons.category_outlined)),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: ageCtrl,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(labelText: 'Tuổi (tuần/năm)', prefixIcon: Icon(Icons.cake_outlined)),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextField(
                          controller: weightCtrl,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(labelText: 'Cân nặng (kg)', prefixIcon: Icon(Icons.monitor_weight_outlined)),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: selectedGender,
                          decoration: const InputDecoration(labelText: 'Giới tính'),
                          items: const [
                            DropdownMenuItem(value: 'FEMALE', child: Text('Cái')),
                            DropdownMenuItem(value: 'MALE', child: Text('Đực')),
                          ],
                          onChanged: (val) => setModalState(() => selectedGender = val!),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: CheckboxListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text('Triệt sản', style: GoogleFonts.nunito(fontSize: 13, fontWeight: FontWeight.w600)),
                          value: isNeutered,
                          activeColor: AppColors.primary,
                          onChanged: (val) => setModalState(() => isNeutered = val ?? false),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),

                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      ),
                      onPressed: () async {
                        final name = nameCtrl.text.trim();
                        if (name.isEmpty) return;
                        final breed = breedCtrl.text.trim();
                        final age = int.tryParse(ageCtrl.text.trim()) ?? 1;
                        final weight = double.tryParse(weightCtrl.text.trim()) ?? 3.0;

                        if (isEdit && existingPet != null) {
                          await ref.read(petListProvider.notifier).updatePet(
                            existingPet.id,
                            {
                              'name': name,
                              'breed': breed.isNotEmpty ? breed : existingPet.breed,
                              'age': age,
                              'weight': weight,
                              'gender': selectedGender,
                              'neutered': isNeutered,
                            },
                          );
                        } else {
                          await ref.read(petListProvider.notifier).createPet(
                            {
                              'name': name,
                              'breed': breed.isNotEmpty ? breed : 'Mèo ta',
                              'age': age,
                              'gender': selectedGender,
                              'neutered': isNeutered,
                              'weight': weight,
                              'imageUrl': imageCtrl.text.trim(),
                            },
                          );
                        }
                        if (context.mounted) Navigator.of(ctx).pop();
                      },
                      child: Text(
                        isEdit ? 'LƯU THAY ĐỔI' : 'THÊM BÉ MÈO',
                        style: GoogleFonts.quicksand(fontSize: 15, fontWeight: FontWeight.w800),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _PetGridCard extends StatelessWidget {
  final Pet pet;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const _PetGridCard({
    required this.pet,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final avatarUrl = pet.imageUrl != null && pet.imageUrl!.isNotEmpty
        ? pet.imageUrl!
        : 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=500&auto=format&fit=crop&q=60';

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(24),
      child: Container(
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFFFFA8C0), Color(0xFFFF8FAD)],
          ),
          borderRadius: BorderRadius.circular(24),
          boxShadow: [
            BoxShadow(
              color: const Color(0xFFFF7A9E).withOpacity(0.35),
              blurRadius: 16,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: Stack(
          children: [
            // Decorative Paw Icon at bottom right
            Positioned(
              right: -10,
              bottom: -10,
              child: IgnorePointer(
                child: Icon(
                  Icons.pets,
                  size: 90,
                  color: Colors.white.withOpacity(0.18),
                ),
              ),
            ),

            // Top action buttons (Edit & Delete)
            Positioned(
              top: 6,
              right: 6,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  GestureDetector(
                    onTap: onEdit,
                    child: Container(
                      padding: const EdgeInsets.all(5),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.35),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.edit_outlined, size: 15, color: Colors.white),
                    ),
                  ),
                  const SizedBox(width: 4),
                  GestureDetector(
                    onTap: onDelete,
                    child: Container(
                      padding: const EdgeInsets.all(5),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.35),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.delete_outline, size: 15, color: Colors.white),
                    ),
                  ),
                ],
              ),
            ),

            // Main Card Content
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 14, 12, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // Circular Cat Avatar with 3.5px white border
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(color: Colors.white, width: 3.5),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.12),
                          blurRadius: 8,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: ClipOval(
                      child: Image.network(
                        avatarUrl,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => Container(
                          color: Colors.white,
                          child: const Icon(Icons.pets, color: AppColors.primary, size: 36),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),

                  // Pet Name
                  Text(
                    pet.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.quicksand(
                      fontSize: 18,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 8),

                  // Info items list
                  _PetCardInfoLine(label: 'Tuổi', value: '${pet.age} tuổi'),
                  _PetCardInfoLine(label: 'Giới tính', value: pet.gender == 'MALE' ? 'Đực' : 'Cái'),
                  _PetCardInfoLine(label: 'Giống loài', value: pet.breed),
                  _PetCardInfoLine(label: 'Triệt sản', value: pet.neutered ? 'Đã triệt sản' : 'Chưa triệt sản'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PetCardInfoLine extends StatelessWidget {
  final String label;
  final String value;

  const _PetCardInfoLine({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '$label : ',
            style: GoogleFonts.nunito(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: Colors.white.withOpacity(0.9),
            ),
          ),
          Flexible(
            child: Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.nunito(
                fontSize: 11,
                fontWeight: FontWeight.w800,
                color: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _AddPetGridCard extends StatelessWidget {
  final VoidCallback onTap;

  const _AddPetGridCard({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(24),
      child: Container(
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF1E1E24) : Colors.white,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: AppColors.primary.withOpacity(0.6),
            width: 2,
            strokeAlign: BorderSide.strokeAlignInside,
          ),
          boxShadow: [
            BoxShadow(
              color: AppColors.primary.withOpacity(0.08),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 54,
              height: 54,
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.add_rounded, size: 32, color: AppColors.primary),
            ),
            const SizedBox(height: 12),
            Text(
              '+ Thêm Thú Cưng',
              style: GoogleFonts.quicksand(
                fontSize: 15,
                fontWeight: FontWeight.w800,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Thêm bé mèo mới',
              style: GoogleFonts.nunito(
                fontSize: 11,
                color: isDark ? const Color(0xFF8E8E98) : const Color(0xFF94A3B8),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
