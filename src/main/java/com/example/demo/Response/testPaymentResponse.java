package com.example.demo.Response;

import com.example.demo.domain.Payment;
import com.example.demo.domain.User;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.json.simple.JSONObject;

import java.time.ZonedDateTime;

@Data
@Getter
@NoArgsConstructor
public class testPaymentResponse {
    public static Payment toEntity(JSONObject response, User user) {
        return Payment.builder()
                .user(user)
                .orderId(response.get("orderId").toString())
                .paymentKey(response.get("paymentKey").toString())
                .requestedAt(ZonedDateTime.parse(response.get("requestedAt").toString()))
                .method(response.get("method").toString())
                .amount((Long) response.get("totalAmount"))
                .build();
    }
}
