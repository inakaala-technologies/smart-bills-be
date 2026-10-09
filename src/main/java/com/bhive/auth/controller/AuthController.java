package com.bhive.auth.controller;

import com.bhive.auth.dto.AuthRequest;
import jakarta.validation.Valid;
import com.bhive.auth.dto.AuthResponse;
import com.bhive.auth.dto.ProfileUpdateRequest;
import com.bhive.auth.dto.RegisterRequest;
import com.bhive.auth.entity.User;
import com.bhive.auth.entity.UserAccount;
import com.bhive.auth.entity.UserRole;
import com.bhive.auth.repository.UserAccountRepository;
import com.bhive.auth.repository.UserRepository;
import com.bhive.auth.service.RefreshTokenService;
import com.bhive.business.entity.Business;
import com.bhive.business.entity.BusinessStatus;
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.common.config.AccessTokenService;
import com.bhive.common.util.GeoUtils;
import com.bhive.common.util.ProfileIdGenerator;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final BusinessRepository businessRepository;
    private final BusinessUserRepository businessUserRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final BusinessCustomerRepository businessCustomerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.security.refresh-cookie.secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.security.refresh-cookie.same-site:Lax}")
    private String refreshCookieSameSite = "Lax";

    public AuthController(UserRepository userRepository,
                          UserAccountRepository userAccountRepository,
                          BusinessRepository businessRepository,
                          BusinessUserRepository businessUserRepository,
                          CustomerProfileRepository customerProfileRepository,
                          BusinessCustomerRepository businessCustomerRepository,
                          PasswordEncoder passwordEncoder,
                          AccessTokenService accessTokenService,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userAccountRepository = userAccountRepository;
        this.businessRepository = businessRepository;
        this.businessUserRepository = businessUserRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.businessCustomerRepository = businessCustomerRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Name is required."));
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Email is required."));
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Password must be at least 6 characters long."));
        }

        if (request.getRole() == null) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Role is required."));
        }
        if (UserRole.ADMIN.equals(request.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new AuthResponse(null, null, null, null, null, null, "Administrator accounts cannot be created through public registration."));
        }

        if (request.getCountry() == null || request.getCountry().isBlank() || request.getCountry().length() > 80
            || request.getRegion() == null || request.getRegion().isBlank() || request.getRegion().length() > 80
            || request.getCity() == null || request.getCity().isBlank() || request.getCity().length() > 80
            || !GeoUtils.isValidOptionalCoordinates(request.getLatitude(), request.getLongitude())) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Country, state or region, and city or town are required."));
        }
        String locationAddress = String.join(", ", request.getCity().trim(), request.getRegion().trim(), request.getCountry().trim());

        Optional<User> existing = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (existing.isPresent()) {
            User existingUser = existing.get();
            if (!passwordMatches(existingUser, request.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, null, null, null, "Invalid email or password."));
            }
            boolean roleExists = userAccountRepository.findByUserId(existingUser.getId()).stream()
                .anyMatch(account -> account.getRole() != null && account.getRole().equals(request.getRole()));

            if (roleExists) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse(null, null, null, null, null, null, "This account already exists for this login."));
            }

            existingUser.setRole(request.getRole());
            existingUser.setTenantId(resolveTenantIdForRole(request.getRole(), existingUser.getId(), existingUser.getTenantId()));
            userRepository.save(existingUser);

            UserAccount newAccount = new UserAccount();
            newAccount.setUserId(existingUser.getId());
            newAccount.setRole(request.getRole());
            newAccount.setTenantId(existingUser.getTenantId());
            newAccount.setStatus("ACTIVE");
            userAccountRepository.save(newAccount);
            ensureBusinessProfile(existingUser, request.getRole(), existingUser.getTenantId(), request.getPhone(), locationAddress, request.getLatitude(), request.getLongitude());
            ensureCustomerProfile(existingUser, request.getRole(), existingUser.getTenantId(), request.getPhone(), locationAddress, request.getLatitude(), request.getLongitude());

            SessionContext session = resolveSessionContext(existingUser);
            return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED)
                .body(buildAuthResponse(existingUser, session, "Account linked to your existing login.")),
                refreshTokenService.issueRefreshToken(existingUser.getId()));
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setTenantId(resolveTenantIdForRole(request.getRole(), null, null));

        User saved = userRepository.save(user);
        ensureUserAccount(saved, saved.getRole(), null, null);
        ensureBusinessProfile(saved, saved.getRole(), saved.getTenantId(), request.getPhone(), locationAddress, request.getLatitude(), request.getLongitude());
        ensureCustomerProfile(saved, saved.getRole(), saved.getTenantId(), request.getPhone(), locationAddress, request.getLatitude(), request.getLongitude());
        SessionContext session = resolveSessionContext(saved);
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED)
            .body(buildAuthResponse(saved, session, "Registration successful.")),
            refreshTokenService.issueRefreshToken(saved.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUserProfile() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, null, null, null, null, null, "User is not authenticated.", List.of()));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthResponse(null, null, null, null, null, null, null, "User profile not found.", List.of()));
        }

        User user = userOpt.get();
        SessionContext session = resolveSessionContext(user);
        return ResponseEntity.ok(buildAuthResponse(user, session, "Profile fetched successfully."));
    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity<AuthResponse> updateCurrentUserProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, null, null, null, null, null, "User is not authenticated.", List.of()));
        }

        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest()
                .body(new AuthResponse(null, null, null, null, null, null, "Name is required."));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthResponse(null, null, null, null, null, null, null, "User profile not found.", List.of()));
        }

        User user = userOpt.get();
        if (UserRole.CUSTOMER.equals(user.getRole())
            && (request.getCountry() == null || request.getCountry().isBlank() || request.getCountry().length() > 80
                || request.getRegion() == null || request.getRegion().isBlank() || request.getRegion().length() > 80
                || request.getCity() == null || request.getCity().isBlank() || request.getCity().length() > 80
                || !GeoUtils.isValidOptionalCoordinates(request.getLatitude(), request.getLongitude()))) {
            return ResponseEntity.badRequest()
                .body(new AuthResponse(null, null, null, null, null, null, "Country, state or region, and city or town are required."));
        }

        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : user.getTenantId();
        Optional<CustomerProfile> customerProfile = UserRole.CUSTOMER.equals(user.getRole())
            ? customerProfileRepository.findByUserIdAndTenantId(userId, tenantId)
            : Optional.empty();
        if (UserRole.CUSTOMER.equals(user.getRole()) && customerProfile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AuthResponse(null, null, null, null, null, null, null, "Customer profile not found.", List.of()));
        }

        user.setName(request.getName().trim());
        User savedUser = userRepository.save(user);
        customerProfile.ifPresent(profile -> {
            profile.setName(savedUser.getName());
            profile.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
            profile.setLocationAddress(String.join(", ", request.getCity().trim(), request.getRegion().trim(), request.getCountry().trim()));
            profile.setLatitude(request.getLatitude());
            profile.setLongitude(request.getLongitude());
            customerProfileRepository.save(profile);
        });

        SessionContext session = resolveSessionContext(savedUser);
        return ResponseEntity.ok(buildAuthResponse(savedUser, session, "Profile updated successfully."));
    }

    @GetMapping("/profile")
    public ResponseEntity<AuthResponse> getUserProfile() {
        return getCurrentUserProfile();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Email is required."));
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null, null, null, null, null, "Password is required."));
        }

        Optional<User> userOpt = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (userOpt.isEmpty() || !passwordMatches(userOpt.get(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(null, null, null, null, null, null, "Invalid email or password."));
        }

        User user = userOpt.get();
        SessionContext session = resolveSessionContext(user);
        List<UserAccount> linkedAccounts = userAccountRepository.findByUserId(user.getId());

        boolean hasBusinessMembership = businessUserRepository.findByUserId(user.getId()).stream().findAny().isPresent();
        if (UserRole.BUSINESS.equals(user.getRole()) && session.businessId == null && linkedAccounts.isEmpty() && !hasBusinessMembership) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildAuthResponse(user, session, "Business profile not found for this user."));
        }

        return withRefreshCookie(ResponseEntity.ok(buildAuthResponse(user, session, "Login successful.")),
            refreshTokenService.issueRefreshToken(user.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshAccessToken(
        @CookieValue(name = "BHIVE_REFRESH_TOKEN", required = false) String rawRefreshToken
    ) {
        Optional<RefreshTokenService.RotatedRefreshToken> rotated = refreshTokenService.rotate(rawRefreshToken);
        if (rotated.isEmpty()) {
            return unauthorizedRefresh();
        }

        Optional<User> user = userRepository.findById(rotated.get().userId());
        if (user.isEmpty()) {
            refreshTokenService.revoke(rotated.get().rawToken());
            return unauthorizedRefresh();
        }

        SessionContext session = resolveSessionContext(user.get());
        return withRefreshCookie(ResponseEntity.ok(buildAuthResponse(user.get(), session, "Access token refreshed.")),
            rotated.get().rawToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = "BHIVE_REFRESH_TOKEN", required = false) String rawRefreshToken
    ) {
        refreshTokenService.revoke(rawRefreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expiredRefreshCookie()).build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(ResponseEntity<AuthResponse> response, String rawRefreshToken) {
        return ResponseEntity.status(response.getStatusCode())
            .headers(response.getHeaders())
            .header(HttpHeaders.SET_COOKIE, refreshCookie(rawRefreshToken))
            .body(response.getBody());
    }

    private ResponseEntity<AuthResponse> unauthorizedRefresh() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie())
            .body(new AuthResponse(null, null, null, null, null, null, "Refresh session is invalid or expired."));
    }

    private String refreshCookie(String value) {
        return ResponseCookie.from("BHIVE_REFRESH_TOKEN", value)
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite(refreshCookieSameSite)
            .path("/api/v1/auth")
            .maxAge(Duration.ofDays(30))
            .build()
            .toString();
    }

    private String expiredRefreshCookie() {
        return ResponseCookie.from("BHIVE_REFRESH_TOKEN", "")
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite(refreshCookieSameSite)
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build()
            .toString();
    }

    private SessionContext resolveSessionContext(User user) {
        Long tenantId = resolveTenantIdForRole(user.getRole(), user.getId(), user.getTenantId());
        Long businessId = null;

        List<UserAccount> linkedAccounts = userAccountRepository.findByUserId(user.getId());
        Optional<UserAccount> activeAccount = linkedAccounts.stream()
            .filter(account -> account.getRole() != null && account.getRole().equals(user.getRole()))
            .findFirst();
        if (activeAccount.isPresent()) {
            if (activeAccount.get().getTenantId() != null) {
                tenantId = activeAccount.get().getTenantId();
            }
            if (UserRole.BUSINESS.equals(user.getRole()) && activeAccount.get().getBusinessId() != null) {
                businessId = activeAccount.get().getBusinessId();
            }
        }

        if (UserRole.BUSINESS.equals(user.getRole()) && businessId == null) {
            final Long resolvedTenantId = tenantId;
            Optional<BusinessUser> membership = businessUserRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getTenantId() != null && m.getTenantId().equals(resolvedTenantId))
                .findFirst();
            if (membership.isPresent()) {
                businessId = membership.get().getBusinessId();
                tenantId = membership.get().getTenantId();
            }
        } else if (UserRole.CUSTOMER.equals(user.getRole())) {
            final Long resolvedTenantId = tenantId;
            final Long currentUserId = user.getId();
            Optional<CustomerProfile> customerProfile = customerProfileRepository.findAll().stream()
                .filter(customer -> customer.getUserId() != null && customer.getUserId().equals(currentUserId))
                .filter(customer -> customer.getTenantId() != null && customer.getTenantId().equals(resolvedTenantId))
                .findFirst();
            if (customerProfile.isPresent()) {
                tenantId = customerProfile.get().getTenantId();
            }
        }

        final Long sessionTenantId = tenantId;
        final Long sessionBusinessId = businessId;
        final UserRole currentRole = user.getRole();

        linkedAccounts.stream()
            .filter(account -> account.getRole() != null && account.getRole().equals(currentRole))
            .findFirst()
            .ifPresentOrElse(account -> {
                account.setTenantId(sessionTenantId);
                if (sessionBusinessId != null) {
                    account.setBusinessId(sessionBusinessId);
                }
                if (account.getStatus() == null || account.getStatus().isBlank()) {
                    account.setStatus("ACTIVE");
                }
                userAccountRepository.save(account);
            }, () -> ensureUserAccount(user, currentRole, sessionTenantId, sessionBusinessId));

        if (user.getTenantId() == null || !tenantId.equals(user.getTenantId())) {
            user.setTenantId(tenantId);
            userRepository.save(user);
        }

        return new SessionContext(tenantId, businessId);
    }

    private boolean passwordMatches(User user, String rawPassword) {
        String storedPassword = user.getPassword();
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")
            || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        if (MessageDigest.isEqual(storedPassword.getBytes(StandardCharsets.UTF_8), rawPassword.getBytes(StandardCharsets.UTF_8))) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private Long resolveTenantIdForRole(UserRole role, Long userId, Long currentTenantId) {
        if (currentTenantId != null && currentTenantId > 0) {
            return currentTenantId;
        }

        if (UserRole.BUSINESS.equals(role) && userId != null) {
            return businessUserRepository.findByUserId(userId).stream()
                .map(BusinessUser::getTenantId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(1L);
        }

        if (UserRole.CUSTOMER.equals(role) && userId != null) {
            return customerProfileRepository.findAll().stream()
                .filter(customer -> customer.getUserId() != null && customer.getUserId().equals(userId))
                .map(CustomerProfile::getTenantId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(1L);
        }

        return 1L;
    }

    private void ensureUserAccount(User user, UserRole role, Long tenantId, Long businessId) {
        boolean exists = userAccountRepository.findByUserId(user.getId()).stream()
            .anyMatch(account -> account.getRole() != null && account.getRole().equals(role));

        if (exists) {
            return;
        }

        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setRole(role);
        account.setTenantId(tenantId != null ? tenantId : user.getTenantId());
        account.setBusinessId(businessId);
        account.setStatus("ACTIVE");
        userAccountRepository.save(account);
    }

    private void ensureBusinessProfile(User user, UserRole role, Long tenantId, String phone, String locationAddress, Double latitude, Double longitude) {
        if (!UserRole.BUSINESS.equals(role) || user == null || user.getId() == null) {
            return;
        }

        Long resolvedTenantId = tenantId != null ? tenantId : user.getTenantId();
        if (resolvedTenantId == null) {
            return;
        }

        Optional<Business> existingBusiness = businessUserRepository.findByUserIdAndTenantId(user.getId(), resolvedTenantId).stream()
            .map(BusinessUser::getBusinessId)
            .filter(Objects::nonNull)
            .map(businessRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

        if (existingBusiness.isPresent()) {
            Business business = existingBusiness.get();
            if (user.getName() != null && !user.getName().isBlank()) {
                business.setName(user.getName().trim());
            }
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                business.setEmail(user.getEmail().trim().toLowerCase());
            }
            if (phone != null && !phone.isBlank()) {
                business.setPhone(phone.trim());
            }
            if (locationAddress != null && !locationAddress.isBlank()) business.setAddress(locationAddress.trim());
            if (latitude != null && longitude != null) {
                business.setLatitude(latitude);
                business.setLongitude(longitude);
            }
            if (business.getProfileId() == null || business.getProfileId().isBlank()) {
                business.setProfileId(ProfileIdGenerator.nextBusinessId(businessRepository::existsByProfileId));
            }
            if (business.getTenantId() == null) {
                business.setTenantId(resolvedTenantId);
            }
            businessRepository.save(business);
            return;
        }

        Business business = new Business();
        business.setTenantId(resolvedTenantId);
        business.setName(user.getName() == null || user.getName().isBlank() ? "Business" : user.getName().trim());
        business.setEmail(user.getEmail() == null ? null : user.getEmail().trim().toLowerCase());
        business.setPhone(phone == null ? null : phone.trim());
        business.setAddress(locationAddress == null ? null : locationAddress.trim());
        business.setLatitude(latitude);
        business.setLongitude(longitude);
        business.setProfileId(ProfileIdGenerator.nextBusinessId(businessRepository::existsByProfileId));
        business.setStatus(BusinessStatus.ACTIVE);

        Business savedBusiness = businessRepository.save(business);
        businessUserRepository.findByUserIdAndTenantId(user.getId(), resolvedTenantId).stream()
            .findFirst()
            .ifPresentOrElse(
                membership -> {
                    membership.setBusinessId(savedBusiness.getId());
                    businessUserRepository.save(membership);
                },
                () -> {
                    BusinessUser membership = new BusinessUser();
                    membership.setBusinessId(savedBusiness.getId());
                    membership.setUserId(user.getId());
                    membership.setTenantId(resolvedTenantId);
                    membership.setRole(com.bhive.business.entity.BusinessUserRole.BUSINESS_ADMIN);
                    membership.setStatus("ACTIVE");
                    businessUserRepository.save(membership);
                }
            );
    }

    private void ensureCustomerProfile(User user, UserRole role, Long tenantId) {
        ensureCustomerProfile(user, role, tenantId, null, null, null, null);
    }

    private void ensureCustomerProfile(User user, UserRole role, Long tenantId, String phone, String locationAddress, Double latitude, Double longitude) {
        if (!UserRole.CUSTOMER.equals(role) || user == null || user.getId() == null) {
            return;
        }

        Long resolvedTenantId = tenantId != null ? tenantId : user.getTenantId();
        if (resolvedTenantId == null) {
            return;
        }

        boolean alreadyExists = customerProfileRepository.findByUserIdAndTenantId(user.getId(), resolvedTenantId).isPresent();
        if (alreadyExists) {
            CustomerProfile existing = customerProfileRepository.findByUserIdAndTenantId(user.getId(), resolvedTenantId).orElse(null);
            boolean changed = false;
            if (existing != null && (existing.getPhone() == null || existing.getPhone().isBlank()) && phone != null && !phone.isBlank()) {
                existing.setPhone(phone.trim());
                changed = true;
            }
            if (existing != null && (existing.getProfileId() == null || existing.getProfileId().isBlank())) {
                existing.setProfileId(ProfileIdGenerator.nextCustomerId(customerProfileRepository::existsByProfileId));
                changed = true;
            }
            if (existing != null && locationAddress != null && !locationAddress.isBlank()) {
                existing.setLocationAddress(locationAddress.trim());
                changed = true;
            }
            if (existing != null && latitude != null && longitude != null) {
                existing.setLatitude(latitude);
                existing.setLongitude(longitude);
                changed = true;
            }
            if (existing != null && changed) {
                customerProfileRepository.save(existing);
            }
            return;
        }

        CustomerProfile claimable = user.getEmail() == null ? null
            : customerProfileRepository.findFirstByEmailIgnoreCaseAndTenantIdAndUserIdIsNull(user.getEmail(), resolvedTenantId).orElse(null);
        if (claimable == null && phone != null && !phone.isBlank()) {
            claimable = customerProfileRepository.findFirstByPhoneAndTenantIdAndUserIdIsNull(phone.trim(), resolvedTenantId).orElse(null);
        }
        if (claimable != null) {
            claimable.setUserId(user.getId());
            claimable.setName(user.getName() == null || user.getName().isBlank() ? claimable.getName() : user.getName().trim());
            if (claimable.getEmail() == null || claimable.getEmail().isBlank()) claimable.setEmail(user.getEmail());
            if ((claimable.getPhone() == null || claimable.getPhone().isBlank()) && phone != null) claimable.setPhone(phone.trim());
            customerProfileRepository.save(claimable);
            return;
        }

        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(user.getId());
        profile.setTenantId(resolvedTenantId);
        profile.setName(user.getName() == null || user.getName().isBlank() ? "Customer" : user.getName().trim());
        profile.setEmail(user.getEmail());
        profile.setPhone(phone == null ? null : phone.trim());
        profile.setLocationAddress(locationAddress == null ? null : locationAddress.trim());
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
        profile.setProfileId(ProfileIdGenerator.nextCustomerId(customerProfileRepository::existsByProfileId));
        customerProfileRepository.save(profile);
    }

    private AuthResponse buildAuthResponse(User user, SessionContext session, String message) {
        List<AuthResponse.UserAccountSummary> accounts = userAccountRepository.findByUserId(user.getId()).stream()
            .map(account -> {
                AuthResponse.UserAccountSummary summary = new AuthResponse.UserAccountSummary();
                summary.setRole(account.getRole());
                summary.setTenantId(account.getTenantId());
                summary.setBusinessId(account.getBusinessId());
                summary.setStatus(account.getStatus());
                return summary;
            })
            .toList();

        UserRole activeRole = user.getRole() == null ? UserRole.BUSINESS : user.getRole();
        String phone = resolvePhoneForRole(user, session.tenantId, session.businessId);

        AuthResponse response = new AuthResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            activeRole,
            activeRole,
            session.tenantId,
            session.businessId,
            message,
            accounts
        );
        response.setPhone(phone);
        LinkedHashSet<Long> allowedTenants = new LinkedHashSet<>();
        if (session.tenantId != null) {
            allowedTenants.add(session.tenantId);
        }
        userAccountRepository.findByUserId(user.getId()).stream()
            .filter(account -> account.getTenantId() != null && account.getTenantId() > 0)
            .filter(account -> account.getStatus() == null || "ACTIVE".equalsIgnoreCase(account.getStatus()))
            .map(UserAccount::getTenantId)
            .forEach(allowedTenants::add);
        response.setAccessToken(accessTokenService.createAccessToken(user.getId(), allowedTenants));
        return response;
    }

    private String resolvePhoneForRole(User user, Long tenantId, Long businessId) {
        if (user == null) {
            return null;
        }

        if (UserRole.CUSTOMER.equals(user.getRole())) {
            final Long resolvedTenantId = tenantId != null ? tenantId : user.getTenantId();
            return customerProfileRepository.findAll().stream()
                .filter(profile -> profile.getUserId() != null && profile.getUserId().equals(user.getId()))
                .filter(profile -> resolvedTenantId == null || profile.getTenantId() == null || profile.getTenantId().equals(resolvedTenantId))
                .map(CustomerProfile::getPhone)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        if (UserRole.BUSINESS.equals(user.getRole())) {
            final Long resolvedBusinessId = businessId != null ? businessId : businessUserRepository.findByUserId(user.getId()).stream()
                .filter(account -> tenantId == null || account.getTenantId() == null || account.getTenantId().equals(tenantId))
                .map(BusinessUser::getBusinessId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

            if (resolvedBusinessId == null) {
                return null;
            }

            return businessRepository.findById(resolvedBusinessId)
                .map(Business::getPhone)
                .orElse(null);
        }

        return null;
    }

    private static class SessionContext {
        private final Long tenantId;
        private final Long businessId;

        private SessionContext(Long tenantId, Long businessId) {
            this.tenantId = tenantId;
            this.businessId = businessId;
        }
    }

}
