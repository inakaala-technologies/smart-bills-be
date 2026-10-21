
package com.bhive.family.repository;

import com.bhive.family.entity.Family;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, Long> {

	Optional<Family> findByCreatedBy(Long createdBy);

}
