import { useEffect } from 'react';
import { formatClock } from '../lib/time';
import { tick } from '../sound/audio';
import { remainingMs } from '../state/timer';
import { brew, toasts, useAuth, useSettings, useTimer, useTimerView } from '../state/services';

/**
 * The side effects of a running session that live outside any one page: the live stream's lifecycle,
 * the ticking clock, strict mode, and the browser tab's countdown title and draining favicon.
 */
export function useBrewEffects() {
  useSessionLifecycle();
  useTickingClock();
  useStrictMode();
  useLiveTab();
}

/** Signing in opens the live stream and loads the account; signing out tears it down. */
function useSessionLifecycle() {
  const userId = useAuth((s) => s.user?.id);
  useEffect(() => {
    if (userId) void brew.getState().start();
    else brew.getState().stop();
  }, [userId]);
}

/** Optional ticking for a little pressure; the final minute ticks louder. Runs once per second. */
function useTickingClock() {
  const second = useTimer((s) => Math.ceil(remainingMs(s) / 1000));
  const status = useTimer((s) => s.state.status);
  const phase = useTimer((s) => s.state.phase);
  const { tick: style } = useSettings();
  useEffect(() => {
    if (status === 'running' && phase === 'focus' && style !== 'off') tick(style, second, second <= 60);
    // Only a new second should tick, not a change of settings mid-second.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [second]);
}

/** Strict mode: every tab switch mid-focus is logged on the server, and closing the tab asks first. */
function useStrictMode() {
  const { strict } = useSettings();
  const active = useTimer((s) => strict && s.state.status === 'running' && s.state.phase === 'focus');
  useEffect(() => {
    if (!active) return undefined;
    const onVisibility = () => {
      if (document.hidden) brew.getState().distracted();
      else toasts.getState().show(`Welcome back. Distraction #${brew.getState().distractions} logged.`);
    };
    const onLeave = (e) => e.preventDefault(); // "Leave site?"
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('beforeunload', onLeave);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('beforeunload', onLeave);
    };
  }, [active]);
}

/** The web's answer to a lock-screen widget: a counting-down title and a draining favicon. */
function useLiveTab() {
  const { state, display, phase, fill } = useTimerView();
  const title = state.status === 'idle' ? 'Brew Focus' : `${formatClock(display.remainingMs / 1000)} · ${phase === 'focus' ? 'Focus' : 'Break'}`;
  const level = Math.round(fill * 10);

  useEffect(() => {
    document.title = title;
  }, [title]);

  useEffect(() => {
    const link = document.querySelector("link[rel~='icon']");
    if (link) link.href = mugIcon(level);
  }, [level]);
}

/** A 16×16 pixel mug whose coffee level (0–10 rows) mirrors the timer. */
function mugIcon(level) {
  const fallback = `${import.meta.env.BASE_URL}favicon.svg`;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 32;
  let g;
  try {
    g = canvas.getContext('2d');
  } catch {
    return fallback; // no canvas (test runners)
  }
  if (!g) return fallback;
  g.scale(2, 2);
  const px = (color, x, y, w, h) => {
    g.fillStyle = color;
    g.fillRect(x, y, w, h);
  };
  px('#2A2A2A', 1, 3, 12, 12); // outline
  px('#FFFFFF', 2, 4, 10, 10); // cup
  if (level > 0) {
    px('#6F4E37', 2, 14 - level, 10, level); // coffee
    px('#A67C52', 2, 14 - level, 10, 1); // surface
  }
  px('#2A2A2A', 13, 5, 3, 7); // handle
  g.clearRect(13, 7, 1, 3);
  return canvas.toDataURL();
}
