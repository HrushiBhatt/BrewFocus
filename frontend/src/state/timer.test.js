import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeApi } from '../test/fakes';
import { createTimerStore, remainingMs } from './timer';

const at = (ms) => new Date(ms).toISOString();

function running(serverNow, left) {
  return {
    phase: 'focus',
    status: 'running',
    duration_ms: 1_500_000,
    remaining_ms: left,
    ends_at: at(serverNow + left),
    server_time: at(serverNow),
  };
}

describe('timer store', () => {
  let api;
  let timer;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    api = fakeApi();
    timer = createTimerStore(api);
  });

  afterEach(() => {
    timer.getState().reset(); // stops the ticker
    vi.useRealTimers();
  });

  it('derives the countdown from the server deadline, corrected for clock skew', () => {
    // The server's clock is 5 s ahead of ours; 60 s remain by its reckoning.
    timer.getState().apply(running(1_005_000, 60_000));
    expect(remainingMs(timer.getState())).toBe(60_000);

    vi.advanceTimersByTime(10_000); // the ticker refreshes `now`
    expect(remainingMs(timer.getState())).toBe(50_000);
  });

  it('ignores states that arrive out of order', () => {
    timer.getState().apply(running(1_000_000, 60_000));
    timer.getState().apply({ ...running(999_000, 90_000), status: 'paused' });
    expect(timer.getState().state.status).toBe('running');
  });

  it('uses the stored remaining time while paused', () => {
    timer.getState().apply({ ...running(1_000_000, 42_000), status: 'paused', ends_at: undefined });
    vi.advanceTimersByTime(10_000);
    expect(remainingMs(timer.getState())).toBe(42_000);
  });

  it('only ticks while a timer runs', () => {
    timer.getState().apply(running(1_000_000, 60_000));
    expect(vi.getTimerCount()).toBe(1);
    timer.getState().reset();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('sends commands to the server and adopts the reply, except for distractions', async () => {
    api.control.mockResolvedValue({ ...running(1_000_000, 30_000), status: 'paused' });
    await timer.getState().control('pause');
    expect(api.control).toHaveBeenCalledWith('pause');
    expect(timer.getState().state.status).toBe('paused');

    api.control.mockResolvedValue({ ...running(1_000_001, 30_000), status: 'running' });
    await timer.getState().control('distraction');
    expect(timer.getState().state.status).toBe('paused'); // nothing visible changed
  });
});
