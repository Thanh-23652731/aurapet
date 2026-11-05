import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme.dart';
import '../state/providers.dart';

class AiScreen extends ConsumerStatefulWidget {
  const AiScreen({super.key});

  @override
  ConsumerState<AiScreen> createState() => _AiScreenState();
}

class _Msg {
  final bool user;
  final String text;
  const _Msg(this.user, this.text);
}

class _AiScreenState extends ConsumerState<AiScreen> {
  static const _suggest = ['Bé ăn như vậy có đủ không?', 'Nên chia mấy bữa một ngày?', 'Bé cần uống bao nhiêu nước?', 'Dấu hiệu nào cần đi bác sĩ?'];
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final List<_Msg> _msgs = [const _Msg(false, 'Chào bạn! Mình đọc được lượng ăn, cân nặng và trạng thái máy của bé. Bạn muốn hỏi gì?')];
  String? _petId;
  bool _busy = false;

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _ask(String q) async {
    if (q.trim().isEmpty || _busy) return;
    final history = _msgs.skip(1).toList();
    final tail = history.length > 8 ? history.sublist(history.length - 8) : history;
    setState(() {
      _msgs.add(_Msg(true, q.trim()));
      _busy = true;
    });
    _input.clear();
    _toBottom();
    String reply;
    try {
      reply = await ref.read(apiProvider).askAi(q.trim(),
          petId: _petId, history: [for (final m in tail) {'role': m.user ? 'user' : 'model', 'text': m.text}]);
    } catch (e) {
      reply = e.toString();
    }
    if (!mounted) return;
    setState(() {
      _msgs.add(_Msg(false, reply));
      _busy = false;
    });
    _toBottom();
  }

  void _toBottom() => WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      });

  @override
  Widget build(BuildContext context) {
    final pets = ref.watch(petsProvider).pets;
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
        child: Row(children: [
          Text('Hỏi về', style: TextStyle(color: context.muted)),
          const SizedBox(width: 10),
          DropdownButton<String?>(
            value: pets.any((p) => p.id == _petId) ? _petId : null,
            items: [
              const DropdownMenuItem<String?>(value: null, child: Text('Chung')),
              for (final p in pets) DropdownMenuItem<String?>(value: p.id, child: Text(p.name)),
            ],
            onChanged: (v) => setState(() => _petId = v),
          ),
        ]),
      ),
      Expanded(
        child: ListView.builder(
          controller: _scroll,
          padding: const EdgeInsets.all(12),
          itemCount: _msgs.length + (_busy ? 1 : 0),
          itemBuilder: (_, i) {
            if (i == _msgs.length) return const _Bubble(user: false, text: 'Đang suy nghĩ…', faded: true);
            return _Bubble(user: _msgs[i].user, text: _msgs[i].text);
          },
        ),
      ),
      SizedBox(
        height: 40,
        child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 12), children: [
          for (final q in _suggest)
            Padding(padding: const EdgeInsets.only(right: 6), child: ActionChip(label: Text(q), onPressed: () => _ask(q))),
        ]),
      ),
      SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 6, 12, 10),
          child: Row(children: [
            Expanded(
              child: TextField(
                controller: _input,
                minLines: 1,
                maxLines: 4,
                textInputAction: TextInputAction.send,
                onSubmitted: _ask,
                decoration: const InputDecoration(hintText: 'Ví dụ: Bé ăn như vậy có đủ không?', border: OutlineInputBorder(), isDense: true),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filled(onPressed: _busy ? null : () => _ask(_input.text), icon: const Icon(Icons.send), tooltip: 'Gửi'),
          ]),
        ),
      ),
    ]);
  }
}

class _Bubble extends StatelessWidget {
  final bool user;
  final String text;
  final bool faded;
  const _Bubble({required this.user, required this.text, this.faded = false});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: user ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.82),
        decoration: BoxDecoration(
          color: user ? context.cs.primary : context.soft,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(14),
            topRight: const Radius.circular(14),
            bottomLeft: Radius.circular(user ? 14 : 4),
            bottomRight: Radius.circular(user ? 4 : 14),
          ),
        ),
        child: Text(text, style: TextStyle(color: user ? context.cs.onPrimary : (faded ? context.muted : null))),
      ),
    );
  }
}
