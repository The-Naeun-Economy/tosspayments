package com.example.demo.repository;

import com.example.demo.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query(value = "SELECT p FROM Payment p WHERE p.user.id = :userId ORDER BY p.id DESC")
    Page<Payment> findMyPayments(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT p FROM Payment p ORDER BY p.id DESC")
    Page<Payment> findAllPayments(Pageable pageable);

    @Query(value = "select p from Payment p where p.requestedAt between :startAt and :endAt order by p.id DESC ")
    Page<Payment> findBetweenAt(@Param("startAt") ZonedDateTime startAt, @Param("endAt") ZonedDateTime endAt, Pageable pageable);


}
