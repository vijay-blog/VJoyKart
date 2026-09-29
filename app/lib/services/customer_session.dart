import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import 'api_service.dart';

class OtpSendResult {
  final bool success;
  final String message;
  final int expiresInSeconds;
  final String deliveryChannel;
  final String? debugOtp;

  const OtpSendResult({
    required this.success,
    required this.message,
    required this.expiresInSeconds,
    required this.deliveryChannel,
    this.debugOtp,
  });

  factory OtpSendResult.fromJson(Map<String, dynamic> json) => OtpSendResult(
        success: json['success'] == true,
        message: json['message']?.toString() ?? 'OTP sent successfully.',
        expiresInSeconds:
            (json['expiresInSeconds'] as num?)?.toInt() ?? 300,
        deliveryChannel: json['deliveryChannel']?.toString() ?? 'SMS',
        debugOtp: json['debugOtp']?.toString(),
      );
}

class CustomerSession {
  CustomerSession({ApiService? api}) : _api = api ?? ApiService();

  final ApiService _api;

  Future<bool> isAuthenticated() async {
    final prefs = await SharedPreferences.getInstance();
    final id = prefs.getInt('nm.customerId');
    final token = prefs.getString('vk.accessToken') ??
        prefs.getString('vk.guestAccessToken');
    return id != null && id > 0 && token != null && token.isNotEmpty && !_isExpired(token);
  }

  Future<int> ensureCustomerId() async {
    final prefs = await SharedPreferences.getInstance();
    final existing = prefs.getInt('nm.customerId');
    final token = prefs.getString('vk.accessToken') ??
        prefs.getString('vk.guestAccessToken');
    if (existing != null && existing > 0 && token != null && !_isExpired(token)) {
      return existing;
    }
    throw StateError('Please verify your mobile number before checkout.');
  }

  Future<OtpSendResult> sendOtp(String phone) async {
    final response = await _api.post('/auth/customer/send-otp', {'phone': phone});
    if (response is! Map<String, dynamic>) {
      throw ApiException('Invalid OTP response from server.', 500);
    }
    return OtpSendResult.fromJson(response);
  }

  Future<int> verifyOtp(String phone, String otp) async {
    final response = await _api.post('/auth/customer/verify-otp', {
      'phone': phone,
      'otp': otp,
    });
    if (response is! Map<String, dynamic>) {
      throw ApiException('Invalid login response from server.', 500);
    }
    return _saveSession(response);
  }

  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('nm.customerId');
    await prefs.remove('vk.accessToken');
    await prefs.remove('vk.refreshToken');
    await prefs.remove('vk.guestAccessToken');
    await prefs.remove('vk.guestRefreshToken');
  }

  Future<int> _saveSession(Map<String, dynamic> response) async {
    final prefs = await SharedPreferences.getInstance();
    final user = response['user'];
    final id = user is Map<String, dynamic>
        ? (user['id'] as num?)?.toInt() ?? 0
        : 0;
    final accessToken = response['accessToken']?.toString() ?? '';
    final refreshToken = response['refreshToken']?.toString() ?? '';
    if (id <= 0 || accessToken.isEmpty || refreshToken.isEmpty) {
      throw StateError('Unable to start your VJoyKart session.');
    }
    await prefs.setInt('nm.customerId', id);
    await prefs.setString('vk.accessToken', accessToken);
    await prefs.setString('vk.refreshToken', refreshToken);
    // Keep the legacy keys for older service/repository code in this project.
    await prefs.setString('vk.guestAccessToken', accessToken);
    await prefs.setString('vk.guestRefreshToken', refreshToken);
    if (user is Map<String, dynamic>) {
      final phone = user['phone']?.toString();
      if (phone != null && phone.isNotEmpty) {
        await prefs.setString('vk.customerPhone', phone);
      }
    }
    return id;
  }

  bool _isExpired(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return true;
      final payload = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      ) as Map<String, dynamic>;
      final expiresAt = (payload['exp'] as num?)?.toInt();
      return expiresAt == null ||
          DateTime.now().millisecondsSinceEpoch >= expiresAt * 1000;
    } catch (_) {
      return true;
    }
  }
}
