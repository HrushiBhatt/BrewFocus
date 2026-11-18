package io.github.hrushibhatt.brewfocus.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.hrushibhatt.brewfocus.account.Settings;
import io.github.hrushibhatt.brewfocus.account.User;
import io.github.hrushibhatt.brewfocus.account.UserRepository;

class OAuthAccountServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final OAuthIdentityRepository identities = mock(OAuthIdentityRepository.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final OAuthAccountService service = new OAuthAccountService(users, identities, transaction, Clock.systemUTC());

    OAuthAccountServiceTest() {
        when(transaction.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private static OAuthIdentity grace(boolean verified) {
        return new OAuthIdentity("google", "sub-42", "Grace@Example.com", verified, "Grace Hopper");
    }

    @Test
    void aReturningProviderAccountOpensTheSameAccount() {
        when(identities.findUserId("google", "sub-42")).thenReturn(Optional.of(7L));
        assertThat(service.signIn(grace(true))).isEqualTo(7L);
        verify(users, never()).insert(anyString(), anyString(), any());
    }

    @Test
    void aNewVerifiedEmailCreatesAPasswordlessAccountAndLinksIt() {
        when(identities.findUserId("google", "sub-42")).thenReturn(Optional.empty());
        when(users.emailTaken("grace@example.com")).thenReturn(false);
        when(users.insert("grace@example.com", "Grace Hopper", null))
                .thenReturn(new User(9, "grace@example.com", "Grace Hopper", null, Settings.DEFAULTS, Instant.now()));

        assertThat(service.signIn(grace(true))).isEqualTo(9L);
        verify(identities).link(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void anUnverifiedEmailIsRefused() {
        when(identities.findUserId("google", "sub-42")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.signIn(grace(false)))
                .isInstanceOf(OAuthAccountService.RefusedException.class)
                .hasMessage("unverified-email");
    }

    /** No linking by email: see the class comment on pre-account takeover. */
    @Test
    void anEmailThatAlreadyHasAnAccountIsRefusedNotMerged() {
        when(identities.findUserId("google", "sub-42")).thenReturn(Optional.empty());
        when(users.emailTaken("grace@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.signIn(grace(true))).hasMessage("email-in-use");
        verify(identities, never()).link(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void namesFallBackToTheEmailAndAreTrimmedToFit() {
        assertThat(OAuthAccountService.displayName(null, "grace@example.com")).isEqualTo("grace");
        assertThat(OAuthAccountService.displayName("  ", "grace@example.com")).isEqualTo("grace");
        assertThat(OAuthAccountService.displayName("x".repeat(90), "g@e.co")).hasSize(60);
    }

    @Test
    void readsOpenIdConnectAndGitHubStyleClaims() {
        var oidc = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")),
                Map.of("sub", "abc", "email", "a@b.co", "email_verified", true, "name", "Ada"), "sub");
        assertThat(OAuthIdentity.from("google", oidc)).isEqualTo(new OAuthIdentity("google", "abc", "a@b.co", true, "Ada"));

        var github = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")),
                Map.of("id", 583231, "login", "octocat", "email_verified", false), "id");
        assertThat(OAuthIdentity.from("github", github))
                .isEqualTo(new OAuthIdentity("github", "583231", null, false, "octocat"));
    }
}
