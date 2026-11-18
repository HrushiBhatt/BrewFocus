import { createStore } from 'zustand/vanilla';

/**
 * The signed-in user. The session itself is an HttpOnly cookie the browser manages, so there's no
 * token here: `user` is all the app knows, and the route guards read it.
 *
 * `user` is undefined until the first check, then the user, or null when signed out.
 *
 * @param {typeof import('../api/api').api} api
 */
export function createAuthStore(api) {
  return createStore((set) => ({
    /** @type {import('../api/api').User | null | undefined} */
    user: undefined,

    /** Asks the server who this browser is signed in as. */
    async restore() {
      try {
        set({ user: await api.me() });
      } catch {
        set({ user: null });
      }
    },

    async login(email, password) {
      set({ user: await api.login(email, password) });
    },

    async register(email, password, name) {
      set({ user: await api.register(email, password, name) });
    },

    async logout() {
      await api.logout().catch(() => undefined);
      set({ user: null });
    },

    /** Ends the session on every device, this one included. */
    async logoutEverywhere() {
      await api.logoutEverywhere();
      set({ user: null });
    },

    /** Forgets the user locally, after a 401. The route guards then show the login page. */
    expire() {
      set({ user: null });
    },

    setUser(user) {
      set({ user });
    },
  }));
}
