package com.bhive.common.config;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitFilterTest {

    @Test
    void rejectsRequestsAfterClientBucketIsExhausted() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(2, 0.01, 1, 0.01, new ConcurrentHashMap<>());

        MockHttpServletResponse firstResponse = perform(filter, "/api/v1/businesses");
        MockHttpServletResponse secondResponse = perform(filter, "/api/v1/businesses");
        MockHttpServletResponse limitedResponse = perform(filter, "/api/v1/businesses");

        assertEquals(200, firstResponse.getStatus());
        assertEquals(200, secondResponse.getStatus());
        assertEquals(429, limitedResponse.getStatus());
        assertNotNull(limitedResponse.getHeader("Retry-After"));
        assertTrue(limitedResponse.getContentAsString().contains("rate_limit_exceeded"));
    }

    @Test
    void authenticationRequestsUseTheirOwnStricterBucket() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(2, 0.01, 1, 0.01, new ConcurrentHashMap<>());

        assertEquals(200, perform(filter, "/api/v1/auth/login").getStatus());
        assertEquals(429, perform(filter, "/api/v1/auth/login").getStatus());
        assertEquals(200, perform(filter, "/api/v1/businesses").getStatus());
    }

    private MockHttpServletResponse perform(ApiRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}