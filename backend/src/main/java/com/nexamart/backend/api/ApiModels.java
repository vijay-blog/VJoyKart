package com.nexamart.backend.api;
import com.nexamart.backend.domain.*;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.Instant;import java.util.*;
public final class ApiModels{private ApiModels(){}
 public record LoginRequest(@NotBlank String identifier,@NotBlank String password){}
 public record RegisterRequest(@NotBlank String name,@Email @NotBlank String email,String phone,@Size(min=8,max=100) String password,@NotBlank String confirmPassword){}
 public record RefreshRequest(@NotBlank String refreshToken){}
 public record OtpRequest(@NotBlank String phone){}
 public record VerifyOtpRequest(@NotBlank String phone,@NotBlank String otp){}
 public record OtpSendResponse(boolean success,String message,int expiresInSeconds,String deliveryChannel,String debugOtp){}
 public record UserResponse(Long id,String name,String phone,String email,String role){}
 public record LoginResponse(String accessToken,String refreshToken,UserResponse user){}
 public record ActionRequest(@NotBlank String action,String reason){}
 public record OrderStatusRequest(@NotBlank String status){}
 public record CancelRequest(String reason){}
 public record AssignRequest(@NotNull Long deliveryPartnerId){}
 public record OrderItemRequest(@NotNull Long productId,@Min(1) int quantity){}
 public record AddressRequest(@NotBlank String recipientName,String phone,@NotBlank String addressLine,String city,String state,String postalCode,Double latitude,Double longitude,boolean defaultAddress){}
 public record CreateOrderRequest(@NotEmpty List<OrderItemRequest> items,@NotNull AddressRequest address,PaymentMethod paymentMethod){}
 public record PaymentCreateOrderResponse(Long paymentId,Long orderId,String keyId,String gateway,String gatewayOrderId,BigDecimal amount,String currency){}
 public record PaymentVerifyRequest(@NotNull Long orderId,@NotBlank String gatewayOrderId,@NotBlank String gatewayPaymentId,@NotBlank String gatewaySignature){}
 public record ProfileUpdate(String name,String email,String vehicleType,String vehicleNumber,String licenseReference){}
 public record AvailabilityRequest(boolean available){}
 public record PageResponse<T>(List<T> content,int page,int pageSize,int totalPages,long totalElements,boolean hasNextPage,int number,int size,boolean last){public PageResponse(List<T> content,int page,int pageSize,int totalPages,long totalElements,boolean hasNextPage){this(content,page,pageSize,totalPages,totalElements,hasNextPage,page,pageSize,!hasNextPage);}}
 public record OrderItemResponse(Long id,Long productId,String productName,BigDecimal unitPrice,int quantity,BigDecimal lineTotal){}
 public record PaymentInfoDto(String method,String status,String transactionReference){}
 public record OrderTotalsDto(String subtotal,String deliveryFee,String discount,String tax,String grandTotal,String currencyCode){}
 public record DeliveryInfoDto(String status,String partnerName,String assignedAt,String partnerId){}
 public record OrderTimelineDto(String status,String timestamp){}
 public record OrderResponse(String orderId,String customerName,String customerPhone,String address,String totalAmount,String currencyCode,String status,String paymentStatus,String assignedAt,String createdAt,Integer itemCount,String deliveryStatus,UserResponse customer,List<OrderItemResponse> items,PaymentInfoDto payment,OrderTotalsDto totals,DeliveryInfoDto delivery,List<OrderTimelineDto> timeline,List<String> allowedTransitions,Boolean canCancel,UserResponse deliveryPartner,List<String> allowedActions,Boolean proofOfDeliveryRequired,String proofOfDeliveryStatus,String proofOfDeliveryUrl){}
 public record AdminRecentOrder(String orderId,String customerName,Double amount,String status,String createdAt){}
 public record DeliveryDashboardResponse(Long activeOrders,Long assignedOrders,Long pickedUpOrders,Long outForDeliveryOrders,Long completedToday,String todayEarnings,String currencyCode,String availability,List<DeliveryOrderSummary> recentOrders){}
 public record DeliveryOrderSummary(String orderId,String customerName,String customerPhone,String address,String totalAmount,String currencyCode,String status,String paymentStatus,String assignedAt,String createdAt,String amount){}
 public record CategoryResponse(String categoryId,String name,String description,String imageUrl,boolean active,int productCount,int sortOrder,Instant createdAt,Instant updatedAt,List<String> allowedActions){}
 public record ProductResponse(String productId,String name,String description,String categoryId,String categoryName,String price,String discountedPrice,String discountAmount,String discountPercent,String currencyCode,Integer stock,String sku,String unit,String status,String availability,String imageUrl,Instant createdAt,Instant updatedAt,List<String> allowedActions){}
 public record CustomerResponse(String customerId,String name,String phone,String email,String profileImageUrl,String accountStatus,Instant registeredAt,Instant lastActiveAt,long orderCount,BigDecimal totalSpent,String currencyCode,String defaultAddress,List<String> allowedActions){}
 public record DeliveryPartnerResponse(String partnerId,String name,String phone,String email,String profileImageUrl,String accountStatus,String verificationStatus,String availability,String workState,String registeredAt,String lastActiveAt,String vehicleType,String vehicleNumber,String licenseReference,DeliveryPartnerStatistics statistics,List<PartnerOrderSummary> currentOrders,List<PartnerOrderSummary> recentHistory,Boolean isAssignable,List<String> allowedActions){}
 public record DeliveryPartnerStatistics(Long totalDeliveries,Long completedDeliveries,Long cancelledDeliveries,Long activeDeliveries){}
 public record PartnerOrderSummary(String orderId,String status,String timestamp){}
 public record AdminNotificationResponse(String id,String title,String message,Instant createdAt,boolean read,Long orderId){}
 public record DashboardResponse(Long totalOrders,Long todayOrders,Long pendingOrders,Long outForDelivery,Long deliveredToday,Double todaySales,String currencyCode,List<AdminRecentOrder> recentOrders,long unreadNotifications,List<AdminNotificationResponse> notifications){}
 public record AvailabilityResponse(boolean available,String status,boolean canChange,String reason,Instant updatedAt){}
 public record ProfileResponse(Long id,String name,String phone,String email,String profileImageUrl,String verificationStatus,String accountStatus,String vehicleType,String vehicleNumber,String licenseReference,Instant registeredAt,Instant lastActiveAt,List<String> editableFields){}
 public record NotificationResponse(String id,String title,String message,Instant createdAt,boolean read,String type,Long orderId,String actionUrl){}
 public record EarningsSummary(String currencyCode,BigDecimal today,BigDecimal thisWeek,BigDecimal thisMonth,long completedDeliveries,BigDecimal pendingPayout,BigDecimal totalEarned){}
 public record EarningResponse(String id,Long orderId,Instant earnedAt,BigDecimal amount,String currencyCode,String status,String description){}
 public record ProofRequest(String notes,String proofUrl){}
}
