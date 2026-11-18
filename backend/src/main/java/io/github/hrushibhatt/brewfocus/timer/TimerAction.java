package io.github.hrushibhatt.brewfocus.timer;

import java.util.Optional;

/** A command a client sends to its running timer: {@code POST /api/timer/{action}}. */
public enum TimerAction {
    PAUSE,
    RESUME,
    /** End focus early (logged if it ran at least a minute), or skip a break. */
    FINISH,
    /** Abandon the block without logging it. */
    CANCEL,
    /** Strict mode: the user left the tab mid-focus. */
    DISTRACTION;

    /** The action a path segment names, such as "pause". */
    public static Optional<TimerAction> parse(String name) {
        for (TimerAction action : values()) {
            if (action.name().equalsIgnoreCase(name)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }
}
