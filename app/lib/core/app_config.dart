class AppConfig {
  static const String _apiPrefix = '/api/v1';
  static const String _defaultApiBaseUrl =
      'https://zeptopluse-production.up.railway.app$_apiPrefix';
  static const bool _isReleaseBuild = bool.fromEnvironment('dart.vm.product');
  static final String apiBaseUrl = _normalizeBaseUrl(
    const String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: _defaultApiBaseUrl,
    ),
  );

  // Product image binaries are served by the catalog/image backend. Keep this
  // separate from the customer API so catalog images can continue to render
  // when the customer API and Partner catalog are deployed independently.
  static const String catalogImageBaseUrl =
      'https://nexamartpartner-production.up.railway.app';

  static String? get runtimeConfigurationIssue {
    final value = apiBaseUrl.toLowerCase();
    if (!_isReleaseBuild) return null;
    if (value.isEmpty || !value.startsWith('https://')) {
      return 'VjoyKart is not configured for production yet.';
    }
    final host = Uri.tryParse(value)?.host ?? '';
    final private172 = RegExp(r'^172\.(1[6-9]|2\d|3[01])\.').hasMatch(host);
    if (value.contains('railway.internal') ||
        host.startsWith('10.') ||
        host.startsWith('192.168.') ||
        private172 ||
        host == '127.0.0.1' ||
        host == 'localhost') {
      return 'VjoyKart is not configured for production yet.';
    }
    return null;
  }

  static String resolveEndpoint(String endpoint) {
    final normalized = endpoint.startsWith('/') ? endpoint : '/$endpoint';
    if (apiBaseUrl.endsWith(_apiPrefix) &&
        (normalized == _apiPrefix || normalized.startsWith('$_apiPrefix/'))) {
      final trimmed = normalized.substring(_apiPrefix.length);
      return trimmed.isEmpty ? '/' : trimmed;
    }
    return normalized;
  }

  static String _normalizeBaseUrl(String value) =>
      value.trim().replaceAll(RegExp(r'/+$'), '');

  static const bool useMockFallback = false;
  static const Duration timeout = Duration(seconds: 15);
}
