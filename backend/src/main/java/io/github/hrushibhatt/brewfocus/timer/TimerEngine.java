package io.github.hrushibhatt.brewfocus.timer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.hrushibhatt.brewfocus.events.EventHub;
import io.github.hrushibhatt.brewfocus.events.LiveEvent;
import io.github.hrushibhatt.brewfocus.journal.FocusSession;
import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository;
import io.github.hrushibhatt.brewfocus.timer.TimerState.Phase;
import io.github.hrushibhatt.brewfocus.timer.TimerState.Status;

/**
 * Runs focus and break timers on the server, so a block keeps going when the browser closes and every
 * device sees the same clock. Each user has at most one timer.
 *
 * <p>Design decisions:
 *
 * <ul>
 *   <li>A timer is a deadline, not a counter. It stores when it ends, so a slow request, a busy CPU or
 *       a restart can't lose time; the countdown anyone sees is worked out from the deadline.
 *   <li>One lock per timer. Commands (pause, resume, finish...) and the timer's own alarm both take it,
 *       so a pause and an expiring deadline that arrive together are applied one at a time. Different
 *       users' timers never wait on each other. It's a {@link ReentrantLock} rather than
 *       {@code synchronized} so a virtual thread blocked on the database inside it doesn't pin its
 *       carrier thread on Java 21.
 *   <li>One scheduler thread keeps time for every timer, and each expiry then runs on its own virtual
 *       thread, so a slow database write for one user can't delay anyone else's deadline.
 *   <li>Every change is written to the database as it happens, and {@link #start()} picks running and
 *       paused blocks back up, so a restart or deploy loses nothing. A block whose deadline passed while
 *       the server was down completes at once: the time really elapsed, so the full block is credited.
 * </ul>
 */
@Component
public class TimerEngine implements SmartLifecycle {

    /** The shortest focus block worth recording when it's ended early. */
    public static final Duration MIN_LOGGED = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(TimerEngine.class);

    private final FocusSessionRepository sessions;
    private final EventHub hub;
    private final Clock clock;
    private final Map<Long, ActiveTimer> timers = new ConcurrentHashMap<>();
    /** Guards starting a timer: the check for an existing one and registering the new one. */
    private final ReentrantLock starting = new ReentrantLock();
    private volatile ScheduledExecutorService alarms;
    private volatile boolean running;

    public TimerEngine(FocusSessionRepository sessions, EventHub hub, Clock clock) {
        this.sessions = sessions;
        this.hub = hub;
        this.clock = clock;
    }

    /** What {@code session.completed} carries: the logged block, and any café decoration it earned. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Completed(@JsonProperty("session") FocusSession session, @JsonProperty("unlocked") String unlocked) {}

    // ---- commands ---------------------------------------------------------------------------------

    /** Begins a focus block, recording it at once so it survives a restart. */
    public TimerState startFocus(long userId, FocusRequest request) {
        starting.lock();
        try {
            requireIdle(userId);
            Instant now = clock.instant();
            Instant endsAt = now.plus(request.planned());
            long id = sessions.insertRunning(userId, request.taskId(), request.mode(),
                    (int) request.planned().toSeconds(), request.intention(), request.day(), endsAt, now);
            return register(ActiveTimer.focus(userId, id, request, endsAt, now));
        } finally {
            starting.unlock();
        }
    }

    /** Begins a break. Breaks live in memory only and aren't logged. */
    public TimerState startBreak(long userId, Duration length) {
        starting.lock();
        try {
            requireIdle(userId);
            Instant endsAt = clock.instant().plus(length);
            return register(ActiveTimer.rest(userId, length.toMillis(), endsAt));
        } finally {
            starting.unlock();
        }
    }

