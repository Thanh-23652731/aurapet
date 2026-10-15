import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import '../constants/api_endpoints.dart';
import 'dio_client.dart';

class ServerDiscoveryService {
  static final ServerDiscoveryService _instance = ServerDiscoveryService._internal();
  factory ServerDiscoveryService() => _instance;
  ServerDiscoveryService._internal();

  static const int udpDiscoveryPort = 8888;
  static const String discoverKeyword = 'AURAPET_DISCOVER';
  static const String responsePrefix = 'AURAPET_SERVER:';

  bool _isDiscovering = false;
  bool get isDiscovering => _isDiscovering;

  /// Tự động tìm kiếm backend AuraPet trong mạng nội bộ
  /// Trả về URL backend nếu tìm thấy, hoặc null nếu không tìm thấy.
  Future<String?> autoDiscoverServer({Duration timeout = const Duration(seconds: 4)}) async {
    if (_isDiscovering) return null;
    _isDiscovering = true;

    try {
      // 1. Kiểm tra nhanh URL hiện tại trước (nếu đang hoạt động thì không cần quét)
      final currentWorking = await _probeServer(ApiEndpoints.baseUrl);
      if (currentWorking != null) {
        debugPrint('[Discovery] Current URL is active: $currentWorking');
        return currentWorking;
      }

      // 2. Thử dò tìm qua UDP Broadcast (nhanh, chuẩn LAN, phản hồi < 50ms)
      final udpDiscovered = await _discoverViaUdp(timeout: const Duration(seconds: 2));
      if (udpDiscovered != null) {
        debugPrint('[Discovery] Discovered via UDP: $udpDiscovered');
        DioClient().updateBaseUrl(udpDiscovered);
        return udpDiscovered;
      }

      // 3. Fallback: Quét nhanh các IP lân cận cùng dải mạng subnet (nếu router chặn UDP broadcast)
      final subnetDiscovered = await _scanSubnetFallback();
      if (subnetDiscovered != null) {
        debugPrint('[Discovery] Discovered via Subnet Scan: $subnetDiscovered');
        DioClient().updateBaseUrl(subnetDiscovered);
        return subnetDiscovered;
      }

      return null;
    } finally {
      _isDiscovering = false;
    }
  }

  /// Kiểm tra xem một URL có phải là AuraPet backend đang hoạt động không
  Future<String?> _probeServer(String baseUrl, {int timeoutMs = 800}) async {
    try {
      final dio = Dio(
        BaseOptions(
          connectTimeout: Duration(milliseconds: timeoutMs),
          receiveTimeout: Duration(milliseconds: timeoutMs),
        ),
      );
      final url = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;
      final res = await dio.get('$url/api/v1/system/status');
      if (res.statusCode == 200 && res.data != null) {
        return url;
      }
    } catch (_) {}
    return null;
  }

  /// Gửi gói tin UDP broadcast tìm backend trên cổng 8888
  Future<String?> _discoverViaUdp({Duration timeout = const Duration(seconds: 2)}) async {
    RawDatagramSocket? socket;
    try {
      socket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0);
      socket.broadcastEnabled = true;

      final completer = Completer<String?>();
      final msgBytes = utf8.encode(discoverKeyword);

      // Gửi broadcast toàn mạng
      socket.send(msgBytes, InternetAddress('255.255.255.255'), udpDiscoveryPort);

      // Lấy danh sách IP của điện thoại để gửi broadcast riêng từng subnet
      try {
        final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
        for (var iface in interfaces) {
          for (var addr in iface.addresses) {
            if (!addr.isLoopback) {
              final parts = addr.address.split('.');
              if (parts.length == 4) {
                // Ví dụ 10.10.18.255 hoặc 192.168.1.255
                final subnetBroadcast = '${parts[0]}.${parts[1]}.${parts[2]}.255';
                socket.send(msgBytes, InternetAddress(subnetBroadcast), udpDiscoveryPort);
              }
            }
          }
        }
      } catch (_) {}

      // Lắng nghe phản hồi
      final sub = socket.listen((event) async {
        if (event == RawSocketEvent.read) {
          final dg = socket?.receive();
          if (dg != null) {
            final received = utf8.decode(dg.data).trim();
            if (received.contains(responsePrefix)) {
              final candidateUrl = received.replaceAll(responsePrefix, '').trim();
              if (!completer.isCompleted) {
                completer.complete(candidateUrl);
              }
            } else {
              // Hoặc dùng trực tiếp IP của máy gửi gói UDP
              final candidateUrl = 'http://${dg.address.address}:8080';
              if (!completer.isCompleted) {
                completer.complete(candidateUrl);
              }
            }
          }
        }
      });

      final result = await completer.future.timeout(timeout, onTimeout: () => null);
      await sub.cancel();
      return result;
    } catch (e) {
      debugPrint('[Discovery] UDP discovery error: $e');
      return null;
    } finally {
      socket?.close();
    }
  }

  /// Quét nhanh các IP phổ biến trong dải mạng hiện tại
  Future<String?> _scanSubnetFallback() async {
    try {
      final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
      final candidateIps = <String>{};

      // Thêm các IP đã từng sử dụng trước đây
      candidateIps.addAll(['10.10.59.64', '10.10.18.41', '172.16.0.108', '192.168.1.100', '192.168.1.1']);

      for (var iface in interfaces) {
        for (var addr in iface.addresses) {
          if (!addr.isLoopback) {
            final parts = addr.address.split('.');
            if (parts.length == 4) {
              final prefix = '${parts[0]}.${parts[1]}.${parts[2]}';
              final hostNum = int.tryParse(parts[3]) ?? 10;

              // Quét gateway và các host hay dùng
              candidateIps.add('$prefix.1');
              candidateIps.add('$prefix.41');
              candidateIps.add('$prefix.100');
              candidateIps.add('$prefix.108');

              // Quét các host gần IP điện thoại
              for (var i = hostNum - 5; i <= hostNum + 5; i++) {
                if (i > 1 && i < 255) {
                  candidateIps.add('$prefix.$i');
                }
              }
            }
          }
        }
      }

      // Thử song song với timeout 450ms mỗi IP
      final futures = candidateIps.map((ip) async {
        final url = 'http://$ip:8080';
        final verified = await _probeServer(url, timeoutMs: 450);
        return verified;
      });

      final results = await Future.wait(futures);
      for (var r in results) {
        if (r != null) return r;
      }
    } catch (_) {}
    return null;
  }
}
