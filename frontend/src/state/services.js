import { environment } from '@env';
import { useMemo } from 'react';
import { useStore } from 'zustand';
import { api, setUnauthorizedHandler } from '../api/api';
import { createLiveEvents } from '../api/live';
import { createAmbienceStore } from './ambience';
import { createAuthStore } from './auth';
import { createBrewStore, settingsOf } from './brew';
import { allModes, findMode } from './modes';
import { createThemeStore } from './theme';
import { createTimerStore } from './timer';
import { createToastStore } from './toasts';
import { displayOf, fillOf, isBusy, phaseOf, progressOf } from './view';

/**
 * The app's stores, wired together once. Zustand stores live outside React, which suits this app:
 * most updates arrive from the server's event stream, not from a component, and the timer has to
 * keep ticking whichever page is open. Components subscribe with the hooks below and re-render only
 * when what they selected changes.
 */
export const auth = createAuthStore(api);
export const timer = createTimerStore(api);
export const toasts = createToastStore();
export const theme = createThemeStore();
export const ambience = createAmbienceStore();
export const live = createLiveEvents({
  me: api.me,
  onSessionEnded: () => auth.getState().expire(),
  localEvents: environment.localEvents,
});
export const brew = createBrewStore({ api, auth, timer, toasts, live });

// A 401 on any request while signed in means the session ended elsewhere: back to sign-in.
setUnauthorizedHandler(() => {
  if (auth.getState().user) auth.getState().expire();
});

// ---- hooks ------------------------------------------------------------------------------------------

export const useAuth = (select) => useStore(auth, select);
export const useBrew = (select) => useStore(brew, select);
export const useTimer = (select) => useStore(timer, select);
export const useToasts = (select) => useStore(toasts, select);
export const useTheme = (select) => useStore(theme, select);
export const useAmbience = (select) => useStore(ambience, select);
export const useLive = (select) => useStore(live.status, select);

/** The signed-in user's settings, or the defaults. */
export const useSettings = () => settingsOf(useAuth((s) => s.user));

/** Every mode on offer, and the selected one. */
export function useModes() {
  const presets = useBrew((s) => s.presets);
  const modeKey = useBrew((s) => s.modeKey);
  return useMemo(() => ({ modes: allModes(presets), mode: findMode(presets, modeKey) }), [presets, modeKey]);
}

/** Everything the timer screen derives: phase, clock, progress and the mug's fill. */
export function useTimerView() {
  const timerState = useTimer((s) => s);
  const reflecting = useBrew((s) => s.reflecting);
  const pendingBreak = useBrew((s) => s.pendingBreak);
  const { mode } = useModes();
  const phase = phaseOf(timerState.state, pendingBreak);
  const display = displayOf({ timer: timerState, reflecting, pendingBreak, mode });
  const progress = progressOf(display);
  return {
    state: timerState.state,
    phase,
    display,
    progress,
    fill: fillOf(phase, progress),
    busy: isBusy(timerState.state, pendingBreak, reflecting),
    intention: timerState.state.intention ?? '',
    mode,
  };
}

export const browserOnly = environment.browserBackend;
