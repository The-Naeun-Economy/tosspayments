package com.example.demo.Service;

import com.example.demo.Response.PaymentResponse;
import com.example.demo.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {
    Long tokenGetUserId(String token);
    Page<PaymentResponse> getPaymentByUserId(Long userId, Pageable pageable);
    Page<PaymentResponse> getPaymentAll(Pageable pageable);
    String getRemaining(Long userId);
    Page<PaymentResponse> getBetweenAt(String start, String end, int page, int size);
}
