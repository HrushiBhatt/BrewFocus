package io.github.hrushibhatt.brewfocus.timer;

import java.time.Duration;

/**
 * A focus block to start.
 *
 * @param mode      the timing mode's name, such as "Pomodoro"
 * @param planned   how long the block should run
 * @param intention what the block is for; may be empty
 * @param taskId    the task it's for, or null
 * @param day       the user's local calendar day (YYYY-MM-DD)
 */
public record FocusRequest(String mode, Duration planned, String intention, Long taskId, String day) {}
