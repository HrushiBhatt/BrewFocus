/**
 * The normal build: the Spring Boot server is the backend, and you sign in. vite.config.js points
 * `@env` here unless the build runs with `--mode pages`.
 *
 * @typedef {object} Environment
 * @property {boolean} browserBackend   the backend runs in this browser instead of on a server
 * @property {Function | null} transport answers API calls in the browser, or null for fetch
 * @property {Function | null} localEvents subscribes to the in-browser backend's live events
 */

/** @type {Environment} */
export const environment = {
  browserBackend: false,
  transport: null,
  localEvents: null,
};
