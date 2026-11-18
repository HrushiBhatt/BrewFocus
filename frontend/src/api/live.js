import { createStore } from 'zustand/vanilla';
import { createEmitter } from '../lib/emitter';
import { ApiError } from './http';

/** Everything the server pushes over /api/events. */
const EVENT_TYPES = ['timer', 'session.completed', 'session.discarded', 'break.completed', 'session.reflected', 'changed'];

/**
 * The server's live stream (Server-Sent Events). Every tab and device signed in to the same account
 * receives the same events, which keeps them in sync. EventSource reconnects on its own, and the
 * server opens each connection with a fresh snapshot of the timer.
 *
 * It gives up when a reconnect is refused, which usually means the session was ended from another
 * device, so that case asks the server rather than leaving a stale page open. In the GitHub Pages
 * build the same events come from the in-browser backend instead (`localEvents`).
 *
 * @param {object} options
 * @param {() => Promise<unknown>} options.me         checks whether the session is still alive
 * @param {() => void} options.onSessionEnded         called when it isn't
 * @param {(listener: Function) => Function} [options.localEvents]  the in-browser backend's events
 */
export function createLiveEvents({ me, onSessionEnded, localEvents }) {
  const events = createEmitter();
  const status = createStore(() => ({ connected: false }));
  let source;
  let wanted = false;
  let stopLocal;

  const setConnected = (connected) => status.setState({ connected });

  function close() {
    source?.close();
    source = undefined;
    setConnected(false);
  }

  async function recheck() {
    close();
    try {
      await me();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        if (wanted) onSessionEnded();
        return;
      }
    }
    setTimeout(() => {
      if (wanted) connect();
    }, 5000); // the server was briefly unwell, not the session
  }

  function connect() {
    wanted = true;
    if (localEvents) {
      stopLocal ??= localEvents((e) => events.emit(e));
      setConnected(true);
      return;
    }
    if (source) return;
    const stream = new EventSource('/api/events'); // same origin, so the session cookie goes along
    for (const type of EVENT_TYPES) {
      stream.addEventListener(type, (e) => events.emit({ type, data: JSON.parse(e.data) }));
    }
    stream.onopen = () => setConnected(true);
    stream.onerror = () => {
      setConnected(stream.readyState === EventSource.OPEN);
      if (stream.readyState === EventSource.CLOSED) void recheck();
    };
    source = stream;
  }

  function disconnect() {
    wanted = false;
    stopLocal?.();
    stopLocal = undefined;
    close();
  }

  return {
    connect,
    disconnect,
    /** Listen to every event; returns the function that stops listening. */
    on: events.on,
    /** `{ connected }`, as a store the header's LIVE badge reads. */
    status,
    /** True when the backend runs in this browser (the GitHub Pages build). */
    isLocal: Boolean(localEvents),
  };
}
