package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query(value = "SELECT nextval('payment_receipt_seq')", nativeQuery = true)
    long nextReceiptSequence();

    List<Payment> findByPatientIdAndVoidedAtIsNullOrderByPaidAtAsc(UUID patientId);
}
