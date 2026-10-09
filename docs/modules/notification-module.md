# In-app Notifications

## Delivery model
- Notifications are persisted in `app_notifications` and scoped by tenant and recipient user.
- Dashboard clients request the latest 50 records and an unread count from `GET /api/v1/notifications/my` every 30 seconds while visible, when the inbox opens, and when the browser tab regains focus.
- Read state is persisted with the single-item and mark-all endpoints.
- Notification producers call the shared `NotificationService`; the API and stored notification contract are independent of the current polling transport, allowing push delivery to be introduced later.

## Current producers
- Invoice creation notifies the linked customer user.
- Customer appointment bookings notify active users for the business; business-created bookings and appointment status changes notify the other participant.
- Recorded payments notify the linked customer user.
- Membership creation notifies registered customer users through both supported membership endpoints. Renewal reminders also create inbox notifications while preserving legacy membership reminder records.
- Business-uploaded documents notify the linked customer user.
- Notifications are not sent to unlinked customer profiles, and broad events such as offer publication are not broadcast to every customer.

## Database
- Apply `database/migrations/2026-09-30-app-notifications.sql` before deploying.# Notification Module

## Purpose
Manages outbound communication to customers and business users through email, SMS, push, and WhatsApp.

## Supported channels
- Email
- SMS
- Push notification
- WhatsApp

## Core idea
Business logic publishes events. A notification worker sends them asynchronously.
