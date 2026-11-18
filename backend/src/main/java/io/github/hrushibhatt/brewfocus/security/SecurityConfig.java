package io.github.hrushibhatt.brewfocus.security;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;
import io.github.hrushibhatt.brewfocus.security.oauth.GitHubUserService;
import io.github.hrushibhatt.brewfocus.security.oauth.NoTokenStore;
import io.github.hrushibhatt.brewfocus.security.oauth.OAuthAccountService;
import io.github.hrushibhatt.brewfocus.security.oauth.OAuthLoginHandler;
import io.github.hrushibhatt.brewfocus.security.oauth.OAuthProviders;
import io.github.hrushibhatt.brewfocus.security.ratelimit.ClientIpResolver;
import io.github.hrushibhatt.brewfocus.security.ratelimit.RateLimitFilter;
import io.github.hrushibhatt.brewfocus.security.ratelimit.RateLimits;
import io.github.hrushibhatt.brewfocus.web.JsonReplies;
import jakarta.servlet.DispatcherType;

/**
 * The protections every request passes through, in order:
 *
 * <ol>
 *   <li>Security headers on every response, the page's files included.
 *   <li>{@code Cache-Control: no-store} on the API.
 *   <li>A cross-site check on anything that changes state ({@link CrossOriginProtectionFilter}).
 *   <li>Rate limits per address ({@link RateLimitFilter}).
 *   <li>The session cookie, read into a signed-in user ({@link SessionCookieFilter}).
 *   <li>CSRF double-submit: changes need the {@code XSRF-TOKEN} cookie echoed in a header.
 *   <li>Authorization: everything under {@code /api} needs an account, except signing in.
 * </ol>
 *
 * <p>Sessions are stateless from Spring Security's point of view: the JWT cookie is read on every
 * request and nothing is kept in a servlet session. OAuth 2.0 sign-in is switched on only when a
 * provider is configured.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Lets the page load only its own scripts, styles, fonts and images, so injected markup can't run a
     * script or send a user's data anywhere. Inline styles stay allowed because React sets style
     * attributes (the coffee level, slider fills); scripts get no such exception. Ambience tracks a
     * user picks from their own disk play from blob: URLs.
     */
    static final String CONTENT_POLICY = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; media-src 'self' blob:; "
            + "manifest-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    static final String CSRF_MESSAGE = "this request is missing its security token; reload the page and try again";

    @Bean
    OAuthProviders oauthProviders(BrewFocusProperties props) {
        return OAuthProviders.from(props);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, BrewFocusProperties props, SessionService sessions,
            RateLimits limits, ClientIpResolver ips, OAuthProviders providers, OAuthAccountService oauthAccounts)
            throws Exception {
        boolean secure = props.secureCookies();

        http.headers(headers -> {
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    secure ? CONTENT_POLICY + "; upgrade-insecure-requests" : CONTENT_POLICY));
            headers.frameOptions(frame -> frame.deny());
            headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
            headers.crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN));
            headers.crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN));
            headers.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY));
            headers.cacheControl(cache -> cache.disable()); // set per path instead (NoStoreFilter, WebConfig)
            if (secure) {
                // Sent whenever the app is configured for HTTPS, even when TLS ends at a proxy.
                headers.httpStrictTransportSecurity(hsts -> hsts.requestMatcher(AnyRequestMatcher.INSTANCE)
                        .maxAgeInSeconds(Duration.ofDays(365).toSeconds())
                        .includeSubDomains(true));
            } else {
                headers.httpStrictTransportSecurity(hsts -> hsts.disable());
            }
        });

        http.csrf(csrf -> csrf.csrfTokenRepository(csrfCookie(secure)).csrfTokenRequestHandler(eagerCsrf()));

        http.addFilterBefore(new NoStoreFilter(), CsrfFilter.class);
        http.addFilterBefore(new CrossOriginProtectionFilter(), CsrfFilter.class);
        http.addFilterBefore(new RateLimitFilter(limits, ips), CsrfFilter.class);
        http.addFilterBefore(new SessionCookieFilter(sessions), CsrfFilter.class);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.requestCache(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable); // POST /api/auth/logout ends the session row instead

        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/providers").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()); // the app's own files, and the OAuth 2.0 endpoints

        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, e) ->
                        JsonReplies.write(response, HttpStatus.UNAUTHORIZED, "please log in"))
                .accessDeniedHandler((request, response, e) -> JsonReplies.write(response, HttpStatus.FORBIDDEN,
                        e instanceof CsrfException ? CSRF_MESSAGE : "you can't do that")));

        if (!providers.isEmpty()) {
            var registrations = new InMemoryClientRegistrationRepository(providers.registrations());
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            var handler = new OAuthLoginHandler(oauthAccounts, sessions);
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(registrations)
                    .authorizedClientRepository(new NoTokenStore())
                    .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                    .userInfoEndpoint(info -> info.userService(new GitHubUserService()))
                    .loginPage("/login") // the React page; stops Spring generating one of its own
                    .successHandler(handler)
                    .failureHandler(handler));
        }
        return http.build();
    }

    /**
     * The CSRF token lives in a cookie page scripts can read ({@code XSRF-TOKEN}), and every POST, PUT,
     * PATCH and DELETE must echo it in the {@code X-XSRF-TOKEN} header. Another site can make the
     * browser send the cookie, but can't read it to copy into the header. SameSite=Lax on the session
     * cookie already stops most cross-site requests; this closes the rest, including login CSRF.
     */
    private static CookieCsrfTokenRepository csrfCookie(boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure).maxAge(Duration.ofDays(365)));
        return repository;
    }

    /**
     * Sets the CSRF cookie on every response, not only once something asks for the token, so the very
     * first request a fresh page makes (often a sign-in) already has one to echo.
     */
    private static CsrfTokenRequestAttributeHandler eagerCsrf() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
