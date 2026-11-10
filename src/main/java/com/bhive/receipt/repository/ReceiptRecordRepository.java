package com.bhive.receipt.repository;

import com.bhive.receipt.entity.ReceiptRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReceiptRecordRepository extends JpaRepository<ReceiptRecord, Long> {
    Optional<ReceiptRecord> findByPartnerIdAndIdempotencyKey(String partnerId, String idempotencyKey);
    Optional<ReceiptRecord> findByPartnerIdAndExternalReceiptId(String partnerId, String externalReceiptId);
    List<ReceiptRecord> findTop100ByUserIdOrderByTransactionAtDesc(Long userId);
}
