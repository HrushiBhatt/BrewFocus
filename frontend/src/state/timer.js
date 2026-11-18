import { createStore } from 'zustand/vanilla';

/** No timer. `server_time` at the epoch, so the first real state always wins. */
export const IDLE = {
  phase: 'focus',
  status: 'idle',
  duration_ms: 0,
  remaining_ms: 0,
  server_time: new Date(0).toISOString(),
};

/**
 * The client's view of the server-run timer. The server owns the clock; this only renders it,
 * deriving the remaining time from the server's deadline, corrected for any skew between the two
 * clocks, so every device shows the same countdown.
 *
 * @param {typeof import('../api/api').api} api
 */
export function createTimerStore(api) {
  let ticker;

  const store = createStore((set, get) => ({
    /** @type {import('../api/api').TimerState} */
    state: IDLE,
    /** This device's clock, refreshed four times a second while a timer runs. */
    now: Date.now(),
    /** The server's clock minus ours. */
    skewMs: 0,

    /** Adopts a state from the server, ignoring any that arrive out of order. */
    apply(next) {
      const at = Date.parse(next.server_time);
      if (at < Date.parse(get().state.server_time)) return;
      set({ skewMs: at - Date.now(), now: Date.now(), state: next });
    },

    reset() {
      set({ state: IDLE });
    },

    async startFocus(req) {
      get().apply(await api.startFocus(req));
    },

    async startBreak(seconds) {
      get().apply(await api.startBreak(seconds));
    },

    async control(action) {
      const state = await api.control(action);
      if (action !== 'distraction') get().apply(state);
    },
  }));

  // Re-render four times a second, only while running.
  store.subscribe(({ state }) => {
    const running = state.status === 'running';
    if (running && !ticker) ticker = setInterval(() => store.setState({ now: Date.now() }), 250);
    if (!running && ticker) {
      clearInterval(ticker);
      ticker = undefined;
    }
  });

  return store;
}

/** Milliseconds left: worked out from the deadline while running, the stored value otherwise. */
export function remainingMs({ state, now, skewMs }) {
  if (state.status !== 'running' || !state.ends_at) return state.remaining_ms;
  return Math.max(0, Date.parse(state.ends_at) - (now + skewMs));
}
