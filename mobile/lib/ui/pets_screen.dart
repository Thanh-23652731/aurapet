import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../data/models.dart';
import '../state/providers.dart';
import 'widgets.dart';

class PetsScreen extends ConsumerWidget {
  const PetsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final view = ref.watch(petsProvider);
    final feederId = ref.watch(deviceProvider.select((v) => v.device?.settings.feederPetId));

    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showPetForm(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Thêm thú cưng'),
      ),
      body: RefreshIndicator(
        onRefresh: ref.read(petsProvider.notifier).reload,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 96),
          children: [
            TextField(
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Tìm theo tên hoặc giống',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              onChanged: ref.read(petsProvider.notifier).search,
            ),
            const SizedBox(height: 10),
            if (view.loading) const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
            if (view.error != null) Text(view.error!, style: const TextStyle(color: AppColors.stop)),
            if (!view.loading && view.pets.isEmpty)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Text('Chưa có thú cưng nào. Bấm "Thêm thú cưng" để bắt đầu.',
                    textAlign: TextAlign.center, style: TextStyle(color: context.muted)),
              ),
            if (view.pets.isNotEmpty)
              Panel(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Column(children: [
                  for (final p in view.pets)
                    ListTile(
                      leading: PetAvatar(p),
                      title: Row(children: [
                        Flexible(child: Text(p.name, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600))),
                        if (p.id == feederId) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 1),
                            decoration: BoxDecoration(color: AppColors.leaf.withValues(alpha: .15), borderRadius: BorderRadius.circular(99)),
                            child: const Text('Máy cho ăn', style: TextStyle(fontSize: 11, color: AppColors.leaf, fontWeight: FontWeight.w600)),
                          ),
                        ],
                      ]),
                      subtitle: Text('${p.breed}, ${p.age} tuổi, ${p.weight.toStringAsFixed(2)} kg, ${p.isMale ? 'đực' : 'cái'}'),
                      trailing: PopupMenuButton<String>(
                        tooltip: 'Tuỳ chọn',
                        onSelected: (v) => v == 'edit' ? showPetForm(context, ref, pet: p) : _confirmDelete(context, ref, p),
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'edit', child: Text('Sửa')),
                          PopupMenuItem(value: 'delete', child: Text('Xoá')),
                        ],
                      ),
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PetDetailScreen(pet: p))),
                    ),
                ]),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref, Pet p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text('Xoá ${p.name}?'),
        content: const Text('Lịch sử ăn của bé vẫn được giữ lại.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Huỷ')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.stop),
            onPressed: () => Navigator.pop(c, true),
            child: const Text('Xoá'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(apiProvider).deletePet(p.id);
      await ref.read(petsProvider.notifier).reload();
      if (context.mounted) showMessage(context, 'Đã xoá ${p.name}');
    } catch (e) {
      if (context.mounted) showMessage(context, e.toString(), error: true);
    }
  }
}

/// Form thêm / sửa thú cưng.
Future<void> showPetForm(BuildContext context, WidgetRef ref, {Pet? pet}) async {
  final saved = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => _PetForm(pet: pet),
  );
  if (saved == true) {
    await ref.read(petsProvider.notifier).reload();
    if (context.mounted) showMessage(context, pet == null ? 'Đã thêm thú cưng' : 'Đã lưu thay đổi');
  }
}

class _PetForm extends ConsumerStatefulWidget {
  final Pet? pet;
  const _PetForm({this.pet});

  @override
  ConsumerState<_PetForm> createState() => _PetFormState();
}

class _PetFormState extends ConsumerState<_PetForm> {
  final _form = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.pet?.name);
  late final _breed = TextEditingController(text: widget.pet?.breed);
  late final _age = TextEditingController(text: widget.pet?.age.toString());
  late final _weight = TextEditingController(text: widget.pet?.weight.toString());
  late final _image = TextEditingController(text: widget.pet?.imageUrl);
  late String _gender = widget.pet?.gender ?? 'FEMALE';
  late bool _neutered = widget.pet?.neutered ?? true;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [_name, _breed, _age, _weight, _image]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final p = Pet(
      id: widget.pet?.id ?? '',
      ownerId: widget.pet?.ownerId ?? '',
      name: _name.text.trim(),
      breed: _breed.text.trim(),
      age: int.parse(_age.text.trim()),
      gender: _gender,
      neutered: _neutered,
      weight: double.parse(_weight.text.trim().replaceAll(',', '.')),
      imageUrl: _image.text.trim().isEmpty ? null : _image.text.trim(),
    );
    try {
      await ref.read(apiProvider).savePet(p, id: widget.pet?.id);
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      setState(() {
        _busy = false;
        _error = e.toString();
      });
    }
  }

  String? _required(String? v) => (v == null || v.trim().isEmpty) ? 'Bắt buộc' : null;

  @override
  Widget build(BuildContext context) {
    const gap = SizedBox(height: 10, width: 10);
    return Padding(
      padding: EdgeInsets.fromLTRB(16, 0, 16, 16 + MediaQuery.of(context).viewInsets.bottom),
      child: Form(
        key: _form,
        child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
            Text(widget.pet == null ? 'Thêm thú cưng' : 'Sửa ${widget.pet!.name}', style: context.tt.titleLarge),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: TextFormField(controller: _name, decoration: const InputDecoration(labelText: 'Tên'), validator: _required)),
              gap,
              Expanded(child: TextFormField(controller: _breed, decoration: const InputDecoration(labelText: 'Giống'), validator: _required)),
            ]),
            gap,
            Row(children: [
              Expanded(
                child: TextFormField(
                  controller: _age,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Tuổi'),
                  validator: (v) {
                    final n = int.tryParse(v?.trim() ?? '');
                    return (n == null || n < 0 || n > 40) ? 'Từ 0 đến 40' : null;
                  },
                ),
              ),
              gap,
              Expanded(
                child: TextFormField(
                  controller: _weight,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Cân nặng (kg)'),
                  validator: (v) {
                    final n = double.tryParse((v ?? '').trim().replaceAll(',', '.'));
                    return (n == null || n < 0.1 || n > 30) ? 'Từ 0.1 đến 30' : null;
                  },
                ),
              ),
            ]),
            gap,
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'FEMALE', label: Text('Cái')),
                ButtonSegment(value: 'MALE', label: Text('Đực')),
              ],
              selected: {_gender},
              onSelectionChanged: (s) => setState(() => _gender = s.first),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Đã triệt sản'),
              value: _neutered,
              onChanged: (v) => setState(() => _neutered = v),
            ),
            TextFormField(controller: _image, keyboardType: TextInputType.url, decoration: const InputDecoration(labelText: 'Link ảnh (tuỳ chọn)')),
            if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: const TextStyle(color: AppColors.stop))),
            const SizedBox(height: 14),
            FilledButton(onPressed: _busy ? null : _save, child: Text(_busy ? 'Đang lưu…' : 'Lưu')),
          ]),
        ),
      ),
    );
  }
}