    /** Applies an action to the user's timer and returns the resulting state. */
    public TimerState control(long userId, TimerAction action) {
        ActiveTimer timer = timers.get(userId);
        if (timer == null) {
            throw new TimerConflictException(TimerConflictException.NO_TIMER, TimerState.idle(Phase.FOCUS, clock.instant()));
        }
        timer.lock.lock();
        try {
            if (timer.retired) { // it finished while we were looking it up
                throw new TimerConflictException(TimerConflictException.NO_TIMER,
                        TimerState.idle(Phase.FOCUS, clock.instant()));
            }
            return apply(timer, action);
        } finally {
            timer.lock.unlock();
        }
    }

    /** The user's timer right now, or an idle state if none is running. */
    public TimerState current(long userId) {
        ActiveTimer timer = timers.get(userId);
        if (timer != null) {
            timer.lock.lock();
            try {
                if (!timer.retired) {
                    return timer.snapshot(clock.instant());
                }
            } finally {
                timer.lock.unlock();
            }
        }
        return TimerState.idle(Phase.FOCUS, clock.instant());
    }

    /** How many timers are running or paused, across all users. */
    public int activeTimers() {
        return timers.size();
    }

    // ---- the state machine (always called holding timer.lock) ---------------------------------------

    private TimerState apply(ActiveTimer timer, TimerAction action) {
        Instant now = clock.instant();
        switch (action) {
            case PAUSE -> {
                if (timer.status != Status.RUNNING) {
                    throw badAction(timer, now);
                }
                timer.remainingMs = timer.remainingAt(now);
                timer.endsAt = null;
                timer.status = Status.PAUSED;
                timer.cancelAlarm();
            }
            case RESUME -> {
                if (timer.status != Status.PAUSED) {
                    throw badAction(timer, now);
                }
                timer.endsAt = now.plusMillis(timer.remainingMs);
                timer.status = Status.RUNNING;
                arm(timer, now);
            }
            case DISTRACTION -> {
                if (!timer.isFocus()) {
                    throw badAction(timer, now);
                }
                timer.distractions++;
                persist(timer);
                return timer.snapshot(now); // nothing visible changed, so nothing to broadcast
            }
            case FINISH -> {
                return end(timer, false);
            }
            case CANCEL -> {
                if (timer.isFocus()) {
                    quietly(() -> sessions.delete(timer.sessionId));
                }
                return retire(timer);
            }
        }
        persist(timer);
        TimerState state = timer.snapshot(now);
        hub.publish(timer.userId, "timer", state);
        return state;
    }

    /**
     * Ends a timer. A focus block is logged and announced, or discarded if it was ended early within
     * its first minute. {@code completed} means it ran all the way to zero.
     */
    private TimerState end(ActiveTimer timer, boolean completed) {
        Instant now = clock.instant();
        timer.cancelAlarm();
        LiveEvent announce;
        if (!timer.isFocus()) {
            announce = new LiveEvent("break.completed", Map.of("completed", completed));
        } else {
            long elapsedMs = timer.durationMs - timer.remainingAt(now);
            if (!completed && elapsedMs < MIN_LOGGED.toMillis()) {
                quietly(() -> sessions.delete(timer.sessionId));
                announce = new LiveEvent("session.discarded", null);
            } else {
                int focused = (int) Math.round(elapsedMs / 1000.0);
                quietly(() -> sessions.complete(timer.sessionId, completed, focused, timer.distractions));
                String unlocked = completed ? unlocked(timer.userId) : null;
                announce = new LiveEvent("session.completed", new Completed(timer.toSession(completed, focused), unlocked));
            }
        }
        TimerState idle = retire(timer); // free the slot first, so the client can start its break at once
        hub.publish(timer.userId, announce);
        return idle;
    }

    /** Unregisters a timer and tells the user's clients they're idle. */
    private TimerState retire(ActiveTimer timer) {
        timer.retired = true;
        timer.cancelAlarm();
        timers.remove(timer.userId, timer);
        TimerState idle = TimerState.idle(timer.phase, clock.instant());
        hub.publish(timer.userId, "timer", idle);
        return idle;
    }

