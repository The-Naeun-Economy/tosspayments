package com.example.demo.repository;

import com.example.demo.domain.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

//    @Query("SELECT p.remaining FROM Payment p WHERE p.id = :userId ORDER BY p.id DESC")
//    List<Integer> findRemainingPayments(@Param("userId") Long userId, Pageable pageable);

}
