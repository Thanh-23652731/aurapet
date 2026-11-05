import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/format.dart';
import '../core/theme.dart';
import '../data/models.dart';
import '../state/providers.dart';
import 'widgets.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  late Future<List<FeedingLog>> _future = ref.read(apiProvider).feedings();

  Future<void> _reload() async {
    setState(() => _future = ref.read(apiProvider).feedings());
    await ref.read(statsProvider.notifier).refresh();
  }

  @override
  Widget build(BuildContext context) {
    final stats = ref.watch(statsProvider);
    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView(padding: const EdgeInsets.fromLTRB(12, 8, 12, 20), children: [
        Panel(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            PanelTitle('Lượng ăn 7 ngày',
                trailing: stats == null ? null : Text('Hôm nay ${stats.todayFoodGram} g', style: TextStyle(color: context.muted))),
            const SizedBox(height: 10),
            WeekBars(days: stats?.last7Days ?? const [], height: 150),
          ]),
        ),
        const SizedBox(height: 12),
        Panel(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 6),
          child: FutureBuilder<List<FeedingLog>>(
            future: _future,
            builder: (context, snap) {
              if (snap.connectionState != ConnectionState.done) {
                return const Padding(padding: EdgeInsets.all(20), child: Center(child: CircularProgressIndicator()));
              }
              if (snap.hasError) return Text('${snap.error}', style: const TextStyle(color: AppColors.stop));
              final rows = snap.data ?? const [];
              return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const PanelTitle('Các lần cho ăn'),
                const SizedBox(height: 6),
                if (rows.isEmpty) Padding(padding: const EdgeInsets.all(12), child: Text('Chưa có lần cho ăn nào.', style: TextStyle(color: context.muted))),
                for (final r in rows)
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(border: Border(bottom: BorderSide(color: context.soft))),
                    child: Row(children: [
                      SizedBox(width: 92, child: Text(timeLabel(r.timestamp), style: TextStyle(color: context.muted, fontSize: 13))),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(r.petName, style: const TextStyle(fontWeight: FontWeight.w600)),
                          if (r.source != null) Text(r.source!, style: TextStyle(color: context.muted, fontSize: 12)),
                        ]),
                      ),
                      Text('${r.foodGram} g', style: const TextStyle(fontWeight: FontWeight.w700, fontFeatures: [FontFeature.tabularFigures()])),
                    ]),
                  ),
              ]);
            },
          ),
        ),
      ]),
    );
  }
}
