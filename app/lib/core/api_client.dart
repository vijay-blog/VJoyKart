import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import 'app_config.dart';

enum HttpMethod { get, post, put, delete }

class ApiException implements Exception {
  final String message;
  final int statusCode;

  ApiException(this.message, this.statusCode);

  @override
  String toString() => message;
}

class ApiClient {
  final http.Client _client;
  String? _accessToken;

  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  void setAccessToken(String? token) {
    _accessToken = token;
  }

  Future<dynamic> request(
    HttpMethod method,
    String endpoint, {
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? body,
  }) async {
    final configIssue = AppConfig.runtimeConfigurationIssue;
    if (configIssue != null) throw ApiException(configIssue, 0);

    final uri = Uri.parse(
      '${AppConfig.apiBaseUrl}${AppConfig.resolveEndpoint(endpoint)}',
    ).replace(
      queryParameters:
          queryParameters?.map((key, value) => MapEntry(key, value.toString())),
    );

    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };

    if (_accessToken != null && _accessToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_accessToken';
    }

    try {
      final response = await _send(method, uri, headers, body);
      return _handleResponse(response);
    } on TimeoutException {
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on SocketException {
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on http.ClientException {
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    } on FormatException {
      throw ApiException(
        'We could not process the server response. Please try again.',
        0,
      );
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        'Unable to connect to VjoyKart. Please check your internet connection.',
        0,
      );
    }
  }

  Future<http.Response> _send(
    HttpMethod method,
    Uri uri,
    Map<String, String> headers,
    Map<String, dynamic>? body,
  ) {
    switch (method) {
      case HttpMethod.get:
        return _client.get(uri, headers: headers).timeout(AppConfig.timeout);
      case HttpMethod.post:
        return _client
            .post(
              uri,
              headers: headers,
              body: body != null ? jsonEncode(body) : null,
            )
            .timeout(AppConfig.timeout);
      case HttpMethod.put:
        return _client
            .put(
              uri,
              headers: headers,
              body: body != null ? jsonEncode(body) : null,
            )
            .timeout(AppConfig.timeout);
      case HttpMethod.delete:
        return _client.delete(uri, headers: headers).timeout(AppConfig.timeout);
    }
  }

  dynamic _handleResponse(http.Response response) {
    final code = response.statusCode;
    if (code >= 200 && code < 300) {
      if (response.body.isEmpty) return null;
      return jsonDecode(response.body);
    }
    String message = _messageForStatusCode(code);
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        final candidate = decoded['message'] ?? decoded['error'] ?? decoded['detail'];
        if (candidate != null && candidate.toString().trim().isNotEmpty) {
          message = candidate.toString();
        }
      }
    } catch (_) {}
    throw ApiException(message, code);
  }

  String _messageForStatusCode(int code) {
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
}
