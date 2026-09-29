import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/app_theme.dart';
import '../services/customer_session.dart';

class OtpLoginView extends StatefulWidget {
  final VoidCallback onAuthenticated;

  const OtpLoginView({super.key, required this.onAuthenticated});

  @override
  State<OtpLoginView> createState() => _OtpLoginViewState();
}

class _OtpLoginViewState extends State<OtpLoginView> {
  final phoneController = TextEditingController();
  final otpController = TextEditingController();
  final session = CustomerSession();
  bool sending = false;
  bool verifying = false;
  bool otpSent = false;
  String? debugOtp;

  @override
  void dispose() {
    phoneController.dispose();
    otpController.dispose();
    super.dispose();
  }

  Future<void> sendOtp() async {
    final phone = phoneController.text.replaceAll(RegExp(r'\D'), '');
    if (!RegExp(r'^[6-9]\d{9}$').hasMatch(phone)) {
      _show('Enter a valid 10-digit mobile number.');
      return;
    }
    setState(() => sending = true);
    try {
      final result = await session.sendOtp(phone);
      if (!mounted) return;
      setState(() {
        otpSent = result.success;
        debugOtp = result.debugOtp;
      });
      if (result.debugOtp != null && result.debugOtp!.isNotEmpty) {
        otpController.text = result.debugOtp!;
      }
      _show(result.debugOtp == null
          ? 'OTP sent to +91 $phone.'
          : 'Development OTP: ${result.debugOtp}');
    } catch (e) {
      _show(_message(e));
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> verifyOtp() async {
    final phone = phoneController.text.replaceAll(RegExp(r'\D'), '');
    final otp = otpController.text.trim();
    if (!RegExp(r'^[6-9]\d{9}$').hasMatch(phone)) {
      _show('Enter a valid mobile number.');
      return;
    }
    if (!RegExp(r'^\d{6}$').hasMatch(otp)) {
      _show('Enter the 6-digit OTP.');
      return;
    }
    setState(() => verifying = true);
    try {
      await session.verifyOtp(phone, otp);
      if (!mounted) return;
      widget.onAuthenticated();
    } catch (e) {
      _show(_message(e));
    } finally {
      if (mounted) setState(() => verifying = false);
    }
  }

  void _changeNumber() {
    setState(() {
      otpSent = false;
      otpController.clear();
      debugOtp = null;
    });
    phoneController.selection = TextSelection(
      baseOffset: 0,
      extentOffset: phoneController.text.length,
    );
  }

  void _show(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String _message(Object error) {
    if (error is ApiException) return error.message;
    return 'Unable to continue right now. Please try again.';
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(22, 24, 22, 30),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(22),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 58,
                      height: 58,
                      decoration: BoxDecoration(
                        color: const Color(0xffe9edff),
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: const Icon(Icons.phone_android_rounded,
                          color: AppTheme.green, size: 30),
                    ),
                    const SizedBox(height: 18),
                    const Text('Verify your mobile number',
                        style: TextStyle(
                            fontSize: 24, fontWeight: FontWeight.w900)),
                    const SizedBox(height: 8),
                    const Text(
                      'You can shop as a guest. We only ask for your mobile number when you are ready to place the order.',
                      style: TextStyle(color: Colors.black54, height: 1.4),
                    ),
                    const SizedBox(height: 20),
                    TextField(
                      controller: phoneController,
                      keyboardType: TextInputType.phone,
                      maxLength: 10,
                      enabled: !otpSent && !sending && !verifying,
                      decoration: const InputDecoration(
                        counterText: '',
                        labelText: 'Mobile number',
                        prefixText: '+91 ',
                        prefixIcon: Icon(Icons.phone_outlined),
                      ),
                    ),
                    if (otpSent) ...[
                      const SizedBox(height: 12),
                      TextField(
                        controller: otpController,
                        keyboardType: TextInputType.number,
                        maxLength: 6,
                        autofocus: true,
                        decoration: const InputDecoration(
                          counterText: '',
                          labelText: '6-digit OTP',
                          prefixIcon: Icon(Icons.lock_outline),
                        ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          const Text('OTP sent by SMS',
                              style: TextStyle(color: Colors.black54)),
                          const Spacer(),
                          TextButton(
                            onPressed:
                                sending || verifying ? null : _changeNumber,
                            child: const Text('Change number'),
                          ),
                        ],
                      ),
                    ],
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: FilledButton(
                        onPressed: sending || verifying
                            ? null
                            : (otpSent ? verifyOtp : sendOtp),
                        child: sending || verifying
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.5,
                                  color: Colors.white,
                                ),
                              )
                            : Text(otpSent ? 'Verify & Continue' : 'Send OTP'),
                      ),
                    ),
                    if (debugOtp != null) ...[
                      const SizedBox(height: 10),
                      Text('Development OTP: $debugOtp',
                          style: const TextStyle(
                              fontSize: 12, color: Colors.orange)),
                    ],
                    const SizedBox(height: 12),
                    const Text(
                      'By continuing, you agree to VJoyKart terms and privacy policy.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 11, color: Colors.black45),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
