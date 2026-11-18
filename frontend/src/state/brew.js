import { createStore } from 'zustand/vanilla';
import { errorMessage } from '../api/http';
import { load, persistField } from '../lib/persisted';
import { localDay } from '../lib/time';
import { audio, chime } from '../sound/audio';
import { BUILTIN_MODES, findMode, fromPreset } from './modes';

export const DEFAULT_SETTINGS = { sound: true, notify: false, autoBreak: true, strict: false, breathe: true, tick: 'off' };
const MODE_KEY = 'brew:mode';

/** The current settings: the user's, or the defaults before anyone has signed in. */
export const settingsOf = (user) => user?.settings ?? DEFAULT_SETTINGS;

/**
 * The session flow and the user's productivity data.
 *
 * It turns live server events into UI state and runs each block through
 * intention → (breathe) → focus → reflect → break. The timer itself runs on the server, so every tab
 * and device follows along; this store only decides what to show and which request to send next.
 *
 * @param {object} deps
 * @param {typeof import('../api/api').api} deps.api
 * @param {import('zustand/vanilla').StoreApi<any>} deps.auth    the signed-in user (and their settings)
 * @param {import('zustand/vanilla').StoreApi<any>} deps.timer   the server's timer, as this device sees it
 * @param {import('zustand/vanilla').StoreApi<any>} deps.toasts
 * @param {{ connect: Function, disconnect: Function, on: Function }} deps.live   the server's event stream
 */
