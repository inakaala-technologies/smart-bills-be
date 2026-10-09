package com.bhive.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final long RETENTION_MILLIS = 24 * 60 * 60 * 1000L;
    private static final int MAX_STORED_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final int MAX_RECORDS = 10_000;

    private final ConcurrentMap<String, IdempotencyEntry> entries = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || !isMutation(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!idempotencyKey.matches("[A-Za-z0-9._~-]{1,128}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Idempotency-Key.");
            return;
        }
        if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                "Idempotency keys are not supported for multipart requests.");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                "Idempotency-keyed request bodies must not exceed 1 MiB.");
            return;
        }
        String fingerprint = fingerprint(request, body);
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String recordKey = client + ':' + method + ':' + path + ':' + idempotencyKey;
        pruneExpired();

        IdempotencyEntry candidate = new IdempotencyEntry(fingerprint);
        IdempotencyEntry entry = entries.putIfAbsent(recordKey, candidate);
        if (entry != null) {
            if (!entry.fingerprint.equals(fingerprint)) {
                writeConflict(response, "idempotency_key_reused");
            } else if (!entry.complete) {
                response.setHeader("Retry-After", "1");
                writeConflict(response, "request_in_progress");
            } else if (entry.storedResponse == null) {
                writeConflict(response, "response_not_replayable");
            } else {
                entry.storedResponse.writeTo(response);
                response.setHeader("Idempotency-Replayed", "true");
            }
            return;
        }

        if (entries.size() > MAX_RECORDS) {
            entries.remove(recordKey, candidate);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "1");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"idempotency_store_full\"}");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(new CachedBodyRequest(request, body), wrappedResponse);
            byte[] responseBody = wrappedResponse.getContentAsByteArray();
            StoredResponse storedResponse = responseBody.length <= MAX_STORED_RESPONSE_BYTES
                ? new StoredResponse(wrappedResponse.getStatus(), wrappedResponse.getContentType(),
                    wrappedResponse.getHeader("Location"), responseBody)
                : null;
            candidate.storedResponse = storedResponse;
            candidate.complete = true;
        } catch (IOException | ServletException | RuntimeException exception) {
            entries.remove(recordKey, candidate);
            throw exception;
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void pruneExpired() {
        long expiration = System.currentTimeMillis() - RETENTION_MILLIS;
        entries.entrySet().removeIf(entry -> entry.getValue().createdAtMillis < expiration && entry.getValue().complete);
    }

    private String fingerprint(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, request.getMethod());
            updateDigest(digest, request.getRequestURI());
            updateDigest(digest, request.getQueryString());
            updateDigest(digest, request.getContentType());
            updateDigest(digest, request.getHeader("X-Tenant-Id"));
            updateDigest(digest, request.getHeader("X-User-Id"));
            updateDigest(digest, request.getHeader("Authorization"));
            digest.update(body);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private boolean isMutation(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private void writeConflict(HttpServletResponse response, String error) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }

    private static final class IdempotencyEntry {
        private final String fingerprint;
        private final long createdAtMillis = System.currentTimeMillis();
        private volatile boolean complete;
        private volatile StoredResponse storedResponse;

        private IdempotencyEntry(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    private static final class StoredResponse {
        private final int status;
        private final String contentType;
        private final String location;
        private final byte[] body;

        private StoredResponse(int status, String contentType, String location, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.location = location;
            this.body = Arrays.copyOf(body, body.length);
        }

        private void writeTo(HttpServletResponse response) throws IOException {
            response.setStatus(status);
            if (contentType != null) {
                response.setContentType(contentType);
            }
            if (location != null) {
                response.setHeader("Location", location);
            }
            response.getOutputStream().write(body);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new IllegalStateException("Asynchronous request reads are not supported.");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}