package com.bhive.auth.repository;

import com.bhive.auth.entity.UserAccount;
import com.bhive.auth.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    List<UserAccount> findByUserId(Long userId);
    List<UserAccount> findByUserIdAndRole(Long userId, UserRole role);
    List<UserAccount> findByTenantId(Long tenantId);
}
