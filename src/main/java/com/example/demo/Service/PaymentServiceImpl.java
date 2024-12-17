package com.example.demo.Service;

import com.example.demo.Dto.IsBilling;
import com.example.demo.Response.PaymentResponse;
import com.example.demo.Response.testPaymentResponse;
import com.example.demo.domain.Payment;
import com.example.demo.domain.User;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public void UserIfPresentOrElse(Long id , JSONObject response){
        ZonedDateTime parsedDateTime = ZonedDateTime.parse(response.get("requestedAt").toString());
        int days = response.get("totalAmount").toString().equals("5900") ? 30 : 365;

        userRepository.findById(id).ifPresentOrElse(user -> {
            ZonedDateTime newDateTime = user.getRemaining().plusDays(days);
            user.setRemaining(newDateTime);
            Payment payment = testPaymentResponse.toEntity(response, user);
            paymentRepository.save(payment);
            user.getPayments().add(payment);
            userRepository.save(user);
        }, () -> {
            ZonedDateTime newDateTime = parsedDateTime.plusDays(days);
            Set<Payment> payments = new HashSet<>();
            User newUser = new User(id, newDateTime, payments);
            userRepository.save(newUser);
            Payment payment = testPaymentResponse.toEntity(response, newUser);
            paymentRepository.save(payment);
            payments.add(payment);
            userRepository.save(newUser);
        });

    }


}
