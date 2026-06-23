package com.mahalaxmi.autoparts.repository;

import com.mahalaxmi.autoparts.domain.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findTop10ByOrderByPaymentDateDescCreatedAtDesc();
}
