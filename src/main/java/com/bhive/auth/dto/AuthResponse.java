package com.bhive.auth.dto;

import com.bhive.auth.entity.UserRole;
import java.util.ArrayList;
import java.util.List;

public class AuthResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private UserRole role;
    private UserRole activeRole;
    private Long tenantId;
    private Long businessId;
    private String message;
    private String accessToken;
    private List<UserAccountSummary> accounts = new ArrayList<>();

    public AuthResponse(Long id, String name, String email, UserRole role, Long tenantId, Long businessId, String message) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.activeRole = role;
        this.tenantId = tenantId;
        this.businessId = businessId;
        this.message = message;
    }

    public AuthResponse(Long id, String name, String email, UserRole role, UserRole activeRole, Long tenantId, Long businessId, String message, List<UserAccountSummary> accounts) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.activeRole = activeRole;
        this.tenantId = tenantId;
        this.businessId = businessId;
        this.message = message;
        this.accounts = accounts == null ? new ArrayList<>() : accounts;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserRole getActiveRole() {
        return activeRole;
    }

    public void setActiveRole(UserRole activeRole) {
        this.activeRole = activeRole;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public List<UserAccountSummary> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<UserAccountSummary> accounts) {
        this.accounts = accounts == null ? new ArrayList<>() : accounts;
    }

    public static class UserAccountSummary {
        private UserRole role;
        private Long tenantId;
        private Long businessId;
        private String status;

        public UserRole getRole() {
            return role;
        }

        public void setRole(UserRole role) {
            this.role = role;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public Long getBusinessId() {
            return businessId;
        }

        public void setBusinessId(Long businessId) {
            this.businessId = businessId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
