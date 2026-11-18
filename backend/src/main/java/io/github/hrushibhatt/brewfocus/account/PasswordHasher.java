package io.github.hrushibhatt.brewfocus.account;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.hrushibhatt.brewfocus.config.BrewFocusProperties;

/**
 * bcrypt for passwords: deliberately slow, salted, and its cost can rise over time.
 *
 * <ul>
 *   <li>Hashing is bounded to one at a time per CPU core, so a burst of sign-ins queues instead of
 *       spending every core and stalling the timers and event streams.
 *   <li>An unknown email is checked against a dummy hash, so a sign-in takes as long whether or not
 *       the account exists, and response times don't reveal which emails are registered.
 * </ul>
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder bcrypt;
    private final Semaphore slots = new Semaphore(Runtime.getRuntime().availableProcessors());
    private volatile String dummyHash;

    public PasswordHasher(BrewFocusProperties props) {
        this.bcrypt = new BCryptPasswordEncoder(props.bcryptCost());
    }

    /** Hashes a password already checked by {@link PasswordPolicy}, which keeps it within bcrypt's 72 bytes. */
    public String hash(String password) {
        return bounded(() -> bcrypt.encode(password));
    }

    /**
     * Reports whether {@code password} matches {@code hash}. A missing hash (no such user, or an OAuth
     * account) never matches, and takes just as long to say so.
     */
    public boolean matches(String hash, String password) {
        boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES;
        if (hash == null || hash.isEmpty() || tooLong) {
            bounded(() -> bcrypt.matches("no such user", dummyHash()));
            return false;
        }
        return bounded(() -> bcrypt.matches(password, hash));
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = dummyHash = bcrypt.encode("no such user");
        }
        return hash;
    }

    private <T> T bounded(Supplier<T> work) {
        slots.acquireUninterruptibly();
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }
}
