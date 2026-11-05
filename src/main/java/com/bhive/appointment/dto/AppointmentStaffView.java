package com.bhive.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppointmentStaffView(Long staffId, String label, String email, String phone, Long userId) {
}
