package com.bhive.common.config;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdempotencyFilterTest {

    @Test
    void replaysSuccessfulResponseForIdenticalRequest() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter();
        AtomicInteger executions = new AtomicInteger();

        MockHttpServletResponse first = perform(filter, "{\"amount\":10}", (request, response) -> {
            executions.incrementAndGet();
            jakarta.servlet.http.HttpServletResponse httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"id\":42}");
        });
        MockHttpServletResponse replay = perform(filter, "{\"amount\":10}", (request, response) -> {
            executions.incrementAndGet();
            jakarta.servlet.http.HttpServletResponse httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
            httpResponse.setStatus(201);
            httpResponse.getWriter().write("unexpected");
        });

        assertEquals(1, executions.get());
        assertEquals(201, first.getStatus());
        assertEquals(201, replay.getStatus());
        assertEquals("{\"id\":42}", replay.getContentAsString());
        assertEquals("true", replay.getHeader("Idempotency-Replayed"));
    }

    @Test
    void rejectsReuseOfKeyWithDifferentPayload() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter();
        AtomicInteger executions = new AtomicInteger();
        perform(filter, "{\"amount\":10}", (request, response) -> executions.incrementAndGet());

        MockHttpServletResponse conflict = perform(filter, "{\"amount\":20}", (request, response) -> executions.incrementAndGet());

        assertEquals(409, conflict.getStatus());
        assertEquals(1, executions.get());
    }

    @Test
    void doesNotCacheSingleUseReceiptLinkTokens() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter();
        AtomicInteger executions = new AtomicInteger();

        MockHttpServletResponse first = performSingleUseEndpoint(
            filter, executions, "/api/v1/customer/receipt-link-tokens", "receipt-token-request-1"
        );
        MockHttpServletResponse second = performSingleUseEndpoint(
            filter, executions, "/api/v1/customer/receipt-link-tokens", "receipt-token-request-1"
        );

        assertEquals(2, executions.get());
        assertEquals("token-1", first.getContentAsString());
        assertEquals("token-2", second.getContentAsString());
        assertNull(second.getHeader("Idempotency-Replayed"));
    }

    @Test
    void doesNotCachePartnerApplicationStatusAccessToken() throws Exception {
        IdempotencyFilter filter = new IdempotencyFilter();
        AtomicInteger executions = new AtomicInteger();

        MockHttpServletResponse first = performSingleUseEndpoint(
            filter, executions, "/api/v1/pos-partner-applications", "partner-application-1"
        );
        MockHttpServletResponse second = performSingleUseEndpoint(
            filter, executions, "/api/v1/pos-partner-applications", "partner-application-1"
        );

        assertEquals(2, executions.get());
        assertEquals("token-1", first.getContentAsString());
        assertEquals("token-2", second.getContentAsString());
        assertNull(second.getHeader("Idempotency-Replayed"));
    }

    private MockHttpServletResponse performSingleUseEndpoint(
        IdempotencyFilter filter,
        AtomicInteger executions,
        String path,
        String idempotencyKey
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("192.0.2.11");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Idempotency-Key", idempotencyKey);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            int tokenNumber = executions.incrementAndGet();
            ((jakarta.servlet.http.HttpServletResponse) servletResponse).getWriter()
                .write("token-" + tokenNumber);
        });
        return response;
    }

    private MockHttpServletResponse perform(IdempotencyFilter filter, String body,
                                            jakarta.servlet.FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        request.setRemoteAddr("192.0.2.11");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Idempotency-Key", "payment-attempt-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}