package com.github.kevinldg.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    /** A clock the test can move forward. */
    private static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void blocksUsernameAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }
        assertThat(throttle.blockedFor("alice", "10.0.0.1")).isEmpty();

        throttle.recordFailure("Alice", "10.0.0.2");

        assertThat(throttle.blockedFor("ALICE", "10.0.0.3")).contains(Duration.ofMinutes(15));
        assertThat(throttle.blockedFor("bob", "10.0.0.1")).isEmpty();
    }

    @Test
    void blockEndsWhenOldestFailureLeavesTheWindow() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(throttle.blockedFor("alice", "10.0.0.1")).contains(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(10));

        assertThat(throttle.blockedFor("alice", "10.0.0.1")).isEmpty();
    }

    @Test
    void blocksAddressAfterTwentyFailuresAcrossUsernames() {
        for (int i = 0; i < 20; i++) {
            throttle.recordFailure("user" + i, "10.0.0.9");
        }

        assertThat(throttle.blockedFor("someone-else", "10.0.0.9")).isPresent();
        assertThat(throttle.blockedFor("someone-else", "10.0.0.8")).isEmpty();
    }

    @Test
    void successResetsTheUsername() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }

        throttle.recordSuccess("alice");

        assertThat(throttle.blockedFor("alice", "10.0.0.2")).isEmpty();
    }
}
