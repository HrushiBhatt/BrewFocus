package io.github.hrushibhatt.brewfocus.security.oauth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;

/**
 * The OAuth 2.0 providers people can sign in with. A provider is offered only when its client ID and
 * secret are both set, so the app runs exactly as before with none configured: the buttons simply
 * don't appear, and the OAuth endpoints aren't mapped.
 */
public final class OAuthProviders {

    public static final String GOOGLE = "google";
    public static final String GITHUB = "github";

    /** Where providers send people back: Spring Security's callback, per provider. */
    static final String CALLBACK = "/login/oauth2/code/{registrationId}";

    private final List<ClientRegistration> registrations;

    public OAuthProviders(List<ClientRegistration> registrations) {
        this.registrations = List.copyOf(registrations);
    }

    /** The providers configured in {@code props}. */
    public static OAuthProviders from(BrewFocusProperties props) {
        String redirect = redirectUri(props.publicUrl());
        List<ClientRegistration> list = new ArrayList<>();
        BrewFocusProperties.Client google = props.oauth().google();
        if (google.configured()) {
            // OpenID Connect: Google returns a signed ID token with a verified-email claim.
            list.add(CommonOAuth2Provider.GOOGLE.getBuilder(GOOGLE)
                    .clientId(google.clientId())
                    .clientSecret(google.clientSecret())
                    .redirectUri(redirect)
                    .build());
        }
        BrewFocusProperties.Client github = props.oauth().github();
        if (github.configured()) {
            // Plain OAuth 2.0: GitHubUserService looks up the verified email separately.
            list.add(CommonOAuth2Provider.GITHUB.getBuilder(GITHUB)
                    .clientId(github.clientId())
                    .clientSecret(github.clientSecret())
                    .scope("read:user", "user:email")
                    .redirectUri(redirect)
                    .build());
        }
        return new OAuthProviders(list);
    }

    /**
     * The callback address registered with each provider. Behind a proxy the server can't always tell
     * its public address, so {@code PUBLIC_URL} can state it.
     */
    static String redirectUri(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "{baseUrl}" + CALLBACK;
        }
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + CALLBACK;
    }

    public List<ClientRegistration> registrations() {
        return registrations;
    }

    public boolean isEmpty() {
        return registrations.isEmpty();
    }

    /** The provider IDs, for the login page's buttons. */
    public List<String> ids() {
        return registrations.stream().map(ClientRegistration::getRegistrationId).toList();
    }
}
