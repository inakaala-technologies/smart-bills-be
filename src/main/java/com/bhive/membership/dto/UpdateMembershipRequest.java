package com.bhive.membership.dto;

import com.bhive.membership.entity.MembershipStatus;
import java.time.LocalDate;

public class UpdateMembershipRequest {

    private Long planId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String paymentStatus;
    private MembershipStatus status;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus status) { this.status = status; }
}