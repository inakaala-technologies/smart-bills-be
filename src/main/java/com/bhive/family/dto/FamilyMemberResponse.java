package com.bhive.family.dto;

import com.bhive.family.entity.FamilyMember;
import com.bhive.family.entity.FamilyMemberStatus;

import java.time.Instant;
import java.time.LocalDateTime;

public class FamilyMemberResponse {

	private Long id;
	private Long familyId;
	private Long userId;
	private FamilyMemberStatus status;
	private Long invitedBy;
	private Instant joinedAt;

	public FamilyMemberResponse() {
	}

	public FamilyMemberResponse(FamilyMember member) {
		this.id = member.getId();
		this.familyId = member.getFamilyId();
		this.userId = member.getUserId();
		this.status = member.getStatus();
		this.invitedBy = member.getInvitedBy();
		this.joinedAt = member.getJoinedAt();
	}

	public Long getId() {
		return id;
	}

	public Long getFamilyId() {
		return familyId;
	}

	public Long getUserId() {
		return userId;
	}

	public FamilyMemberStatus getStatus() {
		return status;
	}

	public Long getInvitedBy() {
		return invitedBy;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}