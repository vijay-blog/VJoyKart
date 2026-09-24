import 'package:flutter/material.dart';
import 'package:geocoding/geocoding.dart';
import 'package:geolocator/geolocator.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

import '../models/address.dart';
import '../providers/address_provider.dart';

class SavedAddressesScreen extends StatelessWidget {
  const SavedAddressesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AddressProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Saved Addresses', style: TextStyle(fontWeight: FontWeight.w900)),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(context),
        icon: const Icon(Icons.add_location_alt_outlined),
        label: const Text('Add Address'),
      ),
      body: provider.addresses.isEmpty
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(28),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: const Color(0xffe9edff),
                        borderRadius: BorderRadius.circular(24),
                      ),
                      child: const Icon(Icons.location_on_outlined, size: 46, color: Color(0xff3454d1)),
                    ),
                    const SizedBox(height: 16),
                    const Text('Add your delivery address', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
                    const SizedBox(height: 8),
                    const Text('Use your current location for faster and more accurate delivery.', textAlign: TextAlign.center),
                    const SizedBox(height: 18),
                    FilledButton.icon(
                      onPressed: () => _openForm(context),
                      icon: const Icon(Icons.my_location),
                      label: const Text('Use Current Location'),
                    ),
                  ],
                ),
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 100),
              itemCount: provider.addresses.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (_, i) {
                final a = provider.addresses[i];
                final selected = provider.selected?.id == a.id;
                return Card(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(18),
                    onTap: () => provider.select(a),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: selected ? const Color(0xffe9edff) : const Color(0xfff3f4f8),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Icon(selected ? Icons.check_circle : Icons.home_outlined,
                                    color: selected ? const Color(0xff3454d1) : Colors.black54),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Text(a.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
                              ),
                              if (a.isDefault)
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                                  decoration: BoxDecoration(color: const Color(0xffe9edff), borderRadius: BorderRadius.circular(20)),
                                  child: const Text('Default', style: TextStyle(fontSize: 11, color: Color(0xff3454d1), fontWeight: FontWeight.w800)),
                                ),
                              PopupMenuButton<String>(
                                onSelected: (v) {
                                  if (v == 'edit') {
                                    _openForm(context, existing: a);
                                  } else if (v == 'default') {
                                    provider.setDefault(a);
                                  } else {
                                    provider.delete(a);
                                  }
                                },
                                itemBuilder: (_) => const [
                                  PopupMenuItem(value: 'edit', child: Text('Edit')),
                                  PopupMenuItem(value: 'default', child: Text('Set as default')),
                                  PopupMenuItem(value: 'delete', child: Text('Delete')),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(a.oneLine, style: const TextStyle(fontWeight: FontWeight.w600)),
                          const SizedBox(height: 5),
                          Row(children: [const Icon(Icons.phone_outlined, size: 17, color: Colors.black54), const SizedBox(width: 6), Text(a.mobile)]),
                          if (a.latitude != null && a.longitude != null) ...[
                            const SizedBox(height: 8),
                            TextButton.icon(
                              onPressed: () => _openMaps(a.latitude!, a.longitude!),
                              icon: const Icon(Icons.map_outlined, size: 18),
                              label: const Text('Open in Google Maps'),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
    );
  }

  void _openForm(BuildContext context, {Address? existing}) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => AddressFormScreen(existing: existing)));
  }
}

class AddressFormScreen extends StatefulWidget {
  final Address? existing;
  const AddressFormScreen({super.key, this.existing});
  @override
  State<AddressFormScreen> createState() => _AddressFormScreenState();
}

class _AddressFormScreenState extends State<AddressFormScreen> {
  final formKey = GlobalKey<FormState>();
  late final TextEditingController name;
  late final TextEditingController mobile;
  late final TextEditingController house;
  late final TextEditingController street;
  late final TextEditingController area;
  late final TextEditingController city;
  late final TextEditingController state;
  late final TextEditingController pincode;
  bool isDefault = false;
  bool locating = false;
  double? latitude;
  double? longitude;

  @override
  void initState() {
    super.initState();
    final a = widget.existing;
    name = TextEditingController(text: a?.name ?? '');
    mobile = TextEditingController(text: a?.mobile ?? '');
    house = TextEditingController(text: a?.house ?? '');
    street = TextEditingController(text: a?.street ?? '');
    area = TextEditingController(text: a?.area ?? '');
    city = TextEditingController(text: a?.city ?? 'Hyderabad');
    state = TextEditingController(text: a?.state ?? 'Telangana');
    pincode = TextEditingController(text: a?.pincode ?? '');
    isDefault = a?.isDefault ?? false;
    latitude = a?.latitude;
    longitude = a?.longitude;
    if (a == null) _prefillCustomerPhone();
  }

  Future<void> _prefillCustomerPhone() async {
    final prefs = await SharedPreferences.getInstance();
    final customerPhone = prefs.getString('vk.customerPhone');
    if (!mounted || customerPhone == null || customerPhone.isEmpty) return;
    if (mobile.text.isEmpty) mobile.text = customerPhone;
  }

