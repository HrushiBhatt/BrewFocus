import { CAFE } from '../components/sprites';
import { createEmitter } from '../lib/emitter';
import { localDay } from '../lib/time';

/**
 * The backend for the GitHub Pages build, where the Spring Boot server can't run. Everything lives in
 * this browser's localStorage, with no account, and nothing leaves the device. It follows the same
 * rules as the server. Three things make it work across tabs:
 *
 *  - localStorage is the one source of truth, read again on every call.
 *  - Events reach other tabs over a BroadcastChannel.
 *  - Each tab sets an alarm for the timer's deadline, and a Web Lock makes exactly one of them finish
 *    the block.
 *
 * The timer stores when it ends rather than counting down, so it survives reloads, closed tabs and
 * the throttling browsers apply to background tabs.
 */

export const STORAGE_KEY = 'brew:data-v1';
const MIN_LOGGED_MS = 60_000; // a block ended earlier than this isn't worth logging
const HEATMAP_DAYS = 371; // 53 weeks, enough to fill the year grid

const DEFAULT_SETTINGS = { sound: true, notify: false, autoBreak: true, strict: false, breathe: true, tick: 'off' };

/** A mistake the UI can show as it is, such as an empty title. */
export class StoreError extends Error {}

export class Store {
  #events = createEmitter();
  #channel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(STORAGE_KEY);
  #memory; // what's kept when localStorage is blocked or full
  #alarm;
  #warned = false;

  constructor() {
    this.#channel?.addEventListener('message', (e) => this.#events.emit(e.data));
    addEventListener('storage', (e) => {
      if (e.key === STORAGE_KEY) this.#arm(); // another tab changed the timer
    });
    this.#arm(); // also finishes a block whose deadline passed while no tab was open
  }

  /** Events from this tab and every other open tab. Returns the function that stops listening. */
  subscribe(listener) {
    return this.#events.on(listener);
  }

  // ---- settings ---------------------------------------------------------------------------

  settings() {
    return this.#read().settings;
  }

  saveSettings(next) {
    if (!['off', 'clock', 'ominous'].includes(next.tick)) throw new StoreError('Ticking must be off, clock or ominous.');
    const db = this.#read();
    db.settings = next;
    this.#save(db, 'settings');
    return next;
  }

  // ---- custom modes -------------------------------------------------------------------------

  presets() {
    return this.#read().presets;
  }

  createPreset(input) {
    const db = this.#read();
    const preset = {
      id: db.nextId++,
      name: text(input.name, 'Name', 32, true),
      focus: whole(input.focus, 'Focus', 1, 180),
      rest: whole(input.rest, 'Break', 0, 60),
    };
    db.presets.push(preset);
    this.#save(db, 'presets');
    return preset;
  }

  deletePreset(id) {
    this.#remove('presets', id);
  }

  // ---- tasks ---------------------------------------------------------------------------------

  /** Open tasks first, each with its totals from finished sessions. */
  tasks() {
    const db = this.#read();
    const totals = new Map();
    for (const s of db.sessions) {
      if (s.task_id === null) continue;
      const t = totals.get(s.task_id) ?? { brews: 0, seconds: 0 };
      t.brews += s.completed ? 1 : 0;
      t.seconds += s.focused;
      totals.set(s.task_id, t);
    }
    return [...db.tasks]
      .sort((a, b) => Number(a.done) - Number(b.done) || a.id - b.id)
      .map((t) => ({ ...t, brews: totals.get(t.id)?.brews ?? 0, minutes: Math.floor((totals.get(t.id)?.seconds ?? 0) / 60) }));
  }

  createTask(title, estimate) {
    const db = this.#read();
    const task = { id: db.nextId++, title: text(title, 'Title', 120, true), estimate: whole(estimate, 'Estimate', 1, 20), done: false };
    db.tasks.push(task);
    this.#save(db, 'tasks');
    return { ...task, brews: 0, minutes: 0 };
  }

  setTaskDone(id, done) {
    const db = this.#read();
    const task = db.tasks.find((t) => t.id === id);
    if (!task) throw new StoreError('That task no longer exists.');
    task.done = done;
    this.#save(db, 'tasks');
    return task;
  }

  deleteTask(id) {
    this.#remove('tasks', id);
  }

