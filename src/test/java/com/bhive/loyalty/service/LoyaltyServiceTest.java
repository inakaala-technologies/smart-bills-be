package com.bhive.loyalty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhive.appointment.repository.AppointmentServiceRepository;
import com.bhive.billing.repository.ProductRepository;
import com.bhive.customer.entity.BusinessCustomer;
import com.bhive.customer.repository.BusinessCustomerRepository;
import com.bhive.loyalty.dto.LoyaltyRedemptionResponse;
import com.bhive.loyalty.entity.LoyaltyEntryType;
import com.bhive.loyalty.entity.LoyaltyLedgerEntry;
import com.bhive.loyalty.entity.LoyaltyProgramSettings;
import com.bhive.loyalty.entity.InvoiceEarningMode;
import com.bhive.loyalty.repository.LoyaltyLedgerRepository;
import com.bhive.loyalty.repository.LoyaltyProgramSettingsRepository;
import com.bhive.loyalty.repository.LoyaltyRuleRepository;
import com.bhive.membership.repository.MembershipPlanRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {
    @Mock private LoyaltyLedgerRepository ledgerRepository;
    @Mock private LoyaltyRuleRepository ruleRepository;
    @Mock private LoyaltyProgramSettingsRepository settingsRepository;
    @Mock private BusinessCustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AppointmentServiceRepository appointmentServiceRepository;
    @Mock private MembershipPlanRepository membershipPlanRepository;

    private LoyaltyService loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyService(ledgerRepository, ruleRepository, settingsRepository, customerRepository,
            productRepository, appointmentServiceRepository, membershipPlanRepository);
    }

    @Test
    void redeemsPointsAtConfiguredRateAndReturnsUpdatedBalance() {
        when(customerRepository.findForUpdate(1L, 2L, 3L)).thenReturn(Optional.of(new BusinessCustomer()));
        when(ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(2L, 3L, "INVOICE:10"))
            .thenReturn(Optional.empty());
        when(settingsRepository.findByTenantIdAndBusinessId(1L, 2L)).thenReturn(Optional.of(settings(100, "25.00")));
        when(ledgerRepository.getBalance(1L, 2L, 3L)).thenReturn(500L);
        when(ledgerRepository.save(any(LoyaltyLedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoyaltyRedemptionResponse result = loyaltyService.redeemForPurchase(1L, 2L, 3L, 120,
            new BigDecimal("100.00"), "INVOICE:10", "INVOICE", 10L, "test");

        assertEquals(120, result.pointsRedeemed());
        assertEquals(new BigDecimal("1.20"), result.discountAmount());
        assertEquals(380L, result.balanceAfter());
        ArgumentCaptor<LoyaltyLedgerEntry> entry = ArgumentCaptor.forClass(LoyaltyLedgerEntry.class);
        verify(ledgerRepository).save(entry.capture());
        assertEquals(LoyaltyEntryType.REDEEM, entry.getValue().getEntryType());
        assertEquals("INVOICE:10", entry.getValue().getIdempotencyKey());
    }

    @Test
    void rejectsPointsThatExceedConfiguredPerPurchaseCap() {
        when(customerRepository.findForUpdate(1L, 2L, 3L)).thenReturn(Optional.of(new BusinessCustomer()));
        when(ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(2L, 3L, "BOOKING:4"))
            .thenReturn(Optional.empty());
        when(settingsRepository.findByTenantIdAndBusinessId(1L, 2L)).thenReturn(Optional.of(settings(100, "25.00")));
        when(ledgerRepository.getBalance(1L, 2L, 3L)).thenReturn(30_000L);

        assertThrows(ResponseStatusException.class, () -> loyaltyService.redeemForPurchase(
            1L, 2L, 3L, 2_600, new BigDecimal("100.00"), "BOOKING:4", "APPOINTMENT", 4L, "test"));
    }

    @Test
    void rejectsRedemptionWhenBalanceIsInsufficient() {
        when(customerRepository.findForUpdate(1L, 2L, 3L)).thenReturn(Optional.of(new BusinessCustomer()));
        when(ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(2L, 3L, "BOOKING:5"))
            .thenReturn(Optional.empty());
        when(settingsRepository.findByTenantIdAndBusinessId(1L, 2L)).thenReturn(Optional.of(settings(100, "25.00")));
        when(ledgerRepository.getBalance(1L, 2L, 3L)).thenReturn(10L);

        assertThrows(ResponseStatusException.class, () -> loyaltyService.redeemForPurchase(
            1L, 2L, 3L, 11, new BigDecimal("100.00"), "BOOKING:5", "APPOINTMENT", 5L, "test"));
    }

    @Test
    void automaticallyRedeemsAvailablePointsUpToThePurchaseCap() {
        when(customerRepository.findForUpdate(1L, 2L, 3L)).thenReturn(Optional.of(new BusinessCustomer()));
        when(ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(2L, 3L, "INVOICE:11"))
            .thenReturn(Optional.empty());
        when(settingsRepository.findByTenantIdAndBusinessId(1L, 2L)).thenReturn(Optional.of(settings(100, "25.00")));
        when(ledgerRepository.getBalance(1L, 2L, 3L)).thenReturn(500L);
        when(ledgerRepository.save(any(LoyaltyLedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoyaltyRedemptionResponse result = loyaltyService.redeemMaximumForPurchase(1L, 2L, 3L,
            new BigDecimal("100.00"), "INVOICE:11", "INVOICE", 11L, "test");

        assertEquals(500, result.pointsRedeemed());
        assertEquals(new BigDecimal("5.00"), result.discountAmount());
        assertEquals(0L, result.balanceAfter());
    }

    @Test
    void awardsConfiguredPercentagePointsFromFinalInvoiceAmount() {
        LoyaltyProgramSettings settings = settings(100, "25.00");
        settings.setInvoiceEarningMode(InvoiceEarningMode.PERCENTAGE);
        settings.setInvoiceEarningValue(new BigDecimal("2.50"));
        when(settingsRepository.findByTenantIdAndBusinessId(1L, 2L)).thenReturn(Optional.of(settings));
        BusinessCustomer businessCustomer = new BusinessCustomer();
        businessCustomer.setTenantId(1L);
        when(customerRepository.findByBusinessIdAndCustomerProfileId(2L, 3L))
            .thenReturn(Optional.of(businessCustomer));
        when(ledgerRepository.findByBusinessIdAndCustomerIdAndIdempotencyKey(2L, 3L, "INVOICE-EARN:12"))
            .thenReturn(Optional.empty());
        when(ledgerRepository.getBalance(1L, 2L, 3L)).thenReturn(0L);
        when(ledgerRepository.save(any(LoyaltyLedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loyaltyService.awardInvoiceIfConfigured(1L, 2L, 3L, 12L, new BigDecimal("199.00"));

        ArgumentCaptor<LoyaltyLedgerEntry> entry = ArgumentCaptor.forClass(LoyaltyLedgerEntry.class);
        verify(ledgerRepository).save(entry.capture());
        assertEquals(LoyaltyEntryType.EARN, entry.getValue().getEntryType());
        assertEquals(4, entry.getValue().getPoints());
        assertEquals("INVOICE-EARN:12", entry.getValue().getIdempotencyKey());
    }

    private LoyaltyProgramSettings settings(int pointsPerCurrencyUnit, String maxRedemptionPercent) {
        LoyaltyProgramSettings settings = new LoyaltyProgramSettings();
        settings.setPointsPerCurrencyUnit(pointsPerCurrencyUnit);
        settings.setMaxRedemptionPercent(new BigDecimal(maxRedemptionPercent));
        return settings;
    }
}
