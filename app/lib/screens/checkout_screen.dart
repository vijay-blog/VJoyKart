import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:razorpay_flutter/razorpay_flutter.dart';
import '../core/api_client.dart';
import '../providers/address_provider.dart';
import '../providers/cart_provider.dart';
import '../providers/order_provider.dart';
import 'order_success_screen.dart';
import 'saved_addresses_screen.dart';
import 'otp_login_screen.dart';
import '../services/customer_session.dart';
import '../widgets/catalog_image.dart';

class CheckoutScreen extends StatefulWidget {
  const CheckoutScreen({super.key});
  @override
  State<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends State<CheckoutScreen> {
  bool loading = false;
  bool authenticated = false;
  bool checkingSession = true;
  String paymentMethod = 'COD';
  int? _pendingOnlineOrderId;
  late final Razorpay _razorpay;

  @override
  void initState() {
    super.initState();
    _razorpay = Razorpay();
    _razorpay.on(Razorpay.EVENT_PAYMENT_SUCCESS, _handlePaymentSuccess);
    _razorpay.on(Razorpay.EVENT_PAYMENT_ERROR, _handlePaymentError);
    _razorpay.on(Razorpay.EVENT_EXTERNAL_WALLET, _handleExternalWallet);
    _checkSession();
  }

  Future<void> _checkSession() async {
    final ok = await CustomerSession().isAuthenticated();
    if (!mounted) return;
    setState(() {
      authenticated = ok;
      checkingSession = false;
    });
    if (ok) {
      await context.read<OrderProvider>().refresh();
    }
  }

  void _authenticated() async {
    if (!mounted) return;
    setState(() => authenticated = true);
    await context.read<OrderProvider>().refresh();
  }

  @override
  void dispose() {
    _razorpay.clear();
    super.dispose();
  }

  Future<void> place() async {
    final addressProvider = context.read<AddressProvider>();
    final selected = addressProvider.selected;
    if (selected == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Please add/select a delivery address.')));
      return;
    }
    setState(() => loading = true);
    try {
      final cart = context.read<CartProvider>();
      final orders = context.read<OrderProvider>();
      final order = await orders.create(cart.items, selected,
          paymentMethod: paymentMethod);
      if (paymentMethod == 'ONLINE') {
        final paymentOrder = await orders.createPaymentOrder(order);
        _pendingOnlineOrderId = paymentOrder.orderId;
        _razorpay.open({
          'key': paymentOrder.keyId,
          'amount': (paymentOrder.amount * 100).round(),
          'currency': paymentOrder.currency,
          'order_id': paymentOrder.gatewayOrderId,
          'name': 'VJoyKart',
          'description': 'Order ${order.orderNumber}',
          'prefill': {'contact': selected.mobile, 'name': selected.name},
          'theme': {'color': '#3454D1'},
        });
      } else {
        cart.clear();
        if (!mounted) return;
        Navigator.pushAndRemoveUntil(
            context,
            MaterialPageRoute(builder: (_) => OrderSuccessScreen(order: order)),
            (r) => r.isFirst);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_userMessage(e))),
        );
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _handlePaymentSuccess(PaymentSuccessResponse response) async {
    try {
      final order = await context.read<OrderProvider>().verifyPayment(
            orderId: _pendingOnlineOrderId ?? 0,
            gatewayOrderId: response.orderId ?? '',
            gatewayPaymentId: response.paymentId ?? '',
            gatewaySignature: response.signature ?? '',
          );
      if (!mounted) return;
      context.read<CartProvider>().clear();
      Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => OrderSuccessScreen(order: order)),
          (r) => r.isFirst);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_userMessage(e))),
        );
      }
    }
  }

  void _handlePaymentError(PaymentFailureResponse response) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
          content: Text(response.message ?? 'Payment failed or cancelled')),
    );
  }

  void _handleExternalWallet(ExternalWalletResponse response) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
          content: Text('External wallet selected: ${response.walletName}')),
    );
  }

  @override
  Widget build(BuildContext c) {
    final cart = c.watch<CartProvider>();
    final addresses = c.watch<AddressProvider>();
    return Scaffold(
        appBar: AppBar(
            title: const Text('Checkout',
                style: TextStyle(fontWeight: FontWeight.w900))),
        body: checkingSession
            ? const Center(child: CircularProgressIndicator())
            : !authenticated
                ? OtpLoginView(onAuthenticated: _authenticated)
                : ListView(padding: const EdgeInsets.all(18), children: [
          const Text('Delivery address',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
          const SizedBox(height: 12),
          if (addresses.selected == null)
            Card(
                child: ListTile(
                    leading: const Icon(Icons.location_on_outlined),
                    title: const Text('No address selected'),
                    subtitle:
                        const Text('Add or select an address to continue'),
                    trailing: TextButton(
                        onPressed: () => Navigator.push(
                            c,
                            MaterialPageRoute(
                                builder: (_) => const SavedAddressesScreen())),
                        child: const Text('Manage'))))
          else
            Card(
                child: ListTile(
                    leading: const Icon(Icons.home_outlined),
                    title: Text(addresses.selected!.name,
                        style: const TextStyle(fontWeight: FontWeight.w800)),
                    subtitle: Text(
                        '${addresses.selected!.oneLine}\n${addresses.selected!.mobile}'),
                    isThreeLine: true,
                    trailing: TextButton(
                        onPressed: () => Navigator.push(
                            c,
                            MaterialPageRoute(
                                builder: (_) => const SavedAddressesScreen())),
                        child: const Text('Change')))),
          const SizedBox(height: 10),
          const SizedBox(height: 10),
          const Text('Items',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          Card(
              child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                      children: cart.items
                          .map((x) => ListTile(
                                contentPadding: EdgeInsets.zero,
                                leading: CatalogImage(
                                    source: x.product.imageAsset,
                                    width: 40,
                                    height: 40),
                                title: Text(x.product.name),
                                subtitle:
                                    Text('${x.product.unit} × ${x.quantity}'),
                                trailing: Text('₹${x.total.round()}'),
                              ))
                          .toList()))),
          const SizedBox(height: 10),
          Card(
              child: Column(children: [
            _paymentTile(
                value: 'COD',
                icon: Icons.payments_outlined,
                title: 'Cash on Delivery',
                subtitle: 'Pay when your order arrives'),
            const Divider(height: 1),
            _paymentTile(
                value: 'ONLINE',
                icon: Icons.lock_outline,
                title: 'Pay ₹${cart.total.round()} securely',
                subtitle: 'Razorpay UPI, card, wallet or netbanking'),
          ])),
          const SizedBox(height: 12),
          Card(
              child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(children: [
                    _r('Subtotal', cart.subtotal),
                    _r('Delivery', cart.delivery),
                    _r(
                        'Discount',
                        cart.items.fold<double>(
                            0,
                            (sum, item) =>
                                sum +
                                ((item.product.mrp -
                                        item.product.sellingPrice) *
                                    item.quantity)),
                        prefix: '-₹'),
                    const Divider(),
                    _r('Total', cart.total, bold: true)
                  ]))),
          const SizedBox(height: 14),
          SizedBox(
              height: 54,
              child: FilledButton(
                  onPressed: loading ? null : place,
                  child: loading
                      ? const CircularProgressIndicator()
                      : Text(paymentMethod == 'ONLINE'
                          ? 'Pay Securely • ₹${cart.total.round()}'
                          : 'Place COD Order • ₹${cart.total.round()}')))
        ]));
  }

  Widget _r(String x, double v, {bool bold = false, String prefix = '₹'}) =>
      Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Row(children: [
            Text(x,
                style: TextStyle(fontWeight: bold ? FontWeight.w900 : null)),
            const Spacer(),
            Text('$prefix${v.round()}',
                style: TextStyle(fontWeight: bold ? FontWeight.w900 : null))
          ]));

  Widget _paymentTile({
    required String value,
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    final selected = paymentMethod == value;
    return ListTile(
      onTap: () => setState(() => paymentMethod = value),
      leading: Icon(icon),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
      subtitle: Text(subtitle),
      trailing: Icon(
        selected ? Icons.check_circle : Icons.radio_button_unchecked,
        color: selected ? const Color(0xff3454d1) : Colors.grey,
      ),
    );
  }

  String _userMessage(Object error) {
    if (error is ApiException) return error.message;
    return 'Unable to place your order right now. Please check your internet connection and try again.';
  }
}
