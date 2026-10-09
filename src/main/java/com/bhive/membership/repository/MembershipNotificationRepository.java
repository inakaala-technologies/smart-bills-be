package com.bhive.membership.repository;

import com.bhive.membership.entity.MembershipNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipNotificationRepository extends JpaRepository<MembershipNotification, Long> {
    List<MembershipNotification> findByCustomerIdOrderBySentAtDesc(Long customerId);
    List<MembershipNotification> findByBusinessIdAndMembershipIdOrderBySentAtDesc(Long businessId, Long membershipId);
}