export function createBrewStore({ api, auth, timer, toasts, live }) {
  let pending; // the focus block the intention dialog prepared, sent once breathing ends

  const settings = () => settingsOf(auth.getState().user);

  const store = createStore((set, get) => {
    const mode = () => findMode(get().presets, get().modeKey);

    /** Runs a request, showing its error as a toast rather than throwing. */
    const attempt = async (fn) => {
      try {
        await fn();
      } catch (err) {
        toasts.getState().show(errorMessage(err));
      }
    };

    const control = (action) => attempt(() => timer.getState().control(action));

    const closeReflection = () => {
      set({ reflecting: null });
      if (get().dialog === 'reflect') set({ dialog: null });
    };

    const announce = (phase, message) => {
      const { sound, notify } = settings();
      if (sound) chime(phase);
      if (notify && document.hidden && typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        new Notification('Brew Focus', { body: message, icon: `${import.meta.env.BASE_URL}favicon.svg` });
      }
    };

    const allowNotifications = async () => {
      if (typeof Notification === 'undefined') return false;
      return Notification.permission === 'granted' || (await Notification.requestPermission()) === 'granted';
    };

    return {
      // ---- data -------------------------------------------------------------------------------
      presets: [],
      tasks: [],
      stats: null,
      /** The selected mode, remembered on this device. */
      modeKey: load(MODE_KEY, BUILTIN_MODES[0].key),
      /** The task the next focus block is for. */
      taskId: null,

      // ---- flow -------------------------------------------------------------------------------
      /** @type {'intention' | 'breathe' | 'reflect' | 'settings' | null} */
      dialog: null,
      /** The block that just ended, while its reflection prompt is open. */
      reflecting: null,
      /** A break is due but auto-start is off, so it waits for the play button. */
      pendingBreak: false,
      /** Tab switches counted in this block, for strict mode's "welcome back". */
      distractions: 0,

      setDialog: (dialog) => set({ dialog }),

      // ---- lifecycle --------------------------------------------------------------------------

      /** After sign-in: open the live stream and load everything. */
      async start() {
        live.connect();
        await Promise.all(['presets', 'tasks', 'sessions'].map((kind) => get().refresh(kind)));
        try {
          timer.getState().apply(await api.timer());
        } catch {
          // The live stream's opening snapshot brings the timer in anyway.
        }
      },

      /** After sign-out: drop the stream and the user's data. */
      stop() {
        live.disconnect();
        timer.getState().reset();
        set({ presets: [], tasks: [], stats: null, reflecting: null, pendingBreak: false, dialog: null });
      },

      /** Fetches one kind of data again, as the server's "changed" events ask. */
      async refresh(kind) {
        try {
          if (kind === 'presets') set({ presets: await api.presets() });
          if (kind === 'tasks') set({ tasks: await api.tasks() });
          if (kind === 'sessions') set({ stats: await api.stats() });
          if (kind === 'settings' || kind === 'account') await auth.getState().restore();
        } catch {
          // A 401 is handled by the API client; anything else is retried on the next event.
        }
      },

      /** One event from the server's live stream (or, on Pages, the in-browser backend). */
      handle(e) {
        switch (e.type) {
          case 'timer':
            timer.getState().apply(e.data);
            if (e.data.status !== 'idle') {
              set({ pendingBreak: false });
              if (get().reflecting) closeReflection(); // another device moved on
            }
            break;
          case 'session.completed':
            if (e.data.session.completed) announce('focus', 'Brew complete. Time for a break ☕');
            set({ reflecting: e.data, dialog: 'reflect' });
            void get().refresh('sessions');
            void get().refresh('tasks');
            break;
          case 'session.discarded':
            toasts.getState().show('Under a minute, so not logged.');
            break;
          case 'break.completed':
            if (e.data.completed) announce('break', 'Break over. Ready for another brew?');
            break;
          case 'session.reflected':
            if (get().reflecting?.session.id === e.data.id) closeReflection();
            break;
          case 'changed':
            void get().refresh(e.data.kind);
            break;
          case 'save.failed':
            toasts.getState().show("This browser won't save, so changes last until you close the tab.");
            break;
        }
      },

      // ---- session flow -----------------------------------------------------------------------

      /** The big button: start, pause or resume, or begin a waiting break. */
      primary() {
        audio(); // unlock Web Audio inside the click, so later chimes can play
        const { status } = timer.getState().state;
        if (status === 'running') void control('pause');
        else if (status === 'paused') void control('resume');
        else if (get().pendingBreak) void get().startBreak();
        else set({ dialog: 'intention' });
      },

      beginFocus(intention, taskId, breathe) {
        set({ taskId, distractions: 0 });
        if (breathe !== settings().breathe) void get().updateSettings({ breathe });
        const m = mode();
        pending = { mode: m.name, planned: m.focus * 60, intention, task_id: taskId };
        if (breathe) set({ dialog: 'breathe' });
        else void get().launch();
      },

      /** Starts the prepared focus block (after the intention, and breathing if chosen). */
      async launch() {
        set({ dialog: null });
        const req = pending;
        pending = undefined;
        if (req) await attempt(() => timer.getState().startFocus({ ...req, day: localDay() }));
      },

      async finishReflection(note) {
        const done = get().reflecting;
        closeReflection();
        if (!done) return;
        if (note) api.reflect(done.session.id, note).catch((err) => toasts.getState().show(errorMessage(err)));
        if (!mode().rest) return;
        if (settings().autoBreak) await get().startBreak();
        else set({ pendingBreak: true });
      },

      async startBreak() {
        set({ pendingBreak: false });
        await attempt(() => timer.getState().startBreak(mode().rest * 60));
      },

      /** Finish focus early (logged if at least a minute) or skip a break. */
      skip() {
        if (timer.getState().state.status !== 'idle') void control('finish');
        else set({ pendingBreak: false });
      },

      /** Abandon the current block without logging it. */
      reset() {
        if (timer.getState().state.status !== 'idle') void control('cancel');
        else set({ pendingBreak: false });
      },

      /** Strict mode: the tab was hidden mid-focus. */
      distracted() {
        set({ distractions: get().distractions + 1 });
        void control('distraction');
      },

      /** Space, R and S, when focus isn't in a form control. */
      onKey(e) {
        if (get().dialog || e.metaKey || e.ctrlKey || e.altKey) return;
        if (e.target instanceof Element && e.target.closest('input, textarea, select, button, summary')) return;
        if (e.code === 'Space') {
          e.preventDefault();
          get().primary();
        } else if (e.key === 'r') get().reset();
        else if (e.key === 's') get().skip();
      },

      // ---- modes & tasks ------------------------------------------------------------------------

      selectMode(m) {
        set({ modeKey: m.key });
      },

      /** Saves a custom mode. Errors are thrown back to the form, which shows them. */
      async createMode(name, focus, rest) {
        const preset = await api.createPreset({ name, focus, rest });
        // The live "changed" event may have refetched the list already, so add it only if it's missing.
        const list = get().presets;
        set({ presets: list.some((p) => p.id === preset.id) ? list : [...list, preset], modeKey: fromPreset(preset).key });
        toasts.getState().show(`Saved “${preset.name}”`);
      },

      async deleteMode(m) {
        if (m.presetId === undefined) return;
        await attempt(async () => {
          await api.deletePreset(m.presetId);
          set({ presets: get().presets.filter((p) => p.id !== m.presetId) });
          if (m.key === get().modeKey) set({ modeKey: BUILTIN_MODES[0].key });
        });
      },

      async addTask(title, estimate) {
        await attempt(async () => {
          const task = await api.createTask(title, estimate);
          const list = get().tasks;
          if (!list.some((t) => t.id === task.id)) {
            set({ tasks: [...list.filter((t) => !t.done), task, ...list.filter((t) => t.done)] });
          }
        });
      },

      async toggleTask(task) {
        set({ tasks: get().tasks.map((t) => (t.id === task.id ? { ...t, done: !t.done } : t)) }); // optimistic
        await attempt(() => api.updateTask(task.id, { done: !task.done }));
        if (task.id === get().taskId && !task.done) set({ taskId: null });
        void get().refresh('tasks');
      },

      async removeTask(task) {
        await attempt(async () => {
          await api.deleteTask(task.id);
          set({ tasks: get().tasks.filter((t) => t.id !== task.id) });
          if (task.id === get().taskId) set({ taskId: null });
        });
      },

      /** Picks a task and opens the intention prompt for it. */
      focusOn(task) {
        set({ taskId: task.id });
        if (timer.getState().state.status === 'idle' && !get().pendingBreak) get().primary();
      },

      // ---- settings -----------------------------------------------------------------------------

      async updateSettings(patch) {
        const user = auth.getState().user;
        if (!user) return;
        if (patch.notify && !user.settings.notify && !(await allowNotifications())) {
          toasts.getState().show('Notifications are blocked for this site in your browser.');
          return;
        }
        auth.getState().setUser({ ...user, settings: { ...user.settings, ...patch } }); // optimistic
        try {
          await api.saveSettings({ ...user.settings, ...patch });
        } catch (err) {
          auth.getState().setUser(user);
          toasts.getState().show(errorMessage(err));
        }
      },

      async clearData() {
        if (!confirm('Delete every logged session? Tasks and custom modes are kept.')) return;
        await attempt(async () => {
          await api.clearSessions();
          await Promise.all([get().refresh('sessions'), get().refresh('tasks')]);
          toasts.getState().show('All sessions deleted.');
        });
      },
    };
  });

  live.on((e) => store.getState().handle(e));
  persistField(store, 'modeKey', MODE_KEY);
  return store;
}
