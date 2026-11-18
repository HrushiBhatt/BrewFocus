package io.github.hrushibhatt.brewfocus.timer;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import io.github.hrushibhatt.brewfocus.journal.FocusSession;
import io.github.hrushibhatt.brewfocus.journal.FocusSessionRepository;
import io.github.hrushibhatt.brewfocus.timer.TimerState.Phase;
import io.github.hrushibhatt.brewfocus.timer.TimerState.Status;

/**
 * One user's running or paused timer, in memory. Every field is read and written only while holding
 * {@link #lock}, by {@link TimerEngine}, so a pause and an expiring deadline that arrive at the same
 * moment are handled one after the other and can't race.
 */
final class ActiveTimer {

    final long userId;
    final ReentrantLock lock = new ReentrantLock();

    final Phase phase;
    final long durationMs;
    Status status;
    /** The deadline while running; null while paused. */
    Instant endsAt;
    /** Time left while paused. */
    long remainingMs;
    /** The alarm that fires at {@link #endsAt}; null while paused. */
    ScheduledFuture<?> alarm;
    /** Ended: kept only so a late alarm or command can tell. */
    boolean retired;

    // Focus blocks only; a break has no session row.
    final Long sessionId;
    final String mode;
    final String intention;
    final Long taskId;
    final String day;
    final Instant createdAt;
    int distractions;

    private ActiveTimer(long userId, Phase phase, long durationMs, Status status, Instant endsAt, long remainingMs,
            Long sessionId, String mode, String intention, Long taskId, String day, Instant createdAt, int distractions) {
        this.userId = userId;
        this.phase = phase;
        this.durationMs = durationMs;
        this.status = status;
        this.endsAt = endsAt;
        this.remainingMs = remainingMs;
        this.sessionId = sessionId;
        this.mode = mode;
        this.intention = intention;
        this.taskId = taskId;
        this.day = day;
        this.createdAt = createdAt;
        this.distractions = distractions;
    }

    static ActiveTimer focus(long userId, long sessionId, FocusRequest req, Instant endsAt, Instant now) {
        long ms = req.planned().toMillis();
        return new ActiveTimer(userId, Phase.FOCUS, ms, Status.RUNNING, endsAt, ms, sessionId, req.mode(),
                req.intention(), req.taskId(), req.day(), now, 0);
    }

    static ActiveTimer rest(long userId, long lengthMs, Instant endsAt) {
        return new ActiveTimer(userId, Phase.BREAK, lengthMs, Status.RUNNING, endsAt, lengthMs, null, null, null, null,
                null, null, 0);
    }

    /** A block that was running or paused when the server last stopped. */
    static ActiveTimer restored(FocusSessionRepository.Active row) {
        boolean paused = FocusSessionRepository.PAUSED.equals(row.state());
        Instant endsAt = paused ? null : (row.endsAt() != null ? row.endsAt() : row.createdAt());
        return new ActiveTimer(row.userId(), Phase.FOCUS, row.planned() * 1000L, paused ? Status.PAUSED : Status.RUNNING,
                endsAt, row.remainingMs(), row.id(), row.mode(), row.intention(), row.taskId(), row.day(),
                row.createdAt(), row.distractions());
    }

    boolean isFocus() {
        return phase == Phase.FOCUS;
    }

    /** Time left at {@code now}. */
    long remainingAt(Instant now) {
        if (status == Status.RUNNING) {
            return Math.max(0, endsAt.toEpochMilli() - now.toEpochMilli());
        }
        return remainingMs;
    }

    void cancelAlarm() {
        if (alarm != null) {
            alarm.cancel(false);
            alarm = null;
        }
    }

    TimerState snapshot(Instant now) {
        return new TimerState(phase, status, durationMs, remainingAt(now), status == Status.RUNNING ? endsAt : null,
                sessionId, mode, intention == null || intention.isEmpty() ? null : intention, taskId, now);
    }

    /** The journal entry this block becomes when it ends. */
    FocusSession toSession(boolean completed, int focusedSeconds) {
        return new FocusSession(sessionId, taskId, mode, (int) (durationMs / 1000), focusedSeconds, completed,
                intention == null ? "" : intention, "", distractions, day, createdAt);
    }
}
