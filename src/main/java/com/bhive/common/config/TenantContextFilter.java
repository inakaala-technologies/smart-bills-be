package com.bhive.common.config;

import com.bhive.common.util.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.bhive.common.config.AccessTokenService.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            Long tenantId = resolveTenantId(request.getHeader("X-Tenant-Id"), user);
            if (tenantId == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is not available to this user.");
                return;
            }
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(user.userId());
        } else {
            TenantContext.setUserId(null);
            TenantContext.setTenantId(null);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenantId(String tenantHeader, AuthenticatedUser user) {
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return user.tenantIds().get(0);
        }
        try {
            Long requestedTenantId = Long.valueOf(tenantHeader);
            return user.tenantIds().contains(requestedTenantId) ? requestedTenantId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
