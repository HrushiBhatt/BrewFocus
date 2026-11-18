package io.github.hrushibhatt.brewfocus.events;

/**
 * One server-sent event, such as {@code timer} with the timer's state, or {@code changed} with
 * {@code {"kind": "tasks"}}. {@code data} is serialized as JSON; null is sent as {@code null}.
 */
public record LiveEvent(String type, Object data) {}
