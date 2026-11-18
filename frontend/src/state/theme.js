import { createStore } from 'zustand/vanilla';
import { load, persistField } from '../lib/persisted';

export const THEME_KEY = 'brew:theme';

/** The browser's address-bar color for each theme. Keep in step with styles/global.css. */
const COLORS = { light: '#f7f1e8', dark: '#000000' };

const apply = (theme) => {
  document.documentElement.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', COLORS[theme]);
};

/**
 * Light or dark, dark by default whatever the system prefers. It's a preference of this device, like
 * the ambience volumes, so it's kept apart from the account. public/theme-init.js applies the same
 * choice before the first paint, so keep the storage key and colors in step with it.
 */
export function createThemeStore() {
  const store = createStore((set, get) => ({
    current: load(THEME_KEY, 'dark') === 'light' ? 'light' : 'dark', // anything unrecognized means dark
    toggle() {
      set({ current: get().current === 'dark' ? 'light' : 'dark' });
    },
  }));
  apply(store.getState().current);
  store.subscribe(({ current }) => apply(current));
  persistField(store, 'current', THEME_KEY);
  return store;
}
