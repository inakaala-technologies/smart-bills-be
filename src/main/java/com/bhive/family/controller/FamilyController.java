package com.bhive.family.controller;

import com.bhive.auth.entity.User;
import com.bhive.auth.repository.UserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.family.dto.FamilyMemberResponse;
import com.bhive.family.dto.FamilyResponse;
import com.bhive.family.dto.InviteFamilyMemberRequest;
import com.bhive.family.entity.Family;
import com.bhive.family.entity.FamilyMember;
import com.bhive.family.entity.FamilyMemberStatus;
import com.bhive.family.repository.FamilyMemberRepository;
import com.bhive.family.repository.FamilyRepository;
import com.bhive.notification.service.NotificationService;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

	private static final int MAX_FAMILY_SIZE = 4;

	private final FamilyRepository familyRepository;
	private final FamilyMemberRepository familyMemberRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

	public FamilyController(FamilyRepository familyRepository, FamilyMemberRepository familyMemberRepository,
			UserRepository userRepository, NotificationService notificationService) {
		this.familyRepository = familyRepository;
		this.familyMemberRepository = familyMemberRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	@PostMapping
	@Transactional
	public ResponseEntity<FamilyResponse> createFamily() {
		Long userId = requireCurrentUserId();

		if (familyMemberRepository.findByUserId(userId).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"You already belong to a family or have a pending family invitation.");
		}

		if (familyRepository.findByCreatedBy(userId).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already created a family.");
		}

		Family family = new Family();
		family.setCreatedBy(userId);

		try {
			family = familyRepository.saveAndFlush(family);

			FamilyMember head = new FamilyMember();
			head.setFamilyId(family.getId());
			head.setUserId(userId);
			head.setStatus(FamilyMemberStatus.ACCEPTED);
			head.setInvitedBy(null);
			head.setJoinedAt(Instant.now());

			head = familyMemberRepository.saveAndFlush(head);

			User user = userRepository.findById(userId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

			FamilyMemberResponse memberResponse = new FamilyMemberResponse(head, user.getName(), user.getEmail());

			return ResponseEntity.status(HttpStatus.CREATED).body(new FamilyResponse(family, List.of(memberResponse)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"You already belong to a family or have created one.");
		}
	}

	@PostMapping("/invite")
	@Transactional
	public ResponseEntity<String> inviteMember(@Valid @RequestBody InviteFamilyMemberRequest request) {
		Long inviterUserId = requireCurrentUserId();

		FamilyMember inviterMembership = requireAcceptedMembership(inviterUserId);

		Family family = familyRepository.findById(inviterMembership.getFamilyId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found."));

		if (!family.getCreatedBy().equals(inviterUserId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the family head can invite members.");
		}

		String contact = request.getContact().trim();
		User invitee = findRegisteredUser(contact);

		if (invitee == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"This user is not registered yet. Ask them to create an account before sending a family invitation.");
		}

		Long invitedUserId = invitee.getId();

		if (inviterUserId.equals(invitedUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot invite yourself.");
		}

		if (familyMemberRepository.findByUserId(invitedUserId).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This user already belongs to a family or has a pending family invitation.");
		}

		if (familyMemberRepository.countByFamilyId(family.getId()) >= MAX_FAMILY_SIZE) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The family has reached its maximum size of four, including pending invitations.");
		}

		FamilyMember invitation = new FamilyMember();
		invitation.setFamilyId(family.getId());
		invitation.setUserId(invitedUserId);
		invitation.setStatus(FamilyMemberStatus.PENDING);
		invitation.setInvitedBy(inviterUserId);
		invitation.setJoinedAt(null);

		try {
			familyMemberRepository.saveAndFlush(invitation);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This user already belongs to a family or has a pending invitation.");
		}

		// NotificationService is tenant-scoped in the current application.
		// Do not fail or roll back a valid family invitation when there is
		// no active tenant in this request.
		notifyIfTenantAvailable(invitedUserId, "FAMILY_INVITATION", "Family invitation",
				"You have received a family invitation. Accept or reject it from your family invitations.",
				"/dashboard");

		return ResponseEntity.status(HttpStatus.CREATED).body("Family invitation sent successfully.");
	}

	@GetMapping("/invitations")
	@Transactional(readOnly = true)
	public ResponseEntity<List<FamilyMemberResponse>> getInvitations() {

		Long userId = requireCurrentUserId();

		List<FamilyMemberResponse> invitations = familyMemberRepository
				.findByUserIdAndStatusOrderByIdDesc(userId, FamilyMemberStatus.PENDING).stream().map(member -> {
					User inviter = userRepository.findById(member.getInvitedBy()).orElseThrow(
							() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family inviter not found."));

					return new FamilyMemberResponse(member, inviter.getName(), inviter.getEmail());
				}).toList();

		return ResponseEntity.ok(invitations);
	}

	@PostMapping("/invitations/{familyMemberId}/accept")
	@Transactional
	public ResponseEntity<String> acceptInvitation(@PathVariable Long familyMemberId) {
		Long userId = requireCurrentUserId();

		FamilyMember invitation = familyMemberRepository
				.findByIdAndUserIdAndStatus(familyMemberId, userId, FamilyMemberStatus.PENDING)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Pending family invitation not found."));

		invitation.setStatus(FamilyMemberStatus.ACCEPTED);
		invitation.setJoinedAt(Instant.now());
		familyMemberRepository.save(invitation);

		Long inviterUserId = invitation.getInvitedBy();

		if (inviterUserId != null) {
			notifyIfTenantAvailable(inviterUserId, "FAMILY_INVITATION_ACCEPTED", "Family invitation accepted",
					"Your family invitation has been accepted.", "/dashboard");
		}

		return ResponseEntity.ok("Family invitation accepted successfully.");
	}

	@PostMapping("/invitations/{familyMemberId}/reject")
	@Transactional
	public ResponseEntity<String> rejectInvitation(@PathVariable Long familyMemberId) {
		Long userId = requireCurrentUserId();

		FamilyMember invitation = familyMemberRepository
				.findByIdAndUserIdAndStatus(familyMemberId, userId, FamilyMemberStatus.PENDING)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Pending family invitation not found."));

		Long inviterUserId = invitation.getInvitedBy();

		// Rejection deletes the pending row, freeing the user to be invited
		// again later or to create/join another family.
		familyMemberRepository.delete(invitation);

		if (inviterUserId != null) {
			notifyIfTenantAvailable(inviterUserId, "FAMILY_INVITATION_REJECTED", "Family invitation rejected",
					"Your family invitation was rejected.", "/dashboard");
		}

		return ResponseEntity.ok("Family invitation rejected successfully.");
	}

	@GetMapping("/me")
	@Transactional(readOnly = true)
	public ResponseEntity<FamilyResponse> getMyFamily() {
		Long userId = requireCurrentUserId();

		FamilyMember membership = requireAcceptedMembership(userId);

		Family family = familyRepository.findById(membership.getFamilyId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found."));

		List<FamilyMemberResponse> members = familyMemberRepository.findByFamilyIdOrderByIdAsc(family.getId()).stream()
				.map(member -> {
					User user = userRepository.findById(member.getUserId()).orElseThrow(
							() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family member user not found."));

					return new FamilyMemberResponse(member, user.getName(), user.getEmail());
				}).toList();

		return ResponseEntity.ok(new FamilyResponse(family, members));
	}

	@DeleteMapping("/members/{memberUserId}")
	@Transactional
	public ResponseEntity<String> removeMember(@PathVariable Long memberUserId) {
		Long headUserId = requireCurrentUserId();

		FamilyMember headMembership = requireAcceptedMembership(headUserId);

		Family family = familyRepository.findById(headMembership.getFamilyId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found."));

		if (!family.getCreatedBy().equals(headUserId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the family head can remove members.");
		}

		if (headUserId.equals(memberUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The family head cannot remove themselves.");
		}

		FamilyMember member = familyMemberRepository.findByUserIdAndStatus(memberUserId, FamilyMemberStatus.ACCEPTED)
				.filter(existing -> existing.getFamilyId().equals(family.getId())).orElseThrow(
						() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accepted family member not found."));

		familyMemberRepository.delete(member);

		return ResponseEntity.ok("Family member removed successfully.");
	}

	private Long requireCurrentUserId() {
		Long userId = TenantContext.getUserId();

		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated.");
		}

		return userId;
	}

	private FamilyMember requireAcceptedMembership(Long userId) {
		return familyMemberRepository.findByUserIdAndStatus(userId, FamilyMemberStatus.ACCEPTED)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"You do not currently belong to an accepted family."));
	}

	private User findRegisteredUser(String contact) {
		if (contact.contains("@")) {
			return userRepository.findByEmail(contact.toLowerCase(Locale.ROOT)).orElse(null);
		}

		// The supplied phone must use the same normalized format stored
		// by registration. Add normalization here if registration uses it.
		return userRepository.findByPhone(contact).orElse(null);
	}

	private void notifyIfTenantAvailable(Long recipientUserId, String type, String title, String message,
			String targetUrl) {
		Long tenantId = TenantContext.getTenantId();

		if (tenantId == null || recipientUserId == null) {
			return;
		}

		notificationService.create(tenantId, recipientUserId, type, title, message, targetUrl);
	}
}