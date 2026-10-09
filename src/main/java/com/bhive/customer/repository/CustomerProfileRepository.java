package com.bhive.customer.repository;

import com.bhive.customer.entity.CustomerProfile;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserIdAndTenantId(Long userId, Long tenantId);
    java.util.List<CustomerProfile> findByTenantId(Long tenantId);
    Page<CustomerProfile> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids, Pageable pageable);
    Page<CustomerProfile> findByTenantId(Long tenantId, Pageable pageable);
    @Query("""
        SELECT customer FROM CustomerProfile customer
        WHERE customer.tenantId = :tenantId
          AND (LOWER(COALESCE(customer.name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(customer.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(COALESCE(customer.phone, '')) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<CustomerProfile> searchByTenantId(@Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);
    boolean existsByProfileId(String profileId);
    Optional<CustomerProfile> findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNull(String email, Long tenantId);
    Optional<CustomerProfile> findFirstByPhoneAndTenantIdAndUserIdIsNull(String phone, Long tenantId);
}
