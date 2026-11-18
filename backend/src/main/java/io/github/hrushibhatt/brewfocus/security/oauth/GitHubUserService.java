package io.github.hrushibhatt.brewfocus.security.oauth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Loads a plain OAuth 2.0 user, adding GitHub's verified email. GitHub's profile only shows an email
 * the person chose to make public, with no word on whether it's verified, so for GitHub this asks
 * {@code /user/emails} for the primary, verified address. Other providers pass straight through.
 */
public class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String EMAILS = "https://api.github.com/user/emails";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    private final RestClient http = RestClient.create();

    /** One entry of GitHub's email list. */
    record GitHubEmail(@JsonProperty("email") String email, @JsonProperty("primary") boolean primary,
            @JsonProperty("verified") boolean verified) {}

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = delegate.loadUser(request);
        if (!OAuthProviders.GITHUB.equals(request.getClientRegistration().getRegistrationId())) {
            return user;
        }
        List<GitHubEmail> emails = http.get()
                .uri(EMAILS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        Map<String, Object> claims = new HashMap<>(user.getAttributes());
        claims.remove("email"); // the public one is unverified: only a confirmed address counts
        claims.put("email_verified", false);
        if (emails != null) {
            emails.stream().filter(e -> e.primary() && e.verified()).findFirst().ifPresent(e -> {
                claims.put("email", e.email());
                claims.put("email_verified", true);
            });
        }
        String nameKey = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint()
                .getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), claims, nameKey);
    }
}
