package com.bhive.payment.repository;

import com.bhive.payment.entity.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	List<Payment> findByBusinessId(Long businessId);
	List<Payment> findByBusinessIdAndCustomerId(Long businessId, Long customerId);
}
