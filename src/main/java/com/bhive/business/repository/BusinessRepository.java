package com.bhive.business.repository;

import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
	boolean existsByProfileId(String profileId);

	java.util.List<Business> findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(BusinessStatus status);
}
