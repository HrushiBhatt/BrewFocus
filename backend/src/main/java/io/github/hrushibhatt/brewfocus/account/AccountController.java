package io.github.hrushibhatt.brewfocus.account;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.hrushibhatt.brewfocus.security.SessionPrincipal;
import io.github.hrushibhatt.brewfocus.security.SessionService;
import io.github.hrushibhatt.brewfocus.security.oauth.OAuthProviders;
import io.github.hrushibhatt.brewfocus.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Accounts over HTTP: signing up, in and out, the password, and settings. Handlers stay thin: they
 * read the request, call {@link AccountService} or {@link SessionService}, and write the reply.
 */
@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accounts;
    private final SessionService sessions;
    private final UserRepository users;
    private final OAuthProviders providers;

    public AccountController(AccountService accounts, SessionService sessions, UserRepository users,
            OAuthProviders providers) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.users = users;
        this.providers = providers;
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return Map.of("ok", true);
    }

    /** Which OAuth 2.0 providers the login page should offer. Empty when none are configured. */
    @GetMapping("/auth/providers")
    List<String> providers() {
        return providers.ids();
    }

    @PostMapping("/auth/register")
    ResponseEntity<UserView> register(@Validated @RequestBody AccountRequests.Registration in,
            HttpServletRequest request, HttpServletResponse response) {
        User user = accounts.register(in);
        sessions.signIn(request, response, user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserView.of(user));
    }

    @PostMapping("/auth/login")
    UserView login(@Validated @RequestBody AccountRequests.Credentials in, HttpServletRequest request,
            HttpServletResponse response) {
        User user = accounts.login(in);
        sessions.signIn(request, response, user.id());
        return UserView.of(user);
    }

    /** Ends this browser's session. Open to anyone, so a stale page can always sign itself out. */
    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.signOut(request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends the user's sessions on every device, this one included. It works because each JWT names a
     * database row: a JWT on its own couldn't be taken back before it expired.
     */
    @PostMapping("/auth/logout-all")
    ResponseEntity<Void> logoutAll(@AuthenticationPrincipal SessionPrincipal me, HttpServletResponse response) {
        sessions.revokeAll(me.userId(), null);
        sessions.clearCookie(response);
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user. The app calls this on load to restore a session. */
    @GetMapping("/me")
    UserView me(@AuthenticationPrincipal SessionPrincipal me, HttpServletRequest request, HttpServletResponse response) {
        return users.findById(me.userId()).map(UserView::of).orElseThrow(() -> {
            sessions.signOut(request, response); // a valid token for an account that no longer exists
            return new ApiException(HttpStatus.UNAUTHORIZED, "please log in");
        });
    }

    @PutMapping("/me/settings")
    Settings saveSettings(@AuthenticationPrincipal SessionPrincipal me, @Validated @RequestBody Settings in) {
        return accounts.saveSettings(me.userId(), in);
    }

    @PutMapping("/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal SessionPrincipal me,
            @Validated @RequestBody AccountRequests.PasswordChange in) {
        accounts.changePassword(me.userId(), me.sessionId(), in);
        return ResponseEntity.noContent().build();
    }
}
