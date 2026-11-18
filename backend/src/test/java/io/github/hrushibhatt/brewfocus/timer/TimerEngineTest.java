package io.github.hrushibhatt.brewfocus.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.events.LiveEvent;
import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository;
import io.github.hrushibhatt.brewfocus.support.EventRecorder;
import io.github.hrushibhatt.brewfocus.support.MutableClock;
import io.github.hrushibhatt.brewfocus.timer.TimerState.Status;

/**
 * The engine on its own, with the database mocked out. Deadline tests use the real clock and short
 * blocks; tests about elapsed time move a {@link MutableClock} instead of waiting.
 */
class TimerEngineTest {

    private static final Duration WAIT = Duration.ofSeconds(3);

    private final FocusSessionRepository sessions = mock(FocusSessionRepository.class);
    private final EventHub hub = new EventHub();
    private final AtomicLong ids = new AtomicLong();
    private TimerEngine engine;

    private TimerEngine start(Clock clock) {
        when(sessions.insertRunning(anyLong(), any(), anyString(), anyInt(), anyString(), anyString(), any(), any()))
                .thenAnswer(call -> ids.incrementAndGet());
        hub.start();
        engine = new TimerEngine(sessions, hub, clock);
        engine.start();
        return engine;
    }

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.stop();
        }
        hub.stop();
    }

    private static FocusRequest focus(Duration planned) {
        return new FocusRequest("Pomodoro", planned, "Ship it", null, "2026-11-18");
    }

    @Test
    void aFocusBlockCompletesOnItsDeadlineAndEarnsItsDecoration() throws Exception {
        start(Clock.systemUTC());
        when(sessions.countCompleted(1)).thenReturn(1);
        try (var tab = new EventRecorder(hub, 1)) {
            TimerState state = engine.startFocus(1, focus(Duration.ofMillis(200)));
            assertThat(state.status()).isEqualTo(Status.RUNNING);
            assertThat(state.endsAt()).isNotNull();

            LiveEvent done = tab.await("session.completed", WAIT);
            var completed = (TimerEngine.Completed) done.data();
            assertThat(completed.session().completed()).isTrue();
            assertThat(completed.session().intention()).isEqualTo("Ship it");
            assertThat(completed.unlocked()).isEqualTo("sprout");
        }
        verify(sessions).complete(eq(1L), eq(true), anyInt(), eq(0));
        assertThat(engine.current(1).status()).isEqualTo(Status.IDLE);
    }

    @Test
    void aPausedTimerDoesNotExpire() throws Exception {
        start(Clock.systemUTC());
        try (var tab = new EventRecorder(hub, 1)) {
            engine.startFocus(1, focus(Duration.ofMillis(300)));
            TimerState paused = engine.control(1, TimerAction.PAUSE);
            assertThat(paused.status()).isEqualTo(Status.PAUSED);
            assertThat(paused.endsAt()).isNull();

            assertThat(tab.sees("session.completed", Duration.ofMillis(600))).isFalse(); // well past the deadline
            assertThat(engine.current(1).remainingMs()).isPositive();

            engine.control(1, TimerAction.RESUME);
            tab.await("session.completed", WAIT);
        }
    }

    @Test
    void oneTimerPerUser() {
        start(Clock.systemUTC());
        engine.startFocus(1, focus(Duration.ofMinutes(25)));
        assertThatThrownBy(() -> engine.startBreak(1, Duration.ofMinutes(5)))
                .isInstanceOf(TimerConflictException.class)
                .hasMessage("a timer is already running")
                .satisfies(e -> assertThat(((TimerConflictException) e).state().status()).isEqualTo(Status.RUNNING));
        assertThat(engine.startFocus(2, focus(Duration.ofMinutes(25))).status()).isEqualTo(Status.RUNNING);
    }

    @Test
    void commandsThatDontFitAreConflicts() {
        start(Clock.systemUTC());
        assertThatThrownBy(() -> engine.control(1, TimerAction.PAUSE)).hasMessage("no timer is running");
        engine.startFocus(1, focus(Duration.ofMinutes(25)));
        assertThatThrownBy(() -> engine.control(1, TimerAction.RESUME))
                .hasMessage("that action doesn't apply to the timer right now");
    }

    @Test
    void finishingEarlyWithinTheFirstMinuteIsNotLogged() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-18T12:00:00Z"));
        start(clock);
        try (var tab = new EventRecorder(hub, 1)) {
            engine.startFocus(1, focus(Duration.ofMinutes(25)));
            clock.advance(Duration.ofSeconds(59));
            assertThat(engine.control(1, TimerAction.FINISH).status()).isEqualTo(Status.IDLE);
            tab.await("session.discarded", WAIT);
        }
        verify(sessions).delete(1L);
        verify(sessions, never()).complete(anyLong(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void finishingEarlyAfterAMinuteLogsTheTimeActuallyFocused() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-18T12:00:00Z"));
        start(clock);
        try (var tab = new EventRecorder(hub, 1)) {
            engine.startFocus(1, focus(Duration.ofMinutes(25)));
            clock.advance(Duration.ofMinutes(10));
            engine.control(1, TimerAction.PAUSE);
            clock.advance(Duration.ofHours(1)); // paused time doesn't count
            engine.control(1, TimerAction.RESUME);
            clock.advance(Duration.ofMinutes(2));
            engine.control(1, TimerAction.FINISH);

            var completed = (TimerEngine.Completed) tab.await("session.completed", WAIT).data();
            assertThat(completed.session().completed()).isFalse();
            assertThat(completed.session().focused()).isEqualTo(12 * 60);
            assertThat(completed.unlocked()).isNull(); // only full brews furnish the café
        }
        verify(sessions).complete(1L, false, 12 * 60, 0);
    }

    @Test
    void distractionsAreCountedOnTheSessionWithoutABroadcast() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-18T12:00:00Z"));
        start(clock);
        engine.startFocus(1, focus(Duration.ofMinutes(25)));
        try (var tab = new EventRecorder(hub, 1)) {
            engine.control(1, TimerAction.DISTRACTION);
            engine.control(1, TimerAction.DISTRACTION);
            assertThat(tab.sees("timer", Duration.ofMillis(200))).isFalse();
        }
        verify(sessions).saveProgress(eq(1L), eq("running"), any(), anyLong(), eq(2));
        clock.advance(Duration.ofMinutes(5));
        engine.control(1, TimerAction.FINISH);
        verify(sessions).complete(1L, false, 300, 2);
    }

    @Test
    void cancellingThrowsTheBlockAway() {
        start(Clock.systemUTC());
        engine.startFocus(1, focus(Duration.ofMinutes(25)));
        assertThat(engine.control(1, TimerAction.CANCEL).status()).isEqualTo(Status.IDLE);
        verify(sessions).delete(1L);
        assertThat(engine.activeTimers()).isZero();
    }

    @Test
    void breaksAreAnnouncedButNeverStored() throws Exception {
        start(Clock.systemUTC());
        try (var tab = new EventRecorder(hub, 1)) {
            engine.startBreak(1, Duration.ofMillis(100));
            LiveEvent over = tab.await("break.completed", WAIT);
            assertThat(over.data()).isEqualTo(Map.of("completed", true));
        }
        verify(sessions, never()).insertRunning(anyLong(), any(), anyString(), anyInt(), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> engine.control(1, TimerAction.DISTRACTION)).isInstanceOf(TimerConflictException.class);
    }

    @Test
    void restoreResumesTimersAfterARestart() {
        Instant now = Instant.now();
        when(sessions.findActive()).thenReturn(List.of(
                // Its deadline passed while the server was down: it completes at once, fully credited.
                new FocusSessionRepository.Active(7, 1, null, "Pomodoro", 1500, "", "2026-11-18", "running",
                        now.minusSeconds(60), 0, 0, now.minusSeconds(1560)),
                // Paused stays paused, with its remaining time intact.
                new FocusSessionRepository.Active(8, 2, null, "Deep Work", 5400, "Thesis", "2026-11-18", "paused",
                        null, 5_000, 1, now.minusSeconds(600))));

        start(Clock.systemUTC());

        verify(sessions, timeout(WAIT.toMillis())).complete(7L, true, 1500, 0);
        TimerState paused = engine.current(2);
        assertThat(paused.status()).isEqualTo(Status.PAUSED);
        assertThat(paused.remainingMs()).isEqualTo(5_000);
        assertThat(paused.intention()).isEqualTo("Thesis");
    }

    @Test
    void shutdownStopsTimersWithoutLosingThem() throws Exception {
        start(Clock.systemUTC());
        engine.startFocus(1, focus(Duration.ofMillis(300)));
        engine.stop();
        assertThat(engine.isRunning()).isFalse();
        Thread.sleep(500);
        // Nothing ended it: the row is still "running", for the next start to pick up.
        verify(sessions, never()).complete(anyLong(), anyBoolean(), anyInt(), anyInt());
        verify(sessions, never()).delete(anyLong());
    }

    @Test
    void manyUsersTimersRunAndFinishAtOnce() throws Exception {
        start(Clock.systemUTC());
        int users = 300;
        List<EventRecorder> tabs = new ArrayList<>();
        for (long user = 1; user <= users; user++) {
            tabs.add(new EventRecorder(hub, user));
        }
        List<Thread> threads = new ArrayList<>();
        for (long user = 1; user <= users; user++) {
            long id = user;
            threads.add(Thread.ofVirtual().start(() -> {
                engine.startFocus(id, focus(Duration.ofMillis(50 + id % 50)));
                for (int i = 0; i < 5; i++) {
                    try {
                        engine.control(id, TimerAction.DISTRACTION); // commands race the deadline
                    } catch (TimerConflictException alreadyDone) {
                        break;
                    }
                }
            }));
        }
        for (Thread t : threads) {
            t.join();
        }
        for (EventRecorder tab : tabs) {
            tab.await("session.completed", WAIT); // every block finished, exactly as planned
            tab.close();
        }
        assertThat(engine.activeTimers()).isZero();
        verify(sessions, never()).delete(anyLong());
        verify(sessions, never()).complete(anyLong(), eq(false), anyInt(), anyInt());
    }
}
