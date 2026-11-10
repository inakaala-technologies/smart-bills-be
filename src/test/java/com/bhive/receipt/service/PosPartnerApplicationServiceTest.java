package com.bhive.receipt.service;

import com.bhive.receipt.dto.PosPartnerApplicationRequest;
import com.bhive.receipt.dto.PosPartnerApplicationSubmissionResponse;
import com.bhive.receipt.entity.PosIntegration;
import com.bhive.receipt.entity.PosPartnerApplication;
import com.bhive.receipt.repository.PosIntegrationRepository;
import com.bhive.receipt.repository.PosPartnerApplicationRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosPartnerApplicationServiceTest {

    @Mock
    private PosPartnerApplicationRepository applicationRepository;

    @Mock
    private PosIntegrationRepository integrationRepository;

    @InjectMocks
    private PosPartnerApplicationService service;

    private KeyPair keyPair;
    private PosPartnerApplicationRequest request;
    private String publicKey;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        request = signedRequest("Demo POS", publicKey, keyPair);
    }

    @Test
    void submitsOnlyAfterVerifyingProofOfPrivateKeyOwnership() throws Exception {
        when(applicationRepository.existsByPublicKeyAndStatusIn(publicKey, List.of("PENDING", "APPROVED")))
            .thenReturn(false);
        when(integrationRepository.existsByPublicKey(publicKey)).thenReturn(false);
        when(applicationRepository.save(any(PosPartnerApplication.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PosPartnerApplicationSubmissionResponse response = service.submit(request);

        ArgumentCaptor<PosPartnerApplication> saved = ArgumentCaptor.forClass(PosPartnerApplication.class);
        verify(applicationRepository).save(saved.capture());
        assertEquals("PENDING", saved.getValue().getStatus());
        assertEquals("Demo POS", saved.getValue().getPartnerName());
        assertNotEquals(response.statusAccessToken(), saved.getValue().getStatusTokenHash());
        assertEquals(64, saved.getValue().getStatusTokenHash().length());
    }

    @Test
    void rejectsInvalidProofWithoutSavingApplication() throws Exception {
        PosPartnerApplicationRequest invalidRequest = new PosPartnerApplicationRequest(
            request.partnerName(),
            request.contactName(),
            request.contactEmail(),
            request.contactPhone(),
            request.retailerReference(),
            request.retailerName(),
            request.outletReference(),
            request.outletName(),
            request.publicKey(),
            Base64.getEncoder().encodeToString(new byte[64])
        );

        assertThrows(ResponseStatusException.class, () -> service.submit(invalidRequest));
        verify(applicationRepository, never()).save(any(PosPartnerApplication.class));
    }

    @Test
    void approvalCreatesActiveIntegrationWithApplicationPublicKey() {
        PosPartnerApplication application = new PosPartnerApplication(
            "application-id", "token-hash", "Demo POS", "Contact", "contact@example.com", "+10000000000",
            "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", publicKey
        );
        when(applicationRepository.findByApplicationIdForUpdate("application-id")).thenReturn(Optional.of(application));
        when(integrationRepository.save(any(PosIntegration.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.save(any(PosPartnerApplication.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.approve("application-id", 42L);

        ArgumentCaptor<PosIntegration> integration = ArgumentCaptor.forClass(PosIntegration.class);
        verify(integrationRepository).save(integration.capture());
        assertEquals(publicKey, integration.getValue().getPublicKey());
        assertEquals("ACTIVE", integration.getValue().getStatus());
        assertEquals("APPROVED", result.status());
        assertEquals(integration.getValue().getPartnerId(), result.partnerId());
        assertEquals(42L, result.reviewedBy());
    }

    @Test
    void cannotApproveApplicationMoreThanOnce() {
        PosPartnerApplication application = new PosPartnerApplication(
            "application-id", "token-hash", "Demo POS", "Contact", "contact@example.com", "+10000000000",
            "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", publicKey
        );
        application.reject(7L, "Not authorized");
        when(applicationRepository.findByApplicationIdForUpdate("application-id")).thenReturn(Optional.of(application));

        assertThrows(ResponseStatusException.class, () -> service.approve("application-id", 42L));
        verify(integrationRepository, never()).save(any(PosIntegration.class));
    }

    @Test
    void cannotActivatePublicKeyAlreadyBoundToAnIntegration() {
        PosPartnerApplication application = new PosPartnerApplication(
            "application-id", "token-hash", "Demo POS", "Contact", "contact@example.com", "+10000000000",
            "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", publicKey
        );
        when(applicationRepository.findByApplicationIdForUpdate("application-id")).thenReturn(Optional.of(application));
        when(integrationRepository.existsByPublicKey(publicKey)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> service.approve("application-id", 42L));
        verify(integrationRepository, never()).save(any(PosIntegration.class));
    }

    @Test
    void statusLookupRequiresMatchingHashedAccessToken() throws Exception {
        String token = "status-token";
        String tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.UTF_8)));
        PosPartnerApplication application = new PosPartnerApplication(
            "application-id", tokenHash, "Demo POS", "Contact", "contact@example.com", "+10000000000",
            "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", publicKey
        );
        when(applicationRepository.findByApplicationIdAndStatusTokenHash("application-id", tokenHash))
            .thenReturn(Optional.of(application));

        assertEquals("PENDING", service.getStatus("application-id", token).status());
        verify(applicationRepository).findByApplicationIdAndStatusTokenHash(eq("application-id"), eq(tokenHash));
    }

    private PosPartnerApplicationRequest signedRequest(String partnerName, String encodedPublicKey, KeyPair pair)
        throws Exception {
        PosPartnerApplicationRequest unsigned = new PosPartnerApplicationRequest(
            partnerName, " Contact ", "CONTACT@example.com", "+10000000000",
            "retailer-1", "Demo Market", "outlet-1", "Demo Outlet", encodedPublicKey, "pending"
        );
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(PosPartnerApplicationService.canonicalApplication(unsigned).getBytes(StandardCharsets.UTF_8));
        return new PosPartnerApplicationRequest(
            unsigned.partnerName(), unsigned.contactName(), unsigned.contactEmail(), unsigned.contactPhone(),
            unsigned.retailerReference(), unsigned.retailerName(), unsigned.outletReference(), unsigned.outletName(),
            unsigned.publicKey(), Base64.getEncoder().encodeToString(signer.sign())
        );
    }
}
