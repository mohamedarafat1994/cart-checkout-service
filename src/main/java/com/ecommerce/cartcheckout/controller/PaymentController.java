package com.ecommerce.cartcheckout.controller;

import com.ecommerce.cartcheckout.controller.dto.Dtos;
import com.ecommerce.cartcheckout.domain.model.PaymentAttempt;
import com.ecommerce.cartcheckout.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders/{orderId}/payment/start")
    public ResponseEntity<Dtos.PaymentStartResponse> startPayment(@PathVariable UUID orderId) {
        PaymentAttempt attempt = paymentService.startPayment(orderId);

        Dtos.PaymentStartResponse response = Dtos.PaymentStartResponse.builder()
                .paymentAttemptId(attempt.getId())
                .orderId(orderId)
                .idempotencyKey(attempt.getIdempotencyKey())
                .status(attempt.getStatus().name())
                .amount(attempt.getAmount())
                .message("Payment initiated. Mock provider will send webhook callback.")
                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }


    @PostMapping("/payments/webhook")
    public ResponseEntity<Dtos.WebhookResponse> handleWebhook(
            @Valid @RequestBody Dtos.WebhookRequest request) {

        log.info("Webhook received: attemptId={}, idempotencyKey={}, result={}",
                request.getPaymentAttemptId(), request.getIdempotencyKey(), request.getResult());

        PaymentAttempt attempt = paymentService.processWebhook(
                request.getPaymentAttemptId(),
                request.getIdempotencyKey(),
                request.getResult(),
                request.getProviderReference());

        Dtos.WebhookResponse response = Dtos.WebhookResponse.builder()
                .status("OK")
                .message("Webhook processed. Payment status: " + attempt.getStatus().name())
                .build();

        return ResponseEntity.ok(response);
    }
}
