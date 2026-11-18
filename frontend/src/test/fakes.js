import { vi } from 'vitest';
import { createEmitter } from '../lib/emitter';

/** A server reply, as fetch would resolve it. */
export function reply(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Replaces fetch with a recorder. `respond(call)` decides each reply from the call's
 * `{ url, method, headers, body }`; every call made is kept in `calls`, with its JSON body parsed.
 */
export function mockFetch(respond = () => reply(200, {})) {
  const calls = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init = {}) => {
    const call = { url, method: init.method ?? 'GET', headers: init.headers ?? {}, body: init.body ? JSON.parse(init.body) : undefined };
    calls.push(call);
    return respond(call);
  });
  return calls;
}

/** The live stream, minus the network: tests push events with `emit`. */
export function fakeLive() {
  const events = createEmitter();
  return { connect: vi.fn(), disconnect: vi.fn(), on: events.on, emit: events.emit };
}

/** Every API call as a mock that resolves to undefined; override the ones a test cares about. */
export function fakeApi(overrides = {}) {
  const names = [
    'me', 'providers', 'login', 'register', 'logout', 'logoutEverywhere', 'changePassword', 'saveSettings',
    'presets', 'createPreset', 'deletePreset', 'tasks', 'createTask', 'updateTask', 'deleteTask',
    'timer', 'startFocus', 'startBreak', 'control', 'sessions', 'reflect', 'clearSessions', 'stats',
  ];
  const api = Object.fromEntries(names.map((n) => [n, vi.fn(async () => undefined)]));
  return Object.assign(api, overrides);
}
