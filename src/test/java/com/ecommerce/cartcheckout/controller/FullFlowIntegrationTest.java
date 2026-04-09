package com.ecommerce.cartcheckout.controller;

import com.ecommerce.cartcheckout.controller.dto.Dtos;
import com.ecommerce.cartcheckout.service.MockPaymentProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Full E2E Flow Integration Tests")
class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MockPaymentProvider mockPaymentProvider; // Mock to prevent actual async calls

    @Test
    @DisplayName("Happy Path: Create cart → Add items → Checkout → Pay → PAID")
    void happyPath() throws Exception {
        // 1. Create cart
        MvcResult cartResult = mockMvc.perform(post("/carts"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String cartId = objectMapper.readTree(cartResult.getResponse().getContentAsString())
                .get("id").asText();

        // 2. Add items
        String item1 = objectMapper.writeValueAsString(Map.of(
                "productId", "PROD-001", "quantity", 2, "price", 29.99));
        String item2 = objectMapper.writeValueAsString(Map.of(
                "productId", "PROD-002", "quantity", 1, "price", 49.99));

        mockMvc.perform(post("/carts/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON).content(item1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(post("/carts/{cartId}/items", cartId)
                        .contentType(MediaType.APPLICATION_JSON).content(item2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalPrice").value(109.97));

        // 3. Checkout
        MvcResult orderResult = mockMvc.perform(post("/carts/{cartId}/checkout", cartId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(109.97))
                .andReturn();

        String orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString())
                .get("id").asText();

        // 4. Start payment
        MvcResult paymentResult = mockMvc.perform(post("/orders/{orderId}/payment/start", orderId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.idempotencyKey").exists())
                .andReturn();

        String idempotencyKey = objectMapper.readTree(paymentResult.getResponse().getContentAsString())
                .get("idempotencyKey").asText();
        String paymentAttemptId = objectMapper.readTree(paymentResult.getResponse().getContentAsString())
                .get("paymentAttemptId").asText();

        // Verify order is now PENDING_PAYMENT
        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        // 5. Simulate webhook CONFIRMED
        Dtos.WebhookRequest webhook = Dtos.WebhookRequest.builder()
                .paymentAttemptId(UUID.fromString(paymentAttemptId))
                .idempotencyKey(idempotencyKey)
                .result("CONFIRMED")
                .providerReference("MOCK_REF_12345")
                .build();

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        // 6. Verify order is PAID
        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("Duplicate Webhook: Second identical webhook is handled idempotently")
    void duplicateWebhook() throws Exception {
        // Setup: Create cart → add item → checkout → start payment
        MvcResult cartResult = mockMvc.perform(post("/carts"))
                .andExpect(status().isCreated()).andReturn();
        String cartId = objectMapper.readTree(cartResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/carts/{cartId}/items", cartId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "productId", "PROD-X", "quantity", 1, "price", 10.00))));

        MvcResult orderResult = mockMvc.perform(post("/carts/{cartId}/checkout", cartId))
                .andReturn();
        String orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult payResult = mockMvc.perform(post("/orders/{orderId}/payment/start", orderId))
                .andReturn();
        String idempotencyKey = objectMapper.readTree(payResult.getResponse().getContentAsString())
                .get("idempotencyKey").asText();
        String paymentAttemptId = objectMapper.readTree(payResult.getResponse().getContentAsString())
                .get("paymentAttemptId").asText();

        Dtos.WebhookRequest webhook = Dtos.WebhookRequest.builder()
                .paymentAttemptId(UUID.fromString(paymentAttemptId))
                .idempotencyKey(idempotencyKey)
                .result("CONFIRMED")
                .providerReference("MOCK_REF_DUP")
                .build();

        // First webhook — should succeed
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk());

        // Second identical webhook — should be handled idempotently (no error)
        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(webhook)))
                .andExpect(status().isOk());

        // Order should still be PAID (not corrupted)
        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("Payment Failure + Retry: FAILED → retry → PAID")
    void paymentFailureAndRetry() throws Exception {
        // Setup
        MvcResult cartResult = mockMvc.perform(post("/carts"))
                .andExpect(status().isCreated()).andReturn();
        String cartId = objectMapper.readTree(cartResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/carts/{cartId}/items", cartId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "productId", "PROD-Y", "quantity", 1, "price", 75.00))));

        MvcResult orderResult = mockMvc.perform(post("/carts/{cartId}/checkout", cartId))
                .andReturn();
        String orderId = objectMapper.readTree(orderResult.getResponse().getContentAsString())
                .get("id").asText();

        // First payment attempt
        MvcResult pay1 = mockMvc.perform(post("/orders/{orderId}/payment/start", orderId))
                .andExpect(status().isAccepted()).andReturn();
        String key1 = objectMapper.readTree(pay1.getResponse().getContentAsString())
                .get("idempotencyKey").asText();
        String attemptId1 = objectMapper.readTree(pay1.getResponse().getContentAsString())
                .get("paymentAttemptId").asText();

        // Webhook: FAILED
        Dtos.WebhookRequest failWebhook = Dtos.WebhookRequest.builder()
                .paymentAttemptId(UUID.fromString(attemptId1))
                .idempotencyKey(key1)
                .result("FAILED")
                .providerReference("MOCK_FAIL_001")
                .build();

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failWebhook)))
                .andExpect(status().isOk());

        // Verify PAYMENT_FAILED
        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        // Second payment attempt (retry)
        MvcResult pay2 = mockMvc.perform(post("/orders/{orderId}/payment/start", orderId))
                .andExpect(status().isAccepted()).andReturn();
        String key2 = objectMapper.readTree(pay2.getResponse().getContentAsString())
                .get("idempotencyKey").asText();
        String attemptId2 = objectMapper.readTree(pay2.getResponse().getContentAsString())
                .get("paymentAttemptId").asText();

        // Webhook: CONFIRMED
        Dtos.WebhookRequest confirmWebhook = Dtos.WebhookRequest.builder()
                .paymentAttemptId(UUID.fromString(attemptId2))
                .idempotencyKey(key2)
                .result("CONFIRMED")
                .providerReference("MOCK_OK_002")
                .build();

        mockMvc.perform(post("/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmWebhook)))
                .andExpect(status().isOk());

        // Verify PAID
        mockMvc.perform(get("/orders/{orderId}", orderId))
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
