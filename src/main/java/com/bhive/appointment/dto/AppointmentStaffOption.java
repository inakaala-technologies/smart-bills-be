package com.bhive.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppointmentStaffOption(Long staffId, String label, Long userId) {
}