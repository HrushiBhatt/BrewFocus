import { remainingMs } from './timer';

/**
 * What the timer screen shows, worked out from the stores' raw state. Pure functions, so the rules
 * (when the mug is full, what "busy" means) are tested without rendering anything.
 */

/** Focus or break. Idle shows a waiting break as a break, so the mug can refill on it. */
export function phaseOf(timerState, pendingBreak) {
  if (timerState.status !== 'idle') return timerState.phase;
  return pendingBreak ? 'break' : 'focus';
}

/** Mid-session: a timer is going, a break is waiting, or a reflection is open. Modes can't change. */
export function isBusy(timerState, pendingBreak, reflecting) {
  return timerState.status !== 'idle' || pendingBreak || Boolean(reflecting);
}

/**
 * The clock's numbers. A running timer shows its own; otherwise the selected mode's full length, or an
 * empty mug while a just-finished block waits for its reflection.
 */
export function displayOf({ timer, reflecting, pendingBreak, mode }) {
  const s = timer.state;
  if (s.status !== 'idle') return { durationMs: s.duration_ms, remainingMs: remainingMs(timer) };
  if (reflecting) return { durationMs: 1, remainingMs: 0 }; // just drained the mug
  const ms = (pendingBreak ? mode.rest : mode.focus) * 60_000;
  return { durationMs: ms, remainingMs: ms };
}

/** How far through the block, from 0 to 1. */
export function progressOf({ durationMs, remainingMs: left }) {
  return durationMs ? 1 - left / durationMs : 0;
}

/** Coffee drains while focusing and refills on a break. */
export function fillOf(phase, progress) {
  return phase === 'focus' ? 1 - progress : progress;
}
