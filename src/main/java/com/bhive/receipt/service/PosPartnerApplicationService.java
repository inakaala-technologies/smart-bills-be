package com.bhive.receipt.service;

import com.bhive.receipt.dto.AdminPosPartnerApplicationResponse;
import com.bhive.receipt.dto.PosPartnerApplicationRequest;
import com.bhive.receipt.dto.PosPartnerApplicationStatusResponse;
import com.bhive.receipt.dto.PosPartnerApplicationSubmissionResponse;
import com.bhive.receipt.entity.PosIntegration;
import com.bhive.receipt.entity.PosPartnerApplication;
import com.bhive.receipt.repository.PosIntegrationRepository;
import com.bhive.receipt.repository.PosPartnerApplicationRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PosPartnerApplicationService {

    private static final String PENDING = "PENDING";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PosPartnerApplicationRepository applicationRepository;
    private final PosIntegrationRepository integrationRepository;

    public PosPartnerApplicationService(
        PosPartnerApplicationRepository applicationRepository,
        PosIntegrationRepository integrationRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.integrationRepository = integrationRepository;
    }

    @Transactional
    public PosPartnerApplicationSubmissionResponse submit(PosPartnerApplicationRequest request) {
        String publicKey = request.publicKey().trim();
        if (!isValidProof(request, publicKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ed25519 public key or proof signature.");
        }
        if (applicationRepository.existsByPublicKeyAndStatusIn(publicKey, List.of(PENDING, "APPROVED"))
            || integrationRepository.existsByPublicKey(publicKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This public key is already associated with a partner.");
        }

        byte[] rawStatusToken = new byte[32];
        SECURE_RANDOM.nextBytes(rawStatusToken);
        String statusToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawStatusToken);
        String applicationId = UUID.randomUUID().toString();
        PosPartnerApplication application = new PosPartnerApplication(
            applicationId,
            sha256Hex(statusToken),
            request.partnerName().trim(),
            request.contactName().trim(),
            request.contactEmail().trim().toLowerCase(),
            request.contactPhone().trim(),
            request.retailerReference().trim(),
            request.retailerName().trim(),
            request.outletReference().trim(),
            request.outletName().trim(),
            publicKey
        );
        applicationRepository.save(application);
        return new PosPartnerApplicationSubmissionResponse(applicationId, statusToken, PENDING);
    }

    @Transactional(readOnly = true)
    public PosPartnerApplicationStatusResponse getStatus(String applicationId, String statusToken) {
        if (statusToken == null || statusToken.length() > 128) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found.");
        }
        PosPartnerApplication application = applicationRepository
            .findByApplicationIdAndStatusTokenHash(applicationId, sha256Hex(statusToken))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
        return new PosPartnerApplicationStatusResponse(
            application.getApplicationId(),
            application.getStatus(),
            application.getPartnerId(),
            application.getDecisionReason(),
            toUtcInstant(application.getSubmittedAt()),
            toUtcInstant(application.getReviewedAt())
        );
    }

    @Transactional(readOnly = true)
    public List<AdminPosPartnerApplicationResponse> listApplications() {
        return applicationRepository.findAllByOrderBySubmittedAtDesc().stream()
            .map(this::toAdminResponse)
            .toList();
    }

    @Transactional
    public AdminPosPartnerApplicationResponse approve(String applicationId, Long adminUserId) {
        PosPartnerApplication application = findPendingApplication(applicationId);
        if (integrationRepository.existsByPublicKey(application.getPublicKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This public key already belongs to a POS integration.");
        }
        String partnerId = UUID.randomUUID().toString();
        integrationRepository.save(new PosIntegration(
            partnerId,
            application.getPartnerName(),
            application.getRetailerReference(),
            application.getRetailerName(),
            application.getOutletReference(),
            application.getOutletName(),
            application.getPublicKey()
        ));
        application.approve(partnerId, adminUserId, null);
        return toAdminResponse(applicationRepository.save(application));
    }

    @Transactional
    public AdminPosPartnerApplicationResponse reject(String applicationId, Long adminUserId, String reason) {
        PosPartnerApplication application = findPendingApplication(applicationId);
        application.reject(adminUserId, reason.trim());
        return toAdminResponse(applicationRepository.save(application));
    }

    private PosPartnerApplication findPendingApplication(String applicationId) {
        PosPartnerApplication application = applicationRepository.findByApplicationIdForUpdate(applicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
        if (!PENDING.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application has already been reviewed.");
        }
        return application;
    }

    private AdminPosPartnerApplicationResponse toAdminResponse(PosPartnerApplication application) {
        return new AdminPosPartnerApplicationResponse(
            application.getApplicationId(),
            application.getPartnerName(),
            application.getContactName(),
            application.getContactEmail(),
            application.getContactPhone(),
            application.getRetailerReference(),
            application.getRetailerName(),
            application.getOutletReference(),
            application.getOutletName(),
            application.getPublicKey(),
            application.getStatus(),
            application.getPartnerId(),
            application.getDecisionReason(),
            toUtcInstant(application.getSubmittedAt()),
            toUtcInstant(application.getReviewedAt()),
            application.getReviewedBy()
        );
    }

    private Instant toUtcInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private boolean isValidProof(PosPartnerApplicationRequest request, String encodedPublicKey) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(encodedPublicKey);
            var publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonicalApplication(request).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(request.proofSignature().trim()));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            return false;
        }
    }

    public static String canonicalApplication(PosPartnerApplicationRequest request) {
        return String.join("\n",
            "BHIVE_POS_ONBOARDING_V1",
            request.partnerName().trim(),
            request.contactName().trim(),
            request.contactEmail().trim().toLowerCase(),
            request.contactPhone().trim(),
            request.retailerReference().trim(),
            request.retailerName().trim(),
            request.outletReference().trim(),
            request.outletName().trim(),
            request.publicKey().trim()
        );
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
