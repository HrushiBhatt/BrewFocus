/**
 * Per-device preferences in localStorage (the theme, ambience volumes, the selected mode). Storage can
 * be blocked, full or hand-edited, so every read falls back to the default and every write may fail
 * quietly: a preference that doesn't persist is never worth breaking the page for.
 */

/** The saved value, merged over `initial` for objects so newly added keys get their defaults. */
export function load(key, initial) {
  try {
    const saved = localStorage.getItem(key);
    if (saved === null) return initial;
    const parsed = JSON.parse(saved);
    if (typeof initial === 'object' && initial !== null) {
      return typeof parsed === 'object' && parsed !== null ? { ...initial, ...parsed } : initial;
    }
    return parsed;
  } catch {
    return initial;
  }
}

export function save(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Private mode or full: the preference just won't persist.
  }
}

/** Saves one field of a Zustand store whenever it changes. */
export function persistField(store, field, key) {
  return store.subscribe((state, previous) => {
    if (state[field] !== previous[field]) save(key, state[field]);
  });
}
