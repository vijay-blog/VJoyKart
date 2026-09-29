import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import '../core/api_client.dart';
import '../core/app_config.dart';

class ApiService {
  final http.Client client;

  ApiService({http.Client? client}) : client = client ?? http.Client();

  Uri _uri(String path, [Map<String, String>? query]) {
    return Uri.parse(
            '${AppConfig.apiBaseUrl}${AppConfig.resolveEndpoint(path)}')
        .replace(queryParameters: query);
  }

  Future<dynamic> get(String path, [Map<String, String>? query]) async {
    final headers = await _headers();
    return _send(() => client.get(_uri(path, query), headers: headers));
  }

  Future<dynamic> post(String path, Map<String, dynamic> body) async {
    final headers = await _headers();
    return _send(() => client.post(
          _uri(path),
          headers: headers,
          body: jsonEncode(body),
        ));
  }

  Future<dynamic> put(String path, Map<String, dynamic> body) async {
    final headers = await _headers();
    return _send(() => client.put(
          _uri(path),
          headers: headers,
          body: jsonEncode(body),
        ));
  }

  Future<dynamic> delete(String path) async {
    final headers = await _headers();
    return _send(() => client.delete(_uri(path), headers: headers));
  }

  Future<Map<String, String>> _headers() async {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('vk.accessToken') ?? prefs.getString('vk.guestAccessToken');
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  Future<dynamic> _send(Future<http.Response> Function() request) async {
    final configIssue = AppConfig.runtimeConfigurationIssue;
    if (configIssue != null) throw ApiException(configIssue, 0);

    try {
      final response = await request().timeout(AppConfig.timeout);
      return _handle(response);
    } on TimeoutException catch (e, st) {
      _debugLog('Timeout', e, st);
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on SocketException catch (e, st) {
      _debugLog('SocketException', e, st);
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on http.ClientException catch (e, st) {
      _debugLog('ClientException', e, st);
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on FormatException catch (e, st) {
      _debugLog('FormatException', e, st);
      throw ApiException(
        'We could not process the server response. Please try again.',
        0,
      );
    } catch (e, st) {
      if (e is ApiException) rethrow;
      _debugLog('Unexpected API error', e, st);
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    }
  }

  dynamic _handle(http.Response response) {
    final statusCode = response.statusCode;
    if (statusCode >= 200 && statusCode < 300) {
      if (response.body.isEmpty) return null;
      return jsonDecode(response.body);
    }
    String message = _messageForStatus(statusCode);
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        final candidate = decoded['message'] ?? decoded['error'] ?? decoded['detail'];
        if (candidate != null && candidate.toString().trim().isNotEmpty) {
          message = candidate.toString();
        }
      }
    } catch (_) {
      // Keep the friendly status-based message for non-JSON responses.
    }
    throw ApiException(message, statusCode);
  }

  String _messageForStatus(int code) {
    switch (code) {
      case 400:
        return 'Bad request. Please check your input.';
      case 401:
        return 'Your session has expired. Please sign in again.';
      case 403:
        return "You don't have permission to perform this action.";
      case 404:
        return "We couldn't find the requested information.";
      case 409:
        return 'This item or order was updated. Please try again.';
      case 422:
        return 'Validation error. Please check submitted data.';
      case 429:
        return 'Too many requests. Please try again in a moment.';
      case 500:
      case 502:
      case 503:
        return 'VjoyKart is temporarily unavailable. Please try again shortly.';
      default:
        return code >= 500
            ? 'VjoyKart is temporarily unavailable. Please try again shortly.'
            : 'Unable to complete your request right now.';
    }
  }

  void _debugLog(String label, Object error, StackTrace stackTrace) {
    if (kReleaseMode) return;
    debugPrint('[ApiService] $label: $error');
    debugPrint('$stackTrace');
  }
}
