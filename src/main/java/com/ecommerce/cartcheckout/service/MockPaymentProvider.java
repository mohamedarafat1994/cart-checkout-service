package com.ecommerce.cartcheckout.service;

import com.ecommerce.cartcheckout.controller.dto.Dtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class MockPaymentProvider {

    private final String webhookUrl;
    private final long delayMs;
    private final RestClient restClient;

    public MockPaymentProvider(
            @Value("${mock.payment.webhook-url}") String webhookUrl,
            @Value("${mock.payment.delay-ms:2000}") long delayMs) {
        this.webhookUrl = webhookUrl;
        this.delayMs = delayMs;
        this.restClient = RestClient.create();
    }

    @Async
    public void simulatePayment(UUID paymentAttemptId, String idempotencyKey, BigDecimal amount) {
        log.info("[MockProvider] Payment received: attemptId={}, amount={}", paymentAttemptId, amount);

        try {
            Thread.sleep(delayMs);

            Dtos.WebhookRequest webhook = Dtos.WebhookRequest.builder()
                    .paymentAttemptId(paymentAttemptId)
                    .idempotencyKey(idempotencyKey)
                    .result("CONFIRMED")
                    .providerReference("MOCK_REF_" + UUID.randomUUID().toString().substring(0, 8))
                    .build();

            log.info("[MockProvider] Sending webhook: {}", webhook);

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(webhook)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[MockProvider] Webhook delivered successfully for attemptId={}", paymentAttemptId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[MockProvider] Payment simulation interrupted", e);
        } catch (Exception e) {
            log.error("[MockProvider] Failed to deliver webhook for attemptId={}: {}",
                    paymentAttemptId, e.getMessage());
        }
    }
}
