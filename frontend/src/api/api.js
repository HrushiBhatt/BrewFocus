import { environment } from '@env';
import { localDay } from '../lib/time';
import { ApiError, fetchTransport } from './http';

/**
 * The API's JSON shapes, for reference (snake_case where the server uses it):
 *
 * @typedef {{ sound: boolean, notify: boolean, autoBreak: boolean, strict: boolean, breathe: boolean,
 *             tick: 'off' | 'clock' | 'ominous' }} Settings
 * @typedef {{ id: number, email: string, name: string, settings: Settings, password_set?: boolean }} User
 * @typedef {{ id: number, name: string, focus: number, rest: number }} Preset   focus/rest in minutes
 * @typedef {{ id: number, title: string, estimate: number, done: boolean, brews: number, minutes: number }} Task
 * @typedef {{ id: number, task_id: number | null, mode: string, planned: number, focused: number,
 *             completed: boolean, intention: string, reflection: string, distractions: number,
 *             day: string, created_at: string }} Session   planned/focused in seconds
 * @typedef {{ phase: 'focus' | 'break', status: 'idle' | 'running' | 'paused', duration_ms: number,
 *             remaining_ms: number, ends_at?: string, session_id?: number, mode?: string,
 *             intention?: string, task_id?: number, server_time: string }} TimerState
 * @typedef {'pause' | 'resume' | 'finish' | 'cancel' | 'distraction'} TimerAction
 */

/** The server over fetch, or (in the GitHub Pages build) the in-browser backend. */
const transport = environment.transport ?? fetchTransport;

let onUnauthorized = () => {};

/** Called when the server answers 401 to anything but a sign-in call: the session has ended. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

async function request(method, url, body) {
  try {
    return await transport(method, url, body);
  } catch (err) {
    const isAuthCall = url.startsWith('/api/auth/') || url === '/api/me';
    if (err instanceof ApiError && err.status === 401 && !isAuthCall) onUnauthorized();
    throw err;
  }
}

const get = (url) => request('GET', url);
const send = (method, url, body) => request(method, url, body);

/** Every API call the app makes. Each resolves to data or rejects with an ApiError. */
export const api = {
  // accounts
  me: () => get('/api/me'),
  providers: () => get('/api/auth/providers'),
  login: (email, password) => send('POST', '/api/auth/login', { email, password }),
  register: (email, password, name) => send('POST', '/api/auth/register', { email, password, name }),
  logout: () => send('POST', '/api/auth/logout'),
  logoutEverywhere: () => send('POST', '/api/auth/logout-all'),
  changePassword: (current, next) => send('PUT', '/api/me/password', { current, new: next }),
  saveSettings: (settings) => send('PUT', '/api/me/settings', settings),

  // custom modes
  presets: () => get('/api/presets'),
  createPreset: (preset) => send('POST', '/api/presets', preset),
  deletePreset: (id) => send('DELETE', `/api/presets/${id}`),

  // tasks
  tasks: () => get('/api/tasks'),
  createTask: (title, estimate) => send('POST', '/api/tasks', { title, estimate }),
  updateTask: (id, patch) => send('PATCH', `/api/tasks/${id}`, patch),
  deleteTask: (id) => send('DELETE', `/api/tasks/${id}`),

  // the server-run timer
  timer: () => get('/api/timer'),
  startFocus: (req) => send('POST', '/api/timer/focus', req),
  startBreak: (seconds) => send('POST', '/api/timer/break', { length: seconds }),
  control: (action) => send('POST', `/api/timer/${action}`),

  // journal & stats
  sessions: (limit = 30) => get(`/api/sessions?limit=${limit}`),
  reflect: (id, reflection) => send('PATCH', `/api/sessions/${id}`, { reflection }),
  clearSessions: () => send('DELETE', '/api/sessions'),
  stats: () => get(`/api/stats?today=${localDay()}`),
};
