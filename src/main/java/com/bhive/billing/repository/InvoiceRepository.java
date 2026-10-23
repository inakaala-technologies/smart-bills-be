package com.bhive.billing.repository;

import com.bhive.billing.entity.Invoice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
	List<Invoice> findByBusinessId(Long businessId);

	List<Invoice> findByCustomerIdIn(Collection<Long> customerIds);
	Optional<Invoice> findByAppointmentId(Long appointmentId);
}
