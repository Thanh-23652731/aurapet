import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

/// Tự tìm máy chủ AuraPet trong cùng mạng Wi-Fi bằng UDP broadcast (cổng 8888).
/// Chỉ dùng trên Android/iOS/desktop, không dùng trên web.
class ServerDiscovery {
  static const int port = 8888;
  static const String request = 'AURAPET_DISCOVER';
  static const String prefix = 'AURAPET_SERVER:';

  static Future<String?> find({Duration timeout = const Duration(seconds: 3)}) async {
    if (kIsWeb) return null;
    RawDatagramSocket? socket;
    try {
      socket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0);
      socket.broadcastEnabled = true;
      final completer = Completer<String?>();
      final bytes = utf8.encode(request);

      socket.send(bytes, InternetAddress('255.255.255.255'), port);
      try {
        for (final iface in await NetworkInterface.list(type: InternetAddressType.IPv4)) {
          for (final addr in iface.addresses) {
            final parts = addr.address.split('.');
            if (!addr.isLoopback && parts.length == 4) {
              socket.send(bytes, InternetAddress('${parts[0]}.${parts[1]}.${parts[2]}.255'), port);
            }
          }
        }
      } catch (_) {
        // một số máy không cho liệt kê card mạng
      }

      final s = socket;
      final sub = s.listen((event) {
        if (event != RawSocketEvent.read) return;
        final dg = s.receive();
        if (dg == null) return;
        final text = utf8.decode(dg.data, allowMalformed: true).trim();
        if (text.startsWith(prefix) && !completer.isCompleted) {
          completer.complete(text.substring(prefix.length).trim());
        }
      });
      final result = await completer.future.timeout(timeout, onTimeout: () => null);
      await sub.cancel();
      return result;
    } catch (e) {
      debugPrint('[Discovery] $e');
      return null;
    } finally {
      socket?.close();
    }
  }
}
