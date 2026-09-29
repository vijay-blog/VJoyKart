package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.service.AuthService;
import com.nexamart.backend.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;
  private final OtpService otp;
  public AuthController(AuthService auth, OtpService otp) { this.auth = auth; this.otp = otp; }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = auth.login(request);
    requireRole(response, "CUSTOMER");
    return response;
  }

  @PostMapping("/customer/register")
  public LoginResponse registerCustomer(@Valid @RequestBody RegisterRequest request) {
    return auth.registerCustomer(request);
  }


  @PostMapping("/customer/send-otp")
  public OtpSendResponse sendCustomerOtp(@Valid @RequestBody OtpRequest request) {
    return otp.sendOtp(request.phone());
  }

  @PostMapping("/customer/verify-otp")
  public LoginResponse verifyCustomerOtp(@Valid @RequestBody VerifyOtpRequest request) {
    return otp.verifyOtp(request.phone(), request.otp());
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
    LoginResponse response = auth.refresh(request);
    requireRole(response, "CUSTOMER");
    return response;
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() { return ResponseEntity.noContent().build(); }

  private void requireRole(LoginResponse response, String role) {
    if (!role.equals(response.user().role())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Customer access required.");
    }
  }
}
