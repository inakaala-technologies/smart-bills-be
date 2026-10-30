package com.bhive.business.controller;

import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.entity.CallbackRequest;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.business.repository.CallbackRequestRepository;
import com.bhive.business.service.BusinessMembershipService;
import com.bhive.common.util.TenantContext;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/callback-requests")
public class BusinessCallbackController {

    private static final int MAX_REASON_LENGTH = 300;

    public record CallbackRequestInput(String reason) {}

    public record CallbackStatusInput(String status) {}

    public record CallbackRequestView(Long id, Long businessId, Long customerProfileId, String requesterName,
                                      String requesterEmail, String requesterPhone, String reason, String status,
                                      LocalDateTime createdAt) {
        static CallbackRequestView from(CallbackRequest request) {
            return new CallbackRequestView(request.getId(), request.getBusinessId(), request.getCustomerProfileId(),
                request.getRequesterName(), request.getRequesterEmail(), request.getRequesterPhone(),
                request.getReason(), request.getStatus(), request.getCreatedAt());
        }
    }

    private final CallbackRequestRepository callbackRequestRepository;
    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessMembershipService businessMembershipService;
    private final NotificationService notificationService;

    public BusinessCallbackController(CallbackRequestRepository callbackRequestRepository,
                                      BusinessRepository businessRepository,
                                      BusinessUserRepository businessUserRepository,
                                      CustomerProfileRepository customerProfileRepository,
                                      BusinessMembershipService businessMembershipService,
                                      NotificationService notificationService) {
        this.callbackRequestRepository = callbackRequestRepository;
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessMembershipService = businessMembershipService;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CallbackRequestView requestCallback(@PathVariable Long businessId,
                                               @RequestBody(required = false) CallbackRequestInput input) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated.");
        }
        Business business = businessRepository.findById(businessId)
            .filter(found -> tenantId.equals(found.getTenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found."));
        CustomerProfile customer = customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A customer profile is required to request a callback."));

        String reason = input == null || input.reason() == null ? "" : input.reason().trim();
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Reason must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }
        if (callbackRequestRepository.existsByTenantIdAndBusinessIdAndRequesterUserIdAndStatus(
            tenantId, businessId, userId, CallbackRequest.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You already have a pending callback request with this business.");
        }

        CallbackRequest request = new CallbackRequest();
        request.setTenantId(tenantId);
        request.setBusinessId(businessId);
        request.setCustomerProfileId(customer.getId());
        request.setRequesterUserId(userId);
        request.setRequesterName(customer.getName() == null || customer.getName().isBlank()
            ? "A customer" : customer.getName().trim());
        request.setRequesterEmail(customer.getEmail());
        request.setRequesterPhone(customer.getPhone());
        request.setReason(reason.isEmpty() ? null : reason);
        CallbackRequest saved = callbackRequestRepository.save(request);

        String contact = saved.getRequesterPhone() != null && !saved.getRequesterPhone().isBlank()
            ? saved.getRequesterPhone() : saved.getRequesterEmail();
        String message = saved.getRequesterName() + (contact == null ? "" : " (" + contact + ")")
            + " requested a callback" + (saved.getReason() == null ? "." : ": " + saved.getReason());
        businessUserRepository.findByTenantIdAndBusinessIdAndStatusIgnoreCase(tenantId, businessId, "ACTIVE")
            .stream()
            .map(BusinessUser::getUserId)
            .filter(recipientUserId -> recipientUserId != null && !recipientUserId.equals(userId))
            .distinct()
            .forEach(recipientUserId -> notificationService.create(tenantId, recipientUserId, "CALLBACK_REQUESTED",
                "Callback requested for " + business.getName(), truncate(message, 1000),
                "/business/dashboard/callbacks"));
        return CallbackRequestView.from(saved);
    }

    @GetMapping
    public List<CallbackRequestView> listCallbacks(@PathVariable Long businessId) {
        requireManager(businessId);
        return callbackRequestRepository
            .findTop200ByTenantIdAndBusinessIdOrderByCreatedAtDesc(TenantContext.getTenantId(), businessId)
            .stream().map(CallbackRequestView::from).toList();
    }

    @PatchMapping("/{requestId}/status")
    public CallbackRequestView updateStatus(@PathVariable Long businessId, @PathVariable Long requestId,
                                            @RequestBody CallbackStatusInput input) {
        requireManager(businessId);
        String status = input == null || input.status() == null ? "" : input.status().trim().toUpperCase();
        if (!CallbackRequest.PENDING.equals(status) && !CallbackRequest.CONTACTED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be PENDING or CONTACTED.");
        }
        CallbackRequest request = callbackRequestRepository
            .findByIdAndTenantIdAndBusinessId(requestId, TenantContext.getTenantId(), businessId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Callback request not found."));
        request.setStatus(status);
        return CallbackRequestView.from(callbackRequestRepository.save(request));
    }

    private void requireManager(Long businessId) {
        if (!businessMembershipService.userHasAccessToBusiness(
            TenantContext.getUserId(), businessId, TenantContext.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage this business.");
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
