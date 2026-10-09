package com.bhive.appointment.repository;

import com.bhive.appointment.entity.Appointment;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
	List<Appointment> findByBusinessId(Long businessId);
	List<Appointment> findByBusinessIdAndScheduledAtBetween(Long businessId, LocalDateTime start, LocalDateTime end);
	List<Appointment> findByReminder24hSentAtIsNullAndScheduledAtBetweenAndStatusIn(LocalDateTime start, LocalDateTime end, Collection<com.bhive.appointment.entity.AppointmentStatus> statuses);
	List<Appointment> findByReminder1hSentAtIsNullAndScheduledAtBetweenAndStatusIn(LocalDateTime start, LocalDateTime end, Collection<com.bhive.appointment.entity.AppointmentStatus> statuses);
}
