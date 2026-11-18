package io.github.hrushibhatt.brewfocus.security.oauth;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.hrushibhatt.brewfocus.account.User;
import io.github.hrushibhatt.brewfocus.account.UserRepository;

/**
 * Decides which account an OAuth 2.0 sign-in opens.
 *
 * <ol>
 *   <li>A provider account seen before opens the account it created.
 *   <li>Otherwise a new account is made, but only for an email the provider has verified.
 *   <li>If that email already has an account here, the sign-in is refused rather than merged.
 * </ol>
 *
 * <p>Why refuse instead of linking by email: this app doesn't verify emails at sign-up, so anyone can
 * register a password account under someone else's address. If a later Google sign-in for that
 * address joined the existing account, the person who registered it would still know its password
 * and could read everything the real owner logs ("pre-account takeover"). Refusing costs a little
 * convenience and closes that hole.
 */
@Service
public class OAuthAccountService {

    static final int MAX_EMAIL = 254;
    static final int MAX_NAME = 60;

    private final UserRepository users;
    private final OAuthIdentityRepository identities;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public OAuthAccountService(UserRepository users, OAuthIdentityRepository identities, TransactionTemplate transaction,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** Why a sign-in was refused, as a short code the login page turns into a sentence. */
    public static final class RefusedException extends RuntimeException {
        private final String code;

        RefusedException(String code) {
            super(code, null, false, false);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Returns the ID of the account this identity signs in to, creating it on first use. */
    public long signIn(OAuthIdentity identity) {
        if (identity.subject() == null) {
            throw new RefusedException("oauth");
        }
        Optional<Long> linked = identities.findUserId(identity.provider(), identity.subject());
        if (linked.isPresent()) {
            return linked.get();
        }
        if (!identity.emailVerified() || identity.email() == null) {
            throw new RefusedException("unverified-email");
        }
        String email = identity.email().trim().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL) {
            throw new RefusedException("oauth");
        }
        if (users.emailTaken(email)) {
            throw new RefusedException("email-in-use");
        }
        try {
            Long id = transaction.execute(status -> {
                User user = users.insert(email, displayName(identity.name(), email), null);
                identities.link(identity.provider(), identity.subject(), user.id(), clock.instant());
                return user.id();
            });
            return id;
        } catch (DuplicateKeyException race) {
            // The same person finished two sign-ins at once, or someone registered the email meanwhile.
            return identities.findUserId(identity.provider(), identity.subject())
                    .orElseThrow(() -> new RefusedException("email-in-use"));
        }
    }

    /** The provider's name for the person, trimmed to fit, or the email's local part. */
    static String displayName(String name, String email) {
        String chosen = name == null || name.isBlank() ? email.substring(0, Math.max(0, email.indexOf('@'))) : name.trim();
        if (chosen.codePointCount(0, chosen.length()) > MAX_NAME) {
            chosen = chosen.substring(0, chosen.offsetByCodePoints(0, MAX_NAME));
        }
        return chosen;
    }
}
