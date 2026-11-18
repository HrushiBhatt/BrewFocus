package io.github.hrushibhatt.brewfocus.security.oauth;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Throws away each provider's access token as soon as sign-in is done. The app only uses OAuth 2.0 to
 * learn who someone is, never to call the provider for them later, so keeping tokens would hold
 * something sensitive for no reason (Spring Security's default keeps them in memory).
 */
public class NoTokenStore implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId, Authentication principal,
            HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // intentionally not kept
    }

    @Override
    public void removeAuthorizedClient(String registrationId, Authentication principal, HttpServletRequest request,
            HttpServletResponse response) {
        // nothing was kept
    }
}
