import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/address.dart';
import '../providers/address_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SavedAddressesScreen extends StatelessWidget {
  const SavedAddressesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AddressProvider>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Saved Addresses',
            style: TextStyle(fontWeight: FontWeight.w900)),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(context),
        icon: const Icon(Icons.add),
        label: const Text('Add Address'),
      ),
      body: provider.addresses.isEmpty
          ? const Center(
              child: Text('No address added yet'),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 90),
              itemCount: provider.addresses.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) {
                final a = provider.addresses[i];
                final selected = provider.selected?.id == a.id;
                return Card(
                  child: ListTile(
                    onTap: () => provider.select(a),
                    leading: Icon(
                      selected
                          ? Icons.check_circle
                          : Icons.radio_button_unchecked,
                      color: selected ? const Color(0xff3454d1) : Colors.grey,
                    ),
                    title: Row(
                      children: [
                        Expanded(
                          child: Text(a.name,
                              style:
                                  const TextStyle(fontWeight: FontWeight.w800)),
                        ),
                        if (a.isDefault)
                          Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 8, vertical: 3),
                              decoration: BoxDecoration(
                                  color: const Color(0xffe9edff),
                                  borderRadius: BorderRadius.circular(8)),
                              child: const Text('Default',
                                  style: TextStyle(
                                      fontSize: 11,
                                      color: Color(0xff3454d1),
                                      fontWeight: FontWeight.w700))),
                      ],
                    ),
                    subtitle: Text('${a.oneLine}\n${a.mobile}'),
                    isThreeLine: true,
                    trailing: PopupMenuButton<String>(
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
                        PopupMenuItem(
                            value: 'default', child: Text('Set default')),
                        PopupMenuItem(value: 'delete', child: Text('Delete')),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }

  void _openForm(BuildContext context, {Address? existing}) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => AddressFormScreen(existing: existing),
      ),
    );
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.existing == null ? 'Add Address' : 'Edit Address',
            style: const TextStyle(fontWeight: FontWeight.w900)),
      ),
      body: Form(
        key: formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _field('Full Name', name),
            _field('Mobile', mobile,
                keyboardType: TextInputType.phone,
                validator: (v) => RegExp(r'^[6-9]\d{9}$').hasMatch(v ?? '')
                    ? null
                    : 'Enter valid 10-digit mobile'),
            _field('House / Flat', house),
            _field('Street', street),
            _field('Area', area),
            _field('City', city),
            _field('State', state),
            _field('Pincode', pincode,
                keyboardType: TextInputType.number,
                validator: (v) => RegExp(r'^\d{6}$').hasMatch(v ?? '')
                    ? null
                    : 'Enter valid 6-digit pincode'),
            SwitchListTile(
              value: isDefault,
              onChanged: (v) => setState(() => isDefault = v),
              title: const Text('Set as default address'),
            ),
            const SizedBox(height: 12),
            SizedBox(
              height: 52,
              child: FilledButton(
                  onPressed: save, child: const Text('Save Address')),
            )
          ],
        ),
      ),
    );
  }

  Widget _field(String label, TextEditingController controller,
      {TextInputType keyboardType = TextInputType.text,
      String? Function(String?)? validator}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextFormField(
        controller: controller,
        keyboardType: keyboardType,
        validator: validator ??
            (v) =>
                (v == null || v.trim().isEmpty) ? '$label is required' : null,
        decoration: InputDecoration(labelText: label),
      ),
    );
  }

  void save() async {
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
      isDefault: isDefault,
    );
    await context.read<AddressProvider>().save(item);
    if (mounted) Navigator.pop(context);
  }
}
