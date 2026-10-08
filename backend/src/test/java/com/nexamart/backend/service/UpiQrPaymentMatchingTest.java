package com.nexamart.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexamart.backend.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UpiQrPaymentMatchingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode items(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void acceptsCapturedPaymentOfExactAmountAndCurrency() throws Exception {
        JsonNode list = items("[{\"id\":\"pay_1\",\"status\":\"captured\",\"amount\":21000,\"currency\":\"INR\"}]");
        assertEquals("pay_1", PaymentService.findCapturedPayment(list, 21000, "INR").orElseThrow().path("id").asText());
    }

    @Test
    void rejectsWrongAmountCurrencyOrUncapturedPayments() throws Exception {
        JsonNode list = items("["
                + "{\"id\":\"pay_a\",\"status\":\"captured\",\"amount\":20999,\"currency\":\"INR\"},"
                + "{\"id\":\"pay_b\",\"status\":\"captured\",\"amount\":21000,\"currency\":\"USD\"},"
                + "{\"id\":\"pay_c\",\"status\":\"failed\",\"amount\":21000,\"currency\":\"INR\"},"
                + "{\"id\":\"pay_d\",\"status\":\"refunded\",\"captured\":true,\"amount\":21000,\"currency\":\"INR\"},"
                + "{\"id\":\"pay_e\",\"status\":\"authorized\",\"amount\":21000,\"currency\":\"INR\"}"
                + "]");
        assertTrue(PaymentService.findCapturedPayment(list, 21000, "INR").isEmpty());
    }

    @Test
    void handlesMissingItems() throws Exception {
        assertTrue(PaymentService.findCapturedPayment(null, 100, "INR").isEmpty());
        assertTrue(PaymentService.findCapturedPayment(items("{}"), 100, "INR").isEmpty());
    }

    @Test
    void convertsOrderTotalToExactPaise() {
        assertEquals(21000, PaymentService.toPaise(new BigDecimal("210.00")));
        assertEquals(80050, PaymentService.toPaise(new BigDecimal("800.50")));
        assertThrows(ApiException.class, () -> PaymentService.toPaise(BigDecimal.ZERO));
        assertThrows(ArithmeticException.class, () -> PaymentService.toPaise(new BigDecimal("1.005")));
    }
}
