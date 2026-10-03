package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.user.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits failed logins (in memory; single backend instance): at most {@value #MAX_FAILURES_PER_USERNAME} failures per
 * username and {@value #MAX_FAILURES_PER_ADDRESS} per client address within {@link #WINDOW}. Further attempts are
 * rejected without checking the password until the oldest failure leaves the window.
 * <p>
 * Trade-off: someone who knows a username can temporarily block its logins by failing on purpose. That is acceptable
 * for an application that is only reachable on the local network.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return how long logins are still blocked, or empty if a login attempt is allowed
     */
    public Optional<Duration> blockedFor(String username, String address) {
        Instant now = clock.instant();
        return longest(blockedFor(usernameKey(username), MAX_FAILURES_PER_USERNAME, now),
                blockedFor(addressKey(address), MAX_FAILURES_PER_ADDRESS, now));
    }

    public void recordFailure(String username, String address) {
        Instant now = clock.instant();
        record(usernameKey(username), now);
        record(addressKey(address), now);
    }

    /** A successful login resets the failures of the username (not of the address). */
    public void recordSuccess(String username) {
        failures.remove(usernameKey(username));
    }

    /** Forgets all failures (used by tests). */
    void clear() {
        failures.clear();
    }

    private Optional<Duration> blockedFor(String key, int maxFailures, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return Optional.empty();
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < maxFailures) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(now, attempts.peekFirst().plus(WINDOW)));
        }
    }

    private void record(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
        // Drop keys without recent failures, so the map does not grow indefinitely
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> attempts, Instant now) {
        while (!attempts.isEmpty() && !attempts.peekFirst().plus(WINDOW).isAfter(now)) {
            attempts.removeFirst();
        }
    }

    private static Optional<Duration> longest(Optional<Duration> first, Optional<Duration> second) {
        if (first.isEmpty()) {
            return second;
        }
        return second.filter(duration -> duration.compareTo(first.get()) > 0).or(() -> first);
    }

    private static String usernameKey(String username) {
        return "user:" + User.normalizeUsername(username == null ? "" : username);
    }

    private static String addressKey(String address) {
        return "address:" + address;
    }
}