  #remove(kind, id) {
    const db = this.#read();
    const index = db[kind].findIndex((item) => item.id === id);
    if (index >= 0) db[kind].splice(index, 1);
    this.#save(db, kind);
  }

  // ---- journal & stats -------------------------------------------------------------------

  /** The newest finished sessions first. */
  sessions(limit = 30) {
    return this.#read().sessions.slice(-limit).reverse();
  }

  reflect(id, reflection) {
    const db = this.#read();
    const session = db.sessions.find((s) => s.id === id);
    if (!session) throw new StoreError('That session no longer exists.');
    session.reflection = text(reflection, 'Reflection', 280);
    this.#write(db);
    this.#publish({ type: 'session.reflected', data: { id } }); // the other tabs can close their prompt
    return session;
  }

  clearSessions() {
    const db = this.#read();
    db.sessions = [];
    this.#save(db, 'sessions');
  }

  /** Today's totals, streaks, all-time totals and the year's heatmap. */
  stats(today = localDay()) {
    const days = new Map();
    for (const s of this.#read().sessions) {
      const d = days.get(s.day) ?? { seconds: 0, sessions: 0, completed: 0 };
      d.seconds += s.focused;
      d.sessions += 1;
      d.completed += s.completed ? 1 : 0;
      days.set(s.day, d);
    }
    const minutes = Object.fromEntries([...days].map(([day, d]) => [day, Math.floor(d.seconds / 60)]));
    const [streak, best] = streaks(new Set(days.keys()), today);
    const cutoff = shiftDay(today, -HEATMAP_DAYS);
    const sum = (field) => [...days.values()].reduce((n, d) => n + d[field], 0);
    return {
      today: { minutes: minutes[today] ?? 0, sessions: days.get(today)?.sessions ?? 0 },
      streak,
      best_streak: best,
      total: { minutes: Object.values(minutes).reduce((n, m) => n + m, 0), sessions: sum('sessions'), completed: sum('completed') },
      heatmap: Object.fromEntries(Object.entries(minutes).filter(([day]) => day > cutoff)),
    };
  }

  // ---- the timer ---------------------------------------------------------------------------

  timer() {
    return snapshot(this.#read().timer);
  }

  startFocus(req) {
    const db = this.#read();
    const mode = text(req.mode, 'Mode', 32, true);
    const planned = whole(req.planned, 'Length', 60, 10_800) * 1000;
    const intention = text(req.intention, 'Intention', 140);
    const task = req.task_id === null ? undefined : db.tasks.find((t) => t.id === req.task_id);
    if (req.task_id !== null && !task) throw new StoreError('That task no longer exists.');
    this.#ensureIdle(db);
    db.timer = {
      ...running(planned),
      phase: 'focus',
      session_id: db.nextId++,
      mode,
      intention: intention || task?.title || '',
      task_id: req.task_id,
      day: req.day,
    };
    return this.#commit(db, snapshot(db.timer));
  }

  startBreak(seconds) {
    const db = this.#read();
    const length = whole(seconds, 'Break', 60, 3600) * 1000;
    this.#ensureIdle(db);
    db.timer = { ...running(length), phase: 'break' };
    return this.#commit(db, snapshot(db.timer));
  }

  /** Pause, resume, finish early, cancel, or count a distraction. */
  control(action) {
    const db = this.#read();
    const t = db.timer;
    if (!t) throw new StoreError('No timer is running.');
    switch (action) {
      case 'finish':
        return this.#end(db, false);
      case 'cancel':
        db.timer = null;
        return this.#commit(db, snapshot(null, t.phase));
      case 'distraction':
        if (t.phase === 'focus') t.distractions++;
        this.#write(db);
        return snapshot(t); // nothing visible changed, so no event
      case 'pause':
        if (t.status === 'running') Object.assign(t, { status: 'paused', remaining_ms: remainingOf(t), ends_at: undefined });
        break;
      case 'resume':
        if (t.status === 'paused') Object.assign(t, { status: 'running', ends_at: new Date(Date.now() + t.remaining_ms).toISOString() });
        break;
    }
    return this.#commit(db, snapshot(t));
  }

  #ensureIdle(db) {
    if (db.timer) throw new StoreError('A timer is already running.');
  }

  /** Save, set the alarm for the new deadline, and announce the timer's state. */
  #commit(db, state) {
    this.#write(db);
    this.#arm();
    this.#publish({ type: 'timer', data: state });
    return state;
  }

  /** Set this tab's alarm for the running timer's deadline. Every open tab does. */
  #arm() {
    clearTimeout(this.#alarm);
    const t = this.#read().timer;
    if (t?.status === 'running') this.#alarm = setTimeout(() => this.#expire(), Math.max(0, Date.parse(t.ends_at) - Date.now()));
  }

  #expire() {
    const finish = () => {
      const db = this.#read(); // read again inside the lock: another tab may have finished it already
      const t = db.timer;
      if (t?.status !== 'running') return;
      if (Date.parse(t.ends_at) > Date.now()) return this.#arm(); // woke early, e.g. resumed elsewhere
      this.#end(db, true);
    };
    if (typeof navigator !== 'undefined' && navigator.locks) void navigator.locks.request(STORAGE_KEY, finish);
    else finish();
  }

  /** Finish the timer: log a focus block, or discard one ended early in its first minute. */
  #end(db, completed) {
    const t = db.timer;
    db.timer = null;
    const elapsed = t.duration_ms - (completed ? 0 : remainingOf(t));
    let announce;
    if (t.phase === 'break') {
      announce = { type: 'break.completed', data: { completed } };
    } else if (!completed && elapsed < MIN_LOGGED_MS) {
      announce = { type: 'session.discarded', data: null };
    } else {
      const session = {
        id: t.session_id,
        task_id: t.task_id ?? null,
        mode: t.mode,
        planned: Math.round(t.duration_ms / 1000),
        focused: Math.round(elapsed / 1000),
        completed,
        intention: t.intention ?? '',
        reflection: '',
        distractions: t.distractions,
        day: t.day,
        created_at: t.started_at,
      };
      db.sessions.push(session);
      const full = db.sessions.filter((s) => s.completed).length;
      const unlocked = completed ? CAFE.find((item) => item.at === full)?.id : undefined;
      announce = { type: 'session.completed', data: { session, unlocked } };
    }
    const idle = this.#commit(db, snapshot(null, t.phase));
    this.#publish(announce);
    return idle;
  }

  // ---- storage & events --------------------------------------------------------------------

  #read() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) this.#memory = normalize(JSON.parse(saved));
    } catch {
      // Storage blocked or damaged: carry on with what's in memory.
    }
    this.#memory ??= normalize(null);
    return this.#memory;
  }

  #write(db) {
    this.#memory = db;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch {
      // Private mode, or storage full. Changes last until the tab closes, so say so once.
      if (!this.#warned) this.#events.emit({ type: 'save.failed', data: null });
      this.#warned = true;
    }
  }

  /** Write, then tell every tab, this one included, to read that kind of data again. */
  #save(db, kind) {
    this.#write(db);
    this.#publish({ type: 'changed', data: { kind } });
  }

  #publish(event) {
    this.#events.emit(event);
    this.#channel?.postMessage(event);
  }
}

