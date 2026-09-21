package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.LoginResponse;
import com.nexamart.backend.api.ApiModels.OtpSendResponse;
import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.OtpChallenge;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.config.AppProperties;
import com.nexamart.backend.repository.OtpChallengeRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Locale;

@Service
public class OtpService {
    private final OtpChallengeRepository challenges;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AppProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OtpService(OtpChallengeRepository challenges,
                      UserAccountRepository users,
                      PasswordEncoder passwordEncoder,
                      AuthService authService,
                      AppProperties properties) {
        this.challenges = challenges;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.properties = properties;
    }

    @Transactional
    public OtpSendResponse sendOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        OtpChallenge previous = challenges.findTopByPhoneOrderByCreatedAtDesc(phone).orElse(null);
        if (previous != null && previous.getCreatedAt().plusSeconds(30).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait 30 seconds before requesting another OTP.");
        }

        String otp = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhone(phone);
        challenge.setOtpHash(hash(phone, otp));
        challenge.setExpiresAt(Instant.now().plusSeconds(properties.getOtpTtlSeconds()));
        challenges.save(challenge);

        String channel = "SMS";
        try {
            sendSms(phone, otp);
        } catch (Exception ex) {
            if (!properties.isOtpDevMode()) {
                challenges.delete(challenge);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "OTP service is not configured. Please contact support.");
            }
            channel = "DEV";
        }

        return new OtpSendResponse(true, "OTP sent successfully.", properties.getOtpTtlSeconds(), channel,
                properties.isOtpDevMode() ? otp : null);
    }

    @Transactional
    public LoginResponse verifyOtp(String rawPhone, String otp) {
        String phone = normalizePhone(rawPhone);
        if (otp == null || !otp.matches("\\d{6}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the 6-digit OTP.");
        }
        OtpChallenge challenge = challenges.findTopByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP not found. Please request a new OTP."));
        if (challenge.isVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This OTP has already been used.");
        }
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired. Please request a new OTP.");
        }
        if (challenge.getAttempts() >= 5) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts. Please request a new OTP.");
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (!MessageDigest.isEqual(challenge.getOtpHash().getBytes(StandardCharsets.UTF_8),
                hash(phone, otp).getBytes(StandardCharsets.UTF_8))) {
            challenges.save(challenge);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect OTP. Please try again.");
        }
        challenge.setVerified(true);
        challenges.save(challenge);

        UserAccount user = users.findByPhone(phone).orElseGet(() -> createCustomer(phone));
        if (user.getRole() != Role.CUSTOMER) {
            throw new ApiException(HttpStatus.CONFLICT, "This mobile number is already registered for another account type.");
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Customer account is not active.");
        }
        user.setLastActiveAt(Instant.now());
        users.save(user);
        return authService.issueCustomerSession(user);
    }

    private UserAccount createCustomer(String phone) {
        UserAccount user = new UserAccount();
        user.setName("VJoyKart Customer");
        user.setUsername(phone);
        user.setEmail("customer." + phone + "@vjoykart.app");
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(AccountStatus.ACTIVE);
        return users.save(user);
    }

    private void sendSms(String phone, String otp) throws Exception {
        String apiKey = properties.getOtpApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            if (properties.isOtpDevMode()) return;
            throw new IllegalStateException("TWOFACTOR_API_KEY is not configured");
        }

        Exception modernFailure = null;
        try {
            sendSmsUsingCurrentApi(apiKey, phone, otp);
            return;
        } catch (Exception ex) {
            modernFailure = ex;
        }

        // Backward-compatible fallback for accounts still using the original
        // 2Factor manual OTP endpoint.
        try {
            sendSmsUsingLegacyApi(apiKey, phone, otp);
        } catch (Exception legacyFailure) {
            if (modernFailure != null) legacyFailure.addSuppressed(modernFailure);
            throw legacyFailure;
        }
    }

    private void sendSmsUsingCurrentApi(String apiKey, String phone, String otp) throws Exception {
        StringBuilder json = new StringBuilder()
                .append("{\"to\":\"+91").append(phone)
                .append("\"");
        if (properties.getOtpTemplateName() != null && !properties.getOtpTemplateName().isBlank()) {
            json.append(",\"template_name\":\"")
                    .append(jsonEscape(properties.getOtpTemplateName()))
                    .append("\"");
        }
        json.append(",\"var1\":\"").append(otp).append("\"}");

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://2factor.in/API/V1/OTP/SEND"))
                .timeout(Duration.ofSeconds(12))
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body().trim();
        String normalized = body.toLowerCase(Locale.ROOT);
        if (response.statusCode() < 200 || response.statusCode() >= 300 ||
                !(normalized.contains("\"status\":\"sent\"")
                        || normalized.contains("\"status\": \"sent\"")
                        || normalized.contains("\"status\":\"success\"")
                        || normalized.contains("\"status\": \"success\""))) {
            throw new IllegalStateException("2Factor current OTP API rejected request: HTTP "
                    + response.statusCode());
        }
    }

    private void sendSmsUsingLegacyApi(String apiKey, String phone, String otp) throws Exception {
        String encodedKey = enc(apiKey);
        String encodedPhone = enc("91" + phone);
        String encodedOtp = enc(otp);
        StringBuilder url = new StringBuilder("https://2factor.in/API/V1/")
                .append(encodedKey).append("/SMS/").append(encodedPhone).append('/').append(encodedOtp);
        if (properties.getOtpTemplateName() != null && !properties.getOtpTemplateName().isBlank()) {
            url.append('/').append(enc(properties.getOtpTemplateName()));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body().trim();
        String normalized = body.toLowerCase(Locale.ROOT);
        if (response.statusCode() < 200 || response.statusCode() >= 300 ||
                !(normalized.contains("\"status\":\"success\"")
                        || normalized.contains("\"status\": \"success\"")
                        || normalized.contains("\"status\":success"))) {
            throw new IllegalStateException("2Factor legacy OTP API rejected request: HTTP "
                    + response.statusCode());
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Mobile number is required.");
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.startsWith("91") && digits.length() == 12) digits = digits.substring(2);
        if (!digits.matches("[6-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number.");
        }
        return digits;
    }

    private String hash(String phone, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest((phone + ":" + otp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