    /** Fired by the alarm. The deadline is checked again, since a pause or resume may have moved it. */
    private void expire(ActiveTimer timer) {
        timer.lock.lock();
        try {
            if (timer.retired || timer.status != Status.RUNNING) {
                return;
            }
            Instant now = clock.instant();
            if (now.isBefore(timer.endsAt)) {
                arm(timer, now); // woke early: the deadline moved
                return;
            }
            end(timer, true);
        } catch (RuntimeException e) {
            log.error("timer for user {} failed to finish", timer.userId, e);
        } finally {
            timer.lock.unlock();
        }
    }

    // ---- plumbing ---------------------------------------------------------------------------------------

    /** Throws unless the user has no live timer. One that's just ending counts as gone. */
    private void requireIdle(long userId) {
        ActiveTimer existing = timers.get(userId);
        if (existing == null) {
            return;
        }
        existing.lock.lock();
        try {
            if (!existing.retired) {
                throw new TimerConflictException(TimerConflictException.BUSY, existing.snapshot(clock.instant()));
            }
        } finally {
            existing.lock.unlock();
        }
    }

    /** Makes a new timer live: registered, its alarm set, and announced. Called holding {@link #starting}. */
    private TimerState register(ActiveTimer timer) {
        timer.lock.lock(); // nobody else can hold a brand-new timer's lock, so this never waits
        try {
            Instant now = clock.instant();
            timers.put(timer.userId, timer);
            arm(timer, now);
            TimerState state = timer.snapshot(now);
            hub.publish(timer.userId, "timer", state);
            return state;
        } finally {
            timer.lock.unlock();
        }
    }

    /** Sets the alarm for a running timer's deadline. A deadline already past fires at once. */
    private void arm(ActiveTimer timer, Instant now) {
        timer.cancelAlarm();
        ScheduledExecutorService scheduler = alarms;
        if (timer.status != Status.RUNNING || scheduler == null || scheduler.isShutdown()) {
            return;
        }
        long delay = Math.max(0, timer.endsAt.toEpochMilli() - now.toEpochMilli());
        timer.alarm = scheduler.schedule(
                () -> Thread.ofVirtual().name("timer-" + timer.userId).start(() -> expire(timer)),
                delay, TimeUnit.MILLISECONDS);
    }

    /** Saves a focus block's live state so {@link #start()} can resume it after a restart. */
    private void persist(ActiveTimer timer) {
        if (timer.isFocus()) {
            String state = timer.status == Status.PAUSED ? FocusSessionRepository.PAUSED : FocusSessionRepository.RUNNING;
            quietly(() -> sessions.saveProgress(timer.sessionId, state, timer.endsAt, timer.remainingMs, timer.distractions));
        }
    }

    /**
     * Runs a database write whose failure shouldn't undo what the user just did. The timer in memory
     * stays right, and the failure is logged; at worst a restart resumes from the last saved state.
     */
    private static void quietly(Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("couldn't save a timer change", e);
        }
    }

    /** The café decoration this full brew earned, if any. A failed lookup only costs the surprise. */
    private String unlocked(long userId) {
        try {
            return Cafe.unlockedAt(sessions.countCompleted(userId));
        } catch (RuntimeException e) {
            log.error("couldn't count full brews", e);
            return null;
        }
    }

    private TimerConflictException badAction(ActiveTimer timer, Instant now) {
        return new TimerConflictException(TimerConflictException.BAD_ACTION, timer.snapshot(now));
    }

    // ---- lifecycle -------------------------------------------------------------------------------------

    /** Starts the alarm thread and resumes every block that was running or paused at the last stop. */
    @Override
    public void start() {
        alarms = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("timer-alarms").daemon().factory());
        var rows = sessions.findActive();
        starting.lock();
        try {
            for (var row : rows) {
                register(ActiveTimer.restored(row));
            }
        } finally {
            starting.unlock();
        }
        running = true;
        log.info("restored {} timer(s)", rows.size());
    }

    /** Stops keeping time. Every timer's state is already saved, so the next start resumes it. */
    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService scheduler = alarms;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Starts before the web server accepts requests, and stops after it has finished them. */
    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 4096;
    }
}
