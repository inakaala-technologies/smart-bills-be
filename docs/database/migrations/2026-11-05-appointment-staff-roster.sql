CREATE TABLE appointment_staff (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    business_id BIGINT NOT NULL,
    staff_name VARCHAR(160) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    linked_user_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_staff_linked_user (tenant_id, business_id, linked_user_id),
    KEY idx_appointment_staff_business_status (tenant_id, business_id, status)
);

ALTER TABLE appointments
    ADD COLUMN staff_id BIGINT;

INSERT INTO appointment_staff (
    tenant_id,
    business_id,
    staff_name,
    email,
    phone,
    linked_user_id,
    status,
    created_at,
    updated_at
)
SELECT
    business_users.tenant_id,
    business_users.business_id,
    COALESCE(NULLIF(TRIM(customer_profiles.name), ''), CONCAT('Staff ', business_users.user_id)),
    customer_profiles.email,
    customer_profiles.phone,
    business_users.user_id,
    CASE
        WHEN UPPER(COALESCE(business_users.status, 'ACTIVE')) = 'ACTIVE' THEN 'ACTIVE'
        ELSE 'INACTIVE'
    END,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM business_users
LEFT JOIN customer_profiles
    ON customer_profiles.tenant_id = business_users.tenant_id
    AND customer_profiles.user_id = business_users.user_id;

UPDATE appointments
JOIN appointment_staff
    ON appointment_staff.business_id = appointments.business_id
    AND appointment_staff.linked_user_id = appointments.staff_user_id
SET appointments.staff_id = appointment_staff.id
WHERE appointments.staff_user_id IS NOT NULL;
