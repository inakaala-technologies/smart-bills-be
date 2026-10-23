package com.bhive.family.dto;

import com.bhive.family.entity.Family;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public class FamilyResponse {

	private Long familyId;
	private Long createdBy;
	private Instant createdAt;
	private List<FamilyMemberResponse> members;

	public FamilyResponse() {
	}

	public FamilyResponse(Family family, List<FamilyMemberResponse> members) {
		this.familyId = family.getId();
		this.createdBy = family.getCreatedBy();
		this.createdAt = family.getCreatedAt();
		this.members = members;
	}

	public Long getFamilyId() {
		return familyId;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<FamilyMemberResponse> getMembers() {
		return members;
	}
}