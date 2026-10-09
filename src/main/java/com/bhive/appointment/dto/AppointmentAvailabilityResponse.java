package com.bhive.appointment.dto;

import java.time.LocalDate;
import java.util.List;

public record AppointmentAvailabilityResponse(
    LocalDate date,
    List<String> availableSlots,
    List<AppointmentStaffOption> staff
) {
}