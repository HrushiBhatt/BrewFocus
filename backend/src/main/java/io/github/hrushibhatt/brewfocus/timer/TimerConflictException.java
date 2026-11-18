package io.github.hrushibhatt.brewfocus.timer;

/**
 * A command that doesn't fit the timer's current state: starting a second timer, controlling one that
 * isn't running, or pausing one that's already paused. It carries the state the timer is actually in,
 * so a client that was out of date can catch up from the reply.
 */
public class TimerConflictException extends RuntimeException {

    static final String BUSY = "a timer is already running";
    static final String NO_TIMER = "no timer is running";
    static final String BAD_ACTION = "that action doesn't apply to the timer right now";

    private final transient TimerState state;

    TimerConflictException(String message, TimerState state) {
        super(message, null, false, false);
        this.state = state;
    }

    public TimerState state() {
        return state;
    }
}
