# Appointment Module

## Purpose
Supports time-based bookings for salon, clinic, and service businesses.

## Main entities
- `appointment_services` belongs to one tenant and business and stores the service name, description, price, duration, and enabled state.
- `appointment_staff` stores business-scoped staff profiles. Staff do not need a BHive account; adding a profile does not create an account or change an existing user's roles.
- appointments

## Configuration and booking
- Business users can enable or disable appointment requests per business.
- Business users can add appointment staff by name and contact details; only active staff profiles are offered during booking and assignment.
- Businesses must add and save at least one active service before enabling appointments; customers can book once both conditions are met.
- Customers can view enabled services and prices only for businesses linked to their customer profile.
- Booking requests reference a service ID; the API resolves its name, duration, and price and saves a price snapshot on the appointment.
- Existing appointments retain their saved price if the service catalog changes later.
- Businesses may include an optional response note when confirming or cancelling; customers can read it with the appointment in the app.

## Production schema update
Before deploying appointment schema changes, apply `docs/database/migrations/2026-09-29-appointment-business-note.sql` and `docs/database/migrations/2026-11-05-appointment-staff-roster.sql`. Production runs Hibernate schema validation, so the columns and staff table must exist before the application starts. The roster migration seeds existing business users and preserves current appointment assignments.

## Typical lifecycle
- BOOKED
- CONFIRMED
- COMPLETED
- CANCELLED
- NO_SHOW