/// Chi tiết sức khoẻ của một bé, dữ liệu thật từ backend.
class PetDetailScreen extends ConsumerStatefulWidget {
  final Pet pet;
  const PetDetailScreen({super.key, required this.pet});

  @override
  ConsumerState<PetDetailScreen> createState() => _PetDetailScreenState();
}

class _PetDetailScreenState extends ConsumerState<PetDetailScreen> {
  late Future<(HealthSummary, List<DailyFood>, List<WeightPoint>)> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<(HealthSummary, List<DailyFood>, List<WeightPoint>)> _load() async {
    final api = ref.read(apiProvider);
    final id = widget.pet.id;
    final results = await Future.wait([api.health(id), api.dailyFood(id), api.weights(id)]);
    return (results[0] as HealthSummary, results[1] as List<DailyFood>, results[2] as List<WeightPoint>);
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.pet;
    final feederId = ref.watch(deviceProvider.select((v) => v.device?.settings.feederPetId));
    return Scaffold(
      appBar: AppBar(title: Text(p.name)),
      body: FutureBuilder(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          if (snap.hasError) return Center(child: Text('${snap.error}'));
          final (h, daily, weights) = snap.data!;
          final scoreColor = h.healthScore >= 90 ? AppColors.leaf : h.healthScore >= 75 ? AppColors.warn : AppColors.stop;
          return ListView(padding: const EdgeInsets.all(12), children: [
            Panel(
              child: Row(children: [
                PetAvatar(p, size: 60),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(p.name, style: context.tt.titleLarge?.copyWith(fontWeight: FontWeight.w700)),
                    Text('${p.breed}, ${p.age} tuổi, ${p.neutered ? 'đã triệt sản' : 'chưa triệt sản'}',
                        style: TextStyle(color: context.muted)),
                  ]),
                ),
                Container(
                  width: 60,
                  height: 60,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: scoreColor, width: 5)),
                  child: Text('${h.healthScore}', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700)),
                ),
              ]),
            ),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(child: Fact('Ăn/ngày (nên ~${h.expectedDailyFoodGram} g)', '${h.dailyAvgFoodGram.round()} g')),
              const SizedBox(width: 8),
              Expanded(child: Fact('Vệ sinh/tuần', '${h.weeklyLitterVisits} lần')),
              const SizedBox(width: 8),
              Expanded(child: Fact('So với lần cân trước', '${h.weightChangeKg > 0 ? '+' : ''}${h.weightChangeKg} kg')),
            ]),
            const SizedBox(height: 10),
            Panel(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                PanelTitle(h.status, icon: Icons.favorite_border),
                const SizedBox(height: 6),
                for (final n in h.notes)
                  Padding(padding: const EdgeInsets.symmetric(vertical: 2), child: Text('• $n')),
              ]),
            ),
            const SizedBox(height: 10),
            Panel(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const PanelTitle('Lượng ăn 7 ngày'),
                const SizedBox(height: 10),
                WeekBars(days: daily),
              ]),
            ),
            const SizedBox(height: 10),
            Panel(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const PanelTitle('Cân nặng'),
                const SizedBox(height: 8),
                WeightLine(points: weights),
              ]),
            ),
            const SizedBox(height: 12),
            FilledButton.tonalIcon(
              onPressed: p.id == feederId
                  ? null
                  : () async {
                      final err = await ref.read(deviceProvider.notifier).settings({'feederPetId': p.id});
                      if (context.mounted) showMessage(context, err ?? 'Đã gán ${p.name} cho máy cho ăn', error: err != null);
                    },
              icon: const Icon(Icons.rice_bowl_outlined),
              label: Text(p.id == feederId ? 'Bé đang dùng máy cho ăn' : 'Gán bé cho máy cho ăn'),
            ),
          ]);
        },
      ),
    );
  }
}
