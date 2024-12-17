package com.example.demo.Service;

import com.example.demo.Response.PaymentResponse;
import com.example.demo.domain.Payment;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    @Override
    public Long tokenGetUserId(String BearerToken) {
        String token = BearerToken.substring(7);
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecretKey)
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userId", Long.class);
    }

    @Override
    public Page<PaymentResponse> getPaymentByUserId(Long userId, Pageable pageable) {
        return paymentRepository.findMyPayments(userId, pageable).map(PaymentResponse::from);
    }

    @Override
    public Page<PaymentResponse> getPaymentAll(Pageable pageable) {
        return paymentRepository.findAllPayments(pageable).map(PaymentResponse::from);
    }

    @Override
    public String getRemaining(Long userId) {
        return userRepository.findRemainingByUserId(userId).toString();
    }

    @Override
    public Page<PaymentResponse> getBetweenAt(String start, String end, int page, int size){
        ZonedDateTime startAt = ZonedDateTime.parse(start + "T00:00:00+09:00");
        ZonedDateTime endAt = ZonedDateTime.parse( end + "T23:59:59+09:00");
        return paymentRepository.findBetweenAt(startAt,endAt, PageRequest.of(page, size)).map(PaymentResponse::from);
    }

    @Override
    public Long getBetweenSum(String start, String end){
        ZonedDateTime startAt = ZonedDateTime.parse(start + "T00:00:00+09:00");
        ZonedDateTime endAt = ZonedDateTime.parse( end + "T23:59:59+09:00");
        return paymentRepository.sumBetween(startAt,endAt);
    }


}
