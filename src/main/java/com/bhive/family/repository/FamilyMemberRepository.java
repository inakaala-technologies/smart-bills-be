package com.bhive.family.repository;

import com.bhive.family.entity.FamilyMember;
import com.bhive.family.entity.FamilyMemberStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

	Optional<FamilyMember> findByUserId(Long userId);

	Optional<FamilyMember> findByUserIdAndStatus(Long userId, FamilyMemberStatus status);

	Optional<FamilyMember> findByIdAndUserIdAndStatus(Long id, Long userId, FamilyMemberStatus status);

	List<FamilyMember> findByFamilyIdAndStatusOrderByIdAsc(Long familyId, FamilyMemberStatus status);

	List<FamilyMember> findByUserIdAndStatusOrderByIdDesc(Long userId, FamilyMemberStatus status);

	long countByFamilyId(Long familyId);

	long countByFamilyIdAndStatus(Long familyId, FamilyMemberStatus status);

	void deleteByFamilyIdAndUserId(Long familyId, Long userId);
}