package com.example.demo.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService{

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
}
