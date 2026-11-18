package io.github.hrushibhatt.brewfocus.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CrossOriginProtectionFilterTest {

    private static int send(String method, String... headers) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/tasks");
        request.addHeader("Host", "brew.example");
        for (int i = 0; i < headers.length; i += 2) {
            request.addHeader(headers[i], headers[i + 1]);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CrossOriginProtectionFilter().doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void sameOriginRequestsPass() throws Exception {
        assertThat(send("POST", "Sec-Fetch-Site", "same-origin")).isEqualTo(200);
        assertThat(send("POST", "Origin", "https://brew.example")).isEqualTo(200);
        assertThat(send("POST")).isEqualTo(200); // not a browser page: curl, scripts, tests
    }

    @Test
    void crossSiteChangesAreRefused() throws Exception {
        assertThat(send("POST", "Sec-Fetch-Site", "cross-site")).isEqualTo(403);
        assertThat(send("DELETE", "Sec-Fetch-Site", "same-site")).isEqualTo(403);
        assertThat(send("PUT", "Origin", "https://evil.example")).isEqualTo(403);
        assertThat(send("PATCH", "Origin", "null")).isEqualTo(403);
    }

    @Test
    void readsAreNeverBlocked() throws Exception {
        assertThat(send("GET", "Sec-Fetch-Site", "cross-site")).isEqualTo(200);
    }
}
