package io.github.hrushibhatt.brewfocus.security.ratelimit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Works out each visitor's real address, which rate limits are keyed on.
 *
 * <p>{@code X-Forwarded-For} is easy to fake, so it's believed only when the connection comes from a
 * listed proxy ({@code TRUSTED_PROXIES}), and then read from the right: the last address that isn't
 * itself a trusted proxy is the one that proxy actually saw. With no proxies listed, the
 * connection's own address is the client's.
 */
@Component
public class ClientIpResolver {

    /** Digits, hex, dots, colons and an optional /prefix: an address, never a host name to look up. */
    private static final Pattern ADDRESS = Pattern.compile("[0-9A-Fa-f:.]+(/\\d{1,3})?");

    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(BrewFocusProperties props) {
        this.trusted = parse(props.trustedProxies());
    }

    /** Parses TRUSTED_PROXIES, failing at startup on anything that isn't an address or CIDR range. */
    static List<IpAddressMatcher> parse(List<String> entries) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String entry : entries) {
            String value = entry.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!ADDRESS.matcher(value).matches()) {
                throw new IllegalStateException("TRUSTED_PROXIES: \"" + value + "\" is not an address or CIDR range");
            }
            try {
                matchers.add(new IpAddressMatcher(value));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("TRUSTED_PROXIES: \"" + value + "\" is not an address or CIDR range", e);
            }
        }
        return List.copyOf(matchers);
    }

    public String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || !isTrusted(remote)) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (hop.isEmpty() || !ADDRESS.matcher(hop).matches()) {
                return remote; // a malformed chain: trust only what we saw
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return remote;
    }

    private boolean isTrusted(String address) {
        for (IpAddressMatcher matcher : trusted) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }
}
