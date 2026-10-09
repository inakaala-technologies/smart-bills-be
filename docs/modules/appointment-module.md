# Appointment Module

## Purpose
Supports time-based bookings for salon, clinic, and service businesses.

## Main entities
- `appointment_services` belongs to one tenant and business and stores the service name, description, price, duration, and enabled state.
- appointments

## Configuration and booking
- Business users can enable or disable appointment requests per business.
- Businesses must add and save at least one active service before enabling appointments; customers can book once both conditions are met.
- Customers can view enabled services and prices only for businesses linked to their customer profile.
- Booking requests reference a service ID; the API resolves its name, duration, and price and saves a price snapshot on the appointment.
- Existing appointments retain their saved price if the service catalog changes later.
- Businesses may include an optional response note when confirming or cancelling; customers can read it with the appointment in the app.

## Production schema update
Before deploying the business response note change, add the nullable `business_note` column to `appointments` using `docs/database/migrations/2026-09-29-appointment-business-note.sql`. Production runs Hibernate schema validation, so the column must exist before the application starts.

## Typical lifecycle
- BOOKED
- CONFIRMED
- COMPLETED
- CANCELLED
- NO_SHOW
