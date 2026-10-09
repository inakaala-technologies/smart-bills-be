package com.bhive.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final int apiCapacity;
    private final double apiTokensPerSecond;
    private final int authCapacity;
    private final double authTokensPerSecond;
    private final ConcurrentMap<String, TokenBucket> buckets;

    @Autowired
    public ApiRateLimitFilter(
        @Value("${app.security.rate-limit.api-capacity:120}") int apiCapacity,
        @Value("${app.security.rate-limit.api-refill-per-second:2}") double apiTokensPerSecond,
        @Value("${app.security.rate-limit.auth-capacity:10}") int authCapacity,
        @Value("${app.security.rate-limit.auth-refill-per-second:0.1667}") double authTokensPerSecond
    ) {
        this(apiCapacity, apiTokensPerSecond, authCapacity, authTokensPerSecond, new ConcurrentHashMap<>());
    }

    ApiRateLimitFilter(int apiCapacity, double apiTokensPerSecond, int authCapacity,
                       double authTokensPerSecond, ConcurrentMap<String, TokenBucket> buckets) {
        if (apiCapacity < 1 || authCapacity < 1 || apiTokensPerSecond <= 0 || authTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Rate limit capacities and refill rates must be positive.");
        }
        this.apiCapacity = apiCapacity;
        this.apiTokensPerSecond = apiTokensPerSecond;
        this.authCapacity = authCapacity;
        this.authTokensPerSecond = authTokensPerSecond;
        this.buckets = buckets;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean authRequest = path.startsWith("/api/v1/auth/");
        int capacity = authRequest ? authCapacity : apiCapacity;
        double refillRate = authRequest ? authTokensPerSecond : apiTokensPerSecond;
        String clientKey = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String bucketKey = (authRequest ? "auth:" : "api:") + clientKey;
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new TokenBucket(capacity));
        long retryAfter = bucket.tryConsume(capacity, refillRate);

        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        if (buckets.size() > 10_000) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isIdleFor(300_000_000_000L));
        }
        filterChain.doFilter(request, response);
    }

    static final class TokenBucket {
        private double tokens;
        private long lastRefillNanos = System.nanoTime();
        private long lastAccessNanos = lastRefillNanos;

        TokenBucket(int capacity) {
            this.tokens = capacity;
        }

        synchronized long tryConsume(int capacity, double tokensPerSecond) {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) / 1_000_000_000d * tokensPerSecond);
            lastRefillNanos = now;
            lastAccessNanos = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / tokensPerSecond);
        }

        synchronized boolean isIdleFor(long idleNanos) {
            return System.nanoTime() - lastAccessNanos > idleNanos;
        }
    }
}