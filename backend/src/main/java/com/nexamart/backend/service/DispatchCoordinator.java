package com.nexamart.backend.service;

import com.nexamart.backend.config.VJoyKartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Entry points that trigger dispatch outside of a business transaction: right after an order is
 * committed, when a partner becomes free/online, and periodically for orders still waiting.
 * Each order is dispatched in its own transaction and failures never break the caller.
 */
@Component
public class DispatchCoordinator {
  private static final Logger log = LoggerFactory.getLogger(DispatchCoordinator.class);
  private final DeliveryDispatchService dispatch;
  private final VJoyKartProperties props;

  public DispatchCoordinator(DeliveryDispatchService dispatch, VJoyKartProperties props) {
    this.dispatch = dispatch;
    this.props = props;
  }

  public DeliveryDispatchService.DispatchResult dispatchSafely(Long orderId) {
    try {
      return dispatch.dispatch(orderId);
    } catch (RuntimeException e) {
      // e.g. lost a race on the unique active-order index; the retry job will try again.
      log.warn("Dispatch of order {} failed: {}", orderId, e.getMessage());
      return null;
    }
  }

  public void dispatchPendingOrders() {
    for (Long id : dispatch.ordersAwaitingDispatch()) {
      DeliveryDispatchService.DispatchResult r = dispatchSafely(id);
      if (r != null && r.outcome() == DeliveryDispatchService.Outcome.NO_PARTNER_AVAILABLE) break;
    }
  }

  @Scheduled(fixedDelayString = "${vjoykart.dispatch.retry-interval-ms:30000}", initialDelayString = "${vjoykart.dispatch.retry-interval-ms:30000}")
  public void retryWaitingOrders() {
    if (!props.getDispatch().isRetryEnabled()) return;
    try {
      dispatchPendingOrders();
    } catch (RuntimeException e) {
      log.warn("Dispatch retry job failed: {}", e.getMessage());
    }
  }
}
