package io.github.hrushibhatt.brewfocus.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.hrushibhatt.brewfocus.support.TestProperties;

class ClientIpResolverTest {

    private static MockHttpServletRequest from(String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void withoutTrustedProxiesTheConnectionIsTheClient() {
        var ips = new ClientIpResolver(TestProperties.defaults());
        assertThat(ips.clientIp(from("203.0.113.7", "1.2.3.4"))).isEqualTo("203.0.113.7");
    }

    @Test
    void aTrustedProxyReportsTheRealAddress() {
        var ips = new ClientIpResolver(TestProperties.withTrustedProxies("10.0.0.0/8"));
        assertThat(ips.clientIp(from("10.0.0.5", "198.51.100.9"))).isEqualTo("198.51.100.9");
    }

    @Test
    void aSpoofedLeftmostHopIsIgnored() {
        // The client sent "1.1.1.1" itself; the proxy appended the address it really saw.
        var ips = new ClientIpResolver(TestProperties.withTrustedProxies("10.0.0.0/8"));
        assertThat(ips.clientIp(from("10.0.0.5", "1.1.1.1, 198.51.100.9"))).isEqualTo("198.51.100.9");
    }

    @Test
    void anUntrustedConnectionCantClaimAnotherAddress() {
        var ips = new ClientIpResolver(TestProperties.withTrustedProxies("10.0.0.0/8"));
        assertThat(ips.clientIp(from("203.0.113.7", "198.51.100.9"))).isEqualTo("203.0.113.7");
    }

    @Test
    void garbageInTheHeaderFallsBackToTheConnection() {
        var ips = new ClientIpResolver(TestProperties.withTrustedProxies("10.0.0.0/8"));
        assertThat(ips.clientIp(from("10.0.0.5", "evil.example"))).isEqualTo("10.0.0.5");
    }

    @Test
    void invalidSettingsStopTheServerAtStartup() {
        assertThatThrownBy(() -> ClientIpResolver.parse(List.of("proxy.internal")))
                .hasMessageContaining("not an address or CIDR range");
        assertThatThrownBy(() -> ClientIpResolver.parse(List.of("10.0.0.0/99")))
                .hasMessageContaining("not an address or CIDR range");
        assertThat(ClientIpResolver.parse(List.of(" 127.0.0.1 ", "", "::1", "192.168.0.0/16"))).hasSize(3);
    }
}
