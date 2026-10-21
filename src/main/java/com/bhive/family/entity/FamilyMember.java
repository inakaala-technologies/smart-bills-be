package com.bhive.family.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "family_members")
public class FamilyMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "family_id", nullable = false)
	private Long familyId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private FamilyMemberStatus status;

	@Column(name = "invited_by")
	private Long invitedBy;

	@Column(name = "joined_at")
	private Instant joinedAt;

	public FamilyMember() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getFamilyId() {
		return familyId;
	}

	public void setFamilyId(Long familyId) {
		this.familyId = familyId;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public FamilyMemberStatus getStatus() {
		return status;
	}

	public void setStatus(FamilyMemberStatus status) {
		this.status = status;
	}

	public Long getInvitedBy() {
		return invitedBy;
	}

	public void setInvitedBy(Long invitedBy) {
		this.invitedBy = invitedBy;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public void setJoinedAt(Instant joinedAt) {
		this.joinedAt = joinedAt;
	}
}