// ---- helpers ---------------------------------------------------------------------------------

/** Fills in whatever a damaged or hand-edited save is missing, so it can't break the page. */
function normalize(raw) {
  const d = typeof raw === 'object' && raw !== null ? raw : {};
  const list = (value) => (Array.isArray(value) ? value : []);
  return {
    nextId: Number.isInteger(d.nextId) ? d.nextId : 1,
    settings: { ...DEFAULT_SETTINGS, ...(typeof d.settings === 'object' ? d.settings : {}) },
    presets: list(d.presets),
    tasks: list(d.tasks),
    sessions: list(d.sessions),
    timer: d.timer ?? null,
  };
}

const remainingOf = (t) => (t.status === 'running' ? Math.max(0, Date.parse(t.ends_at) - Date.now()) : t.remaining_ms);

/** A freshly started timer. */
function running(durationMs) {
  const now = Date.now();
  return {
    status: 'running',
    duration_ms: durationMs,
    remaining_ms: durationMs,
    ends_at: new Date(now + durationMs).toISOString(),
    started_at: new Date(now).toISOString(),
    distractions: 0,
  };
}

/** The timer as the app sees it. */
function snapshot(t, phase = 'focus') {
  const server_time = new Date().toISOString(); // this device's clock is the only clock here
  if (!t) return { phase, status: 'idle', duration_ms: 0, remaining_ms: 0, server_time };
  return {
    phase: t.phase,
    status: t.status,
    duration_ms: t.duration_ms,
    remaining_ms: remainingOf(t),
    ends_at: t.ends_at,
    session_id: t.session_id,
    mode: t.mode,
    intention: t.intention,
    task_id: t.task_id ?? undefined,
    server_time,
  };
}

/** Moves a YYYY-MM-DD day by n days. Noon, so a clock change can't land on the wrong day. */
function shiftDay(day, n) {
  const [y, m, d] = day.split('-').map(Number);
  return localDay(new Date(y, m - 1, d + n, 12));
}

/** The current and best runs of consecutive active days. Today doesn't break a streak yet. */
function streaks(active, today) {
  let best = 0;
  let run = 0;
  let previous = '';
  for (const day of [...active].sort()) {
    run = previous && shiftDay(previous, 1) === day ? run + 1 : 1;
    best = Math.max(best, run);
    previous = day;
  }
  let current = 0;
  let cursor = active.has(today) ? today : shiftDay(today, -1);
  while (active.has(cursor)) {
    current++;
    cursor = shiftDay(cursor, -1);
  }
  return [current, best];
}

function whole(value, label, lo, hi) {
  if (!Number.isInteger(value) || value < lo || value > hi) throw new StoreError(`${label} must be a whole number from ${lo} to ${hi}.`);
  return value;
}

function text(raw, label, max, required = false) {
  const value = String(raw ?? '').trim();
  if (required && !value) throw new StoreError(`${label} is required.`);
  if (value.length > max) throw new StoreError(`${label} must be at most ${max} characters.`);
  return value;
}