  @override
  void dispose() {
    for (final c in [name, mobile, house, street, area, city, state, pincode]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _useCurrentLocation() async {
    setState(() => locating = true);
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        throw Exception('Location services are turned off. Please enable GPS and try again.');
      }
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied) {
        throw Exception('Location permission was denied.');
      }
      if (permission == LocationPermission.deniedForever) {
        throw Exception('Location permission is permanently denied. Please enable it in Settings.');
      }
      final position = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
      );
      latitude = position.latitude;
      longitude = position.longitude;
      final marks = await placemarkFromCoordinates(position.latitude, position.longitude);
      if (marks.isNotEmpty) {
        final p = marks.first;
        final lines = <String>[];
        for (final value in [p.name, p.street, p.subLocality]) {
          final v = (value ?? '').trim();
          if (v.isNotEmpty && !lines.contains(v)) lines.add(v);
        }
        house.text = lines.isNotEmpty ? lines.first : house.text;
        street.text = lines.length > 1 ? lines[1] : street.text;
        area.text = lines.length > 2 ? lines[2] : (p.locality ?? area.text);
        city.text = (p.locality ?? p.subAdministrativeArea ?? city.text).trim();
        state.text = (p.administrativeArea ?? state.text).trim();
        pincode.text = (p.postalCode ?? pincode.text).trim();
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Current location detected and address fields updated.')));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))));
    } finally {
      if (mounted) setState(() => locating = false);
    }
  }

  Future<void> _openMap() async {
    if (latitude == null || longitude == null) {
      await _useCurrentLocation();
    }
    if (latitude == null || longitude == null) return;
    final uri = Uri.parse('https://www.google.com/maps/search/?api=1&query=$latitude,$longitude');
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.existing == null ? 'Add Address' : 'Edit Address', style: const TextStyle(fontWeight: FontWeight.w900))),
      body: Form(
        key: formKey,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 30),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Row(children: [Icon(Icons.location_searching, color: Color(0xff3454d1)), SizedBox(width: 10), Text('Find your delivery location', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900))]),
                  const SizedBox(height: 7),
                  const Text('Use your phone GPS to automatically fill the address. You can edit any field before saving.'),
                  const SizedBox(height: 14),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: FilledButton.icon(
                      onPressed: locating ? null : _useCurrentLocation,
                      icon: locating ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : const Icon(Icons.my_location),
                      label: Text(locating ? 'Detecting location...' : 'Use Current Location'),
                    ),
                  ),
                  if (latitude != null && longitude != null) ...[
                    const SizedBox(height: 8),
                    OutlinedButton.icon(onPressed: _openMap, icon: const Icon(Icons.map_outlined), label: const Text('View Location on Google Maps')),
                  ],
                ]),
              ),
            ),
            const SizedBox(height: 12),
            _field('Full Name', name),
            _field('Mobile', mobile, keyboardType: TextInputType.phone, validator: (v) => RegExp(r'^[6-9]\d{9}$').hasMatch(v ?? '') ? null : 'Enter valid 10-digit mobile'),
            _field('House / Flat', house),
            _field('Street', street),
            _field('Area', area),
            _field('City', city),
            _field('State', state),
            _field('Pincode', pincode, keyboardType: TextInputType.number, validator: (v) => RegExp(r'^\d{6}$').hasMatch(v ?? '') ? null : 'Enter valid 6-digit pincode'),
            SwitchListTile(value: isDefault, onChanged: (v) => setState(() => isDefault = v), title: const Text('Set as default address'), contentPadding: EdgeInsets.zero),
            const SizedBox(height: 10),
            SizedBox(height: 54, child: FilledButton.icon(onPressed: save, icon: const Icon(Icons.check), label: const Text('Save Address'))),
          ],
        ),
      ),
    );
  }

  Widget _field(String label, TextEditingController controller, {TextInputType keyboardType = TextInputType.text, String? Function(String?)? validator}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextFormField(
        controller: controller,
        keyboardType: keyboardType,
        validator: validator ?? (v) => (v == null || v.trim().isEmpty) ? '$label is required' : null,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }

  Future<void> save() async {
    if (!formKey.currentState!.validate()) return;
    final item = Address(
      id: widget.existing?.id,
      name: name.text.trim(),
      mobile: mobile.text.trim(),
      house: house.text.trim(),
      street: street.text.trim(),
      area: area.text.trim(),
      city: city.text.trim(),
      state: state.text.trim(),
      pincode: pincode.text.trim(),
      country: 'India',
      latitude: latitude,
      longitude: longitude,
      isDefault: isDefault,
    );
    await context.read<AddressProvider>().save(item);
    if (mounted) Navigator.pop(context);
  }
}

Future<void> _openMaps(double lat, double lng) async {
  final uri = Uri.parse('https://www.google.com/maps/search/?api=1&query=$lat,$lng');
  await launchUrl(uri, mode: LaunchMode.externalApplication);
}
