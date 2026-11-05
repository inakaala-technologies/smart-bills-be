package com.bhive.billing.repository;

import com.bhive.billing.entity.Invoice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
	@Override
	@EntityGraph(attributePaths = "lineItems")
	List<Invoice> findAll();

	@Override
	@EntityGraph(attributePaths = "lineItems")
	Optional<Invoice> findById(Long id);

	@EntityGraph(attributePaths = "lineItems")
	List<Invoice> findByBusinessId(Long businessId);

	@EntityGraph(attributePaths = "lineItems")
	List<Invoice> findByCustomerIdIn(Collection<Long> customerIds);

	@EntityGraph(attributePaths = "lineItems")
	Optional<Invoice> findByAppointmentId(Long appointmentId);
}
