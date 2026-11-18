import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeApi, fakeLive } from '../test/fakes';
import { createAuthStore } from './auth';
import { createBrewStore } from './brew';
import { findMode } from './modes';
import { createTimerStore } from './timer';
import { createToastStore } from './toasts';
import { displayOf, fillOf, isBusy, phaseOf, progressOf } from './view';

vi.mock('../sound/audio', () => ({ audio: vi.fn(), chime: vi.fn(), tick: vi.fn() }));

const now = () => new Date().toISOString();
const idle = () => ({ phase: 'focus', status: 'idle', duration_ms: 0, remaining_ms: 0, server_time: now() });

const completed = {
  session: {
    id: 7, task_id: null, mode: 'Pomodoro', planned: 1500, focused: 1500, completed: true, intention: 'Ship it',
    reflection: '', distractions: 0, day: '2026-09-30', created_at: now(),
  },
  unlocked: 'sprout',
};

describe('brew store: the session flow', () => {
  let api;
  let live;
  let auth;
  let timer;
  let toasts;
  let brew;

  /** What the timer screen would show right now. */
  const view = () => {
    const b = brew.getState();
    const mode = findMode(b.presets, b.modeKey);
    const phase = phaseOf(timer.getState().state, b.pendingBreak);
    const display = displayOf({ timer: timer.getState(), reflecting: b.reflecting, pendingBreak: b.pendingBreak, mode });
    return { phase, display, fill: fillOf(phase, progressOf(display)), busy: isBusy(timer.getState().state, b.pendingBreak, b.reflecting) };
  };

  beforeEach(() => {
    api = fakeApi();
    live = fakeLive();
    auth = createAuthStore(api);
    timer = createTimerStore(api);
    toasts = createToastStore();
    brew = createBrewStore({ api, auth, timer, toasts, live });
  });

  it('shows the selected mode, full, when idle', () => {
    expect(view().phase).toBe('focus');
    expect(view().display).toEqual({ durationMs: 25 * 60_000, remainingMs: 25 * 60_000 });
    expect(view().fill).toBe(1);
  });

  it('opens the reflection prompt when the server completes a block, with an empty mug', () => {
    live.emit({ type: 'session.completed', data: completed });
    expect(brew.getState().dialog).toBe('reflect');
    expect(brew.getState().reflecting.unlocked).toBe('sprout');
    expect(view().fill).toBe(0);
    expect(view().busy).toBe(true);
  });

  it('closes the prompt when another device saved the reflection', () => {
    live.emit({ type: 'session.completed', data: completed });
    live.emit({ type: 'session.reflected', data: { id: 7 } });
    expect(brew.getState().dialog).toBeNull();
    expect(brew.getState().reflecting).toBeNull();
  });

  it('follows a break started elsewhere: the phase flips and the mug refills', () => {
    live.emit({ type: 'session.completed', data: completed });
    live.emit({
      type: 'timer',
      data: { ...idle(), phase: 'break', status: 'running', duration_ms: 300_000, remaining_ms: 300_000, ends_at: new Date(Date.now() + 300_000).toISOString() },
    });
    expect(view().phase).toBe('break');
    expect(brew.getState().reflecting).toBeNull(); // another device moved on
    expect(view().fill).toBeCloseTo(0, 2); // a fresh break starts empty and refills
    timer.getState().reset();
  });

  it("doesn't duplicate a new mode when the live refresh lands before the save's response", async () => {
    const preset = { id: 1, name: 'Thesis', focus: 75, rest: 15 };
    let finishSave;
    api.createPreset.mockReturnValue(new Promise((resolve) => (finishSave = resolve)));
    api.presets.mockResolvedValue([preset]);

    const creating = brew.getState().createMode('Thesis', 75, 15);
    live.emit({ type: 'changed', data: { kind: 'presets' } }); // the server announces it first
    await vi.waitFor(() => expect(brew.getState().presets).toEqual([preset]));
    finishSave(preset);
    await creating;

    expect(brew.getState().presets).toEqual([preset]);
    expect(brew.getState().modeKey).toBe('custom-1');
  });

  it('waits for the play button when auto-start breaks is off', async () => {
    auth.getState().setUser({ id: 1, email: 'a@b.co', name: 'A', settings: { ...quietSettings(), autoBreak: false } });
    live.emit({ type: 'session.completed', data: completed });
    await brew.getState().finishReflection('Shipped it');

    expect(api.reflect).toHaveBeenCalledWith(7, 'Shipped it');
    expect(api.startBreak).not.toHaveBeenCalled();
    expect(brew.getState().pendingBreak).toBe(true);
    expect(view().phase).toBe('break');

    brew.getState().skip();
    expect(brew.getState().pendingBreak).toBe(false);
    expect(view().phase).toBe('focus');
  });

  it('starts the break by itself when auto-start is on', async () => {
    api.startBreak.mockResolvedValue({ ...idle(), phase: 'break', status: 'running', duration_ms: 300_000, remaining_ms: 300_000 });
    live.emit({ type: 'session.completed', data: completed });
    await brew.getState().finishReflection('');
    expect(api.reflect).not.toHaveBeenCalled(); // nothing written, nothing saved
    expect(api.startBreak).toHaveBeenCalledWith(5 * 60);
    timer.getState().reset();
  });

  it('asks for an intention, then breathes, then starts the block for the chosen task', async () => {
    api.startFocus.mockResolvedValue({ ...idle(), status: 'running', duration_ms: 1_500_000 });
    brew.getState().primary();
    expect(brew.getState().dialog).toBe('intention');

    brew.getState().beginFocus('Draft the intro', 3, true);
    expect(brew.getState().dialog).toBe('breathe');
    await brew.getState().launch();

    expect(api.startFocus).toHaveBeenCalledWith(expect.objectContaining({ mode: 'Pomodoro', planned: 1500, intention: 'Draft the intro', task_id: 3 }));
    expect(brew.getState().dialog).toBeNull();
    timer.getState().reset();
  });

  it("shows a refused command's reason instead of throwing", async () => {
    timer.getState().apply({ ...idle(), status: 'running', duration_ms: 1_500_000, remaining_ms: 1_000 });
    api.control.mockRejectedValue(new Error('no timer is running'));
    brew.getState().primary();
    await vi.waitFor(() => expect(toasts.getState().message).toBe('no timer is running'));
    timer.getState().reset();
  });

  it('keyboard shortcuts stay out of the way of typing', () => {
    const primary = vi.spyOn(brew.getState(), 'primary');
    const input = document.createElement('input');
    brew.getState().onKey({ code: 'Space', key: ' ', target: input, preventDefault: vi.fn() });
    expect(primary).not.toHaveBeenCalled();
  });
});

function quietSettings() {
  return { sound: false, notify: false, autoBreak: true, strict: false, breathe: true, tick: 'off' };
}
