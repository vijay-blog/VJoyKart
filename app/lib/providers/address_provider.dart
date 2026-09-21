import 'package:flutter/foundation.dart';
import 'dart:convert';
import '../models/address.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AddressProvider extends ChangeNotifier {
  final List<Address> addresses = [];
  Address? selected;
  bool initialized = false;

  AddressProvider() {
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getStringList('zp.addresses') ?? const [];
    final saved = raw
        .map((x) => Address.fromJson(jsonDecode(x) as Map<String, dynamic>))
        .toList();
    addresses
      ..clear()
      ..addAll(saved);
    final selectedId = prefs.getString('zp.selectedAddressId');
    if (selectedId != null) {
      selected = addresses.where((x) => x.id == selectedId).firstOrNull;
    }
    selected ??= addresses.where((x) => x.isDefault).firstOrNull;
    selected ??= addresses.isEmpty ? null : addresses.first;
    initialized = true;
    notifyListeners();
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      'zp.addresses',
      addresses.map((x) => jsonEncode(x.toJson())).toList(),
    );
    if (selected?.id != null) {
      await prefs.setString('zp.selectedAddressId', selected!.id!);
    }
  }

  Future<void> save(Address a) async {
    final id = (a.id == null || a.id!.isEmpty)
        ? 'addr_${DateTime.now().millisecondsSinceEpoch}'
        : a.id!;
    final item = a.copyWith(id: id);
    final idx = addresses.indexWhere((x) => x.id == item.id);
    if (item.isDefault) {
      for (int i = 0; i < addresses.length; i++) {
        addresses[i] = addresses[i].copyWith(isDefault: false);
      }
    }
    if (idx >= 0) {
      addresses[idx] = item;
    } else {
      addresses.insert(0, item);
    }
    selected = item;
    await _persist();
    notifyListeners();
  }

  Future<void> select(Address a) async {
    selected = a;
    await _persist();
    notifyListeners();
  }

  Future<void> setDefault(Address a) async {
    for (int i = 0; i < addresses.length; i++) {
      addresses[i] = addresses[i].copyWith(isDefault: addresses[i].id == a.id);
    }
    selected = addresses.where((x) => x.id == a.id).firstOrNull;
    await _persist();
    notifyListeners();
  }

  Future<void> delete(Address a) async {
    addresses.remove(a);
    if (selected == a) selected = addresses.isEmpty ? null : addresses.first;
    await _persist();
    notifyListeners();
  }
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
