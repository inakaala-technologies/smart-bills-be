package com.bhive.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.bhive.auth.dto.AuthRequest;
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
import com.bhive.business.entity.BusinessUser;
import com.bhive.business.repository.BusinessRepository;
import com.bhive.business.repository.BusinessUserRepository;
import com.bhive.customer.entity.CustomerProfile;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.customer.repository.CustomerProfileRepository;
import com.bhive.common.util.TenantContext;
import com.bhive.common.config.AccessTokenService;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessUserRepository businessUserRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private BusinessCustomerRepository businessCustomerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void configurePasswordEncoder() {
        lenient().when(passwordEncoder.encode(any())).thenReturn("$2a$test-encoded-password");
        lenient().when(accessTokenService.createAccessToken(any(), any(Collection.class))).thenReturn("test-access-token");
        lenient().when(refreshTokenService.issueRefreshToken(any())).thenReturn("test-refresh-token");
    }

    @Test
    void shouldRequireCountryRegionAndCityBeforeRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setName("New Customer");
        request.setEmail("newcustomer@bhive.com");
        request.setPassword("password123");
        request.setRole(UserRole.CUSTOMER);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldRejectPublicAdministratorRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Admin");
        request.setEmail("admin@example.com");
        request.setPassword("long-enough-password");
        request.setRole(UserRole.ADMIN);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void shouldRotateRefreshCookieAndReturnANewAccessToken() {
        User user = new User();
        user.setId(88L);
        user.setName("Customer User");
        user.setEmail("customer@bhive.com");
        user.setPassword("password123");
        user.setRole(UserRole.CUSTOMER);
        user.setTenantId(9L);

        when(refreshTokenService.rotate("old-refresh-token"))
            .thenReturn(Optional.of(new RefreshTokenService.RotatedRefreshToken(88L, "next-refresh-token")));
        when(userRepository.findById(88L)).thenReturn(Optional.of(user));
        when(customerProfileRepository.findAll()).thenReturn(List.of());
        when(userAccountRepository.findByUserId(88L)).thenReturn(List.of());

        ResponseEntity<AuthResponse> response = authController.refreshAccessToken("old-refresh-token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("test-access-token", response.getBody().getAccessToken());
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("next-refresh-token"));
        verify(refreshTokenService).rotate("old-refresh-token");
    }

    @Test
    void shouldCreateCustomerProfileWhenRegisteringCustomerRole() {
        RegisterRequest request = new RegisterRequest();
        request.setName("New Customer");
        request.setEmail("newcustomer@bhive.com");
        request.setPassword("password123");
        request.setPhone("9876543210");
        request.setCountry("United States");
        request.setRegion("California");
        request.setCity("San Francisco");
        request.setLocationAddress("Ignored free text");
        request.setLatitude(37.7749);
        request.setLongitude(-122.4194);
        request.setRole(UserRole.CUSTOMER);

        User savedUser = new User();
        savedUser.setId(150L);
        savedUser.setName("New Customer");
        savedUser.setEmail("newcustomer@bhive.com");
        savedUser.setPassword("password123");
        savedUser.setRole(UserRole.CUSTOMER);
        savedUser.setTenantId(1L);

        when(userRepository.findByEmail("newcustomer@bhive.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userAccountRepository.findByUserId(150L)).thenReturn(List.of());
        when(customerProfileRepository.findByUserIdAndTenantId(150L, 1L)).thenReturn(Optional.empty());

        ResponseEntity<AuthResponse> response = authController.register(request);

        ArgumentCaptor<CustomerProfile> profileCaptor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customerProfileRepository).save(profileCaptor.capture());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("HttpOnly"));
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("SameSite=Lax"));
        verify(userRepository).save(any(User.class));
        assertEquals("9876543210", profileCaptor.getValue().getPhone());
        assertEquals("San Francisco, California, United States", profileCaptor.getValue().getLocationAddress());
        assertEquals(37.7749, profileCaptor.getValue().getLatitude());
        org.junit.jupiter.api.Assertions.assertTrue(profileCaptor.getValue().getProfileId().matches("CUS[A-Z]{5}"));
    }

    @Test
    void shouldUpdateCustomerNameAndPhoneWithoutChangingEmail() {
        User user = new User();
        user.setId(150L);
        user.setName("Old Name");
        user.setEmail("customer@bhive.com");
        user.setPassword("password123");
        user.setRole(UserRole.CUSTOMER);
        user.setTenantId(1L);

        UserAccount account = new UserAccount();
        account.setRole(UserRole.CUSTOMER);
        account.setTenantId(1L);

        CustomerProfile profile = new CustomerProfile();
        profile.setUserId(150L);
        profile.setTenantId(1L);
        profile.setName("Old Name");
        profile.setPhone("1111111111");

        when(userRepository.findById(150L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountRepository.findByUserId(150L)).thenReturn(List.of(account));
        when(customerProfileRepository.findByUserIdAndTenantId(150L, 1L)).thenReturn(Optional.of(profile));
        when(customerProfileRepository.findAll()).thenReturn(List.of(profile));

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("Updated Name");
        request.setPhone("2222222222");
        request.setCountry("India");
        request.setRegion("Andhra Pradesh");
        request.setCity("Vijayawada");
        request.setLatitude(16.5062);
        request.setLongitude(80.6480);

        TenantContext.setUserId(150L);
        TenantContext.setTenantId(1L);
        try {
            ResponseEntity<AuthResponse> response = authController.updateCurrentUserProfile(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Updated Name", response.getBody().getName());
            assertEquals("customer@bhive.com", response.getBody().getEmail());
            assertEquals("2222222222", response.getBody().getPhone());
            assertEquals("Updated Name", profile.getName());
            assertEquals("2222222222", profile.getPhone());
            assertEquals("Vijayawada, Andhra Pradesh, India", profile.getLocationAddress());
            assertEquals(16.5062, profile.getLatitude());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void shouldCreateBusinessProfileWithPhoneWhenRegisteringBusinessRole() {
        RegisterRequest request = new RegisterRequest();
        request.setName("New Business");
        request.setEmail("newbusiness@bhive.com");
        request.setPassword("password123");
        request.setPhone("9123456789");
        request.setCountry("United States");
        request.setRegion("California");
        request.setCity("San Francisco");
        request.setLocationAddress("Ignored free text");
        request.setLatitude(37.7749);
        request.setLongitude(-122.4194);
        request.setRole(UserRole.BUSINESS);

        User savedUser = new User();
        savedUser.setId(160L);
        savedUser.setName("New Business");
        savedUser.setEmail("newbusiness@bhive.com");
        savedUser.setPassword("password123");
        savedUser.setRole(UserRole.BUSINESS);
        savedUser.setTenantId(1L);

        when(userRepository.findByEmail("newbusiness@bhive.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(businessUserRepository.findByUserIdAndTenantId(160L, 1L)).thenReturn(List.of());
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(200L);
            return business;
        });

        ResponseEntity<AuthResponse> response = authController.register(request);

        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(businessCaptor.capture());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("9123456789", businessCaptor.getValue().getPhone());
        assertEquals("San Francisco, California, United States", businessCaptor.getValue().getAddress());
        assertEquals(-122.4194, businessCaptor.getValue().getLongitude());
        org.junit.jupiter.api.Assertions.assertTrue(businessCaptor.getValue().getProfileId().matches("BIZ[A-Z]{5}"));
    }

    @Test
    void shouldResolveBusinessTenantAndMembershipOnLogin() {
        User user = new User();
        user.setId(99L);
        user.setName("Business User");
        user.setEmail("owner@firm.com");
        user.setPassword("password123");
        user.setRole(UserRole.BUSINESS);

        BusinessUser membership = new BusinessUser();
        membership.setId(10L);
        membership.setBusinessId(33L);
        membership.setUserId(99L);
        membership.setTenantId(7L);
        membership.setStatus("ACTIVE");

        when(userRepository.findByEmail("owner@firm.com")).thenReturn(Optional.of(user));
        when(businessUserRepository.findByUserId(99L)).thenReturn(List.of(membership));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthRequest request = new AuthRequest();
        request.setEmail("owner@firm.com");
        request.setPassword("password123");

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(7L, response.getBody().getTenantId());
        assertEquals(33L, response.getBody().getBusinessId());
    }

    @Test
    void shouldRejectBusinessLoginWithoutMembership() {
        User user = new User();
        user.setId(42L);
        user.setName("No Business");
        user.setEmail("nobusiness@firm.com");
        user.setPassword("password123");
        user.setRole(UserRole.BUSINESS);

        when(userRepository.findByEmail("nobusiness@firm.com")).thenReturn(Optional.of(user));
        when(businessUserRepository.findByUserId(42L)).thenReturn(List.of());

        AuthRequest request = new AuthRequest();
        request.setEmail("nobusiness@firm.com");
        request.setPassword("password123");

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Business profile not found for this user.", response.getBody().getMessage());
    }

    @Test
    void shouldResolveCustomerTenantOnLogin() {
        User user = new User();
        user.setId(88L);
        user.setName("Customer User");
        user.setEmail("customer@bhive.com");
        user.setPassword("password123");
        user.setRole(UserRole.CUSTOMER);

        CustomerProfile profile = new CustomerProfile();
        profile.setId(1L);
        profile.setUserId(88L);
        profile.setTenantId(9L);
        profile.setName("Customer User");
        profile.setPhone("9876543210");

        when(userRepository.findByEmail("customer@bhive.com")).thenReturn(Optional.of(user));
        when(customerProfileRepository.findAll()).thenReturn(List.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthRequest request = new AuthRequest();
        request.setEmail("customer@bhive.com");
        request.setPassword("password123");

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(9L, response.getBody().getTenantId());
        assertEquals("9876543210", response.getBody().getPhone());
    }

    @Test
    void shouldKeepLinkedBusinessAndCustomerAccountsUnderSameLogin() {
        User user = new User();
        user.setId(77L);
        user.setName("Dual Persona User");
        user.setEmail("dual@bhive.com");
        user.setPassword("password123");
        user.setRole(UserRole.BUSINESS);

        UserAccount businessAccount = new UserAccount();
        businessAccount.setUserId(77L);
        businessAccount.setRole(UserRole.BUSINESS);
        businessAccount.setTenantId(14L);
        businessAccount.setBusinessId(44L);

        UserAccount customerAccount = new UserAccount();
        customerAccount.setUserId(77L);
        customerAccount.setRole(UserRole.CUSTOMER);
        customerAccount.setTenantId(15L);

        when(userRepository.findByEmail("dual@bhive.com")).thenReturn(Optional.of(user));
        when(userAccountRepository.findByUserId(77L)).thenReturn(List.of(businessAccount, customerAccount));
        when(businessUserRepository.findByUserId(77L)).thenReturn(List.of());

        AuthRequest request = new AuthRequest();
        request.setEmail("dual@bhive.com");
        request.setPassword("password123");

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getAccounts().size());
        assertEquals(UserRole.BUSINESS, response.getBody().getActiveRole());
        assertEquals(14L, response.getBody().getTenantId());
    }
}
