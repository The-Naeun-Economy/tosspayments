package com.example.demo.Response;


import com.example.demo.domain.Payment;
import com.example.demo.domain.User;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentResponse (
        Long id,
        Long userId,
        String orderId,
        String paymentKey,
        String requestedAt,
        String method,
        Long amount,
        boolean isCanceled
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUser().getId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getRequestedAt(),
                payment.getMethod(),
                payment.getAmount(),
                payment.isCanceled()
        );
    }
}




