package com.nexamart.backend.service;

import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.NotificationRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

/** Writes order notifications into the existing in-app notifications table (polled by the apps). */
@Service
public class OrderNotifier {
  private final NotificationRepository notifications;
  private final UserAccountRepository users;

  public OrderNotifier(NotificationRepository notifications, UserAccountRepository users) {
    this.notifications = notifications;
    this.users = users;
  }

  public void notify(UserAccount user, NotificationType type, String title, String message, Order order, String actionUrl) {
    Notification n = new Notification();
    n.setUser(user);
    n.setTitle(title);
    n.setMessage(message);
    n.setType(type);
    n.setOrderId(order.getId());
    n.setActionUrl(actionUrl);
    notifications.save(n);
  }

  public void deliveryAssigned(Order order, String storeName) {
    String drop = order.getDeliveryAddress() == null || order.getDeliveryAddress().getAddressLine() == null
        ? "" : " • Deliver to: " + order.getDeliveryAddress().getAddressLine();
    notify(order.getDeliveryPartner(), NotificationType.ORDER_ASSIGNED, "New delivery assigned",
        "Order #" + order.getId() + " • Pickup: " + storeName + drop, order, "/delivery/orders/" + order.getId());
    notify(order.getCustomer(), NotificationType.ORDER_STATUS, "Delivery boy assigned",
        order.getDeliveryPartner().getName() + " will deliver your order #" + order.getId() + ".", order, "/orders/" + order.getId());
  }

  public void customerStatus(Order order, DeliveryStatus status) {
    notify(order.getCustomer(), NotificationType.ORDER_STATUS, "Order update",
        "Order #" + order.getId() + ": " + status.label() + ".", order, "/orders/" + order.getId());
  }

  public void admins(Order order, String title, String message) {
    users.findAll().stream()
        .filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == AccountStatus.ACTIVE)
        .forEach(admin -> notify(admin, NotificationType.ORDER_STATUS, title, message, order, "/admin/orders/" + order.getId()));
  }
}
