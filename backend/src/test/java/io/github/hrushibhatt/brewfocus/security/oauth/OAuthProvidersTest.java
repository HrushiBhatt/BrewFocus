package io.github.hrushibhatt.brewfocus.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties.Client;
import io.github.hrushibhatt.brewfocus.support.TestProperties;

class OAuthProvidersTest {

    private static final Client NONE = new Client("", "");

    @Test
    void nothingIsOfferedUntilAClientIsConfigured() {
        assertThat(OAuthProviders.from(TestProperties.defaults()).isEmpty()).isTrue();
        // Half a client is no client: an ID without its secret can't complete a sign-in.
        assertThat(OAuthProviders.from(TestProperties.withOAuth(new Client("id", ""), NONE, null)).isEmpty()).isTrue();
    }

    @Test
    void eachConfiguredProviderIsOffered() {
        var providers = OAuthProviders.from(TestProperties.withOAuth(new Client("g-id", "g-secret"),
                new Client("h-id", "h-secret"), null));
        assertThat(providers.ids()).containsExactly("google", "github");

        var github = providers.registrations().get(1);
        assertThat(github.getScopes()).contains("user:email"); // needed to read the verified email
        assertThat(providers.registrations().get(0).getScopes()).contains("openid", "email");
    }

    @Test
    void callbacksUseThePublicAddressWhenGiven() {
        assertThat(OAuthProviders.redirectUri(null)).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(OAuthProviders.redirectUri("https://brew.example/"))
                .isEqualTo("https://brew.example/login/oauth2/code/{registrationId}");
    }
}
