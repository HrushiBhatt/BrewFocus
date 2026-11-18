import { createStore } from 'zustand/vanilla';

const SHOWN_FOR_MS = 3200;

/** The small message at the bottom of the page. One at a time; a new one replaces the last. */
export function createToastStore() {
  let timeout;
  return createStore((set) => ({
    message: '',
    visible: false,
    show(message) {
      clearTimeout(timeout);
      set({ message, visible: true });
      timeout = setTimeout(() => set({ visible: false }), SHOWN_FOR_MS);
    },
  }));
}
