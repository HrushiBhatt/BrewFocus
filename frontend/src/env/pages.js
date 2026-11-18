import { Store } from '../browser/store';
import { createBrowserTransport } from '../browser/transport';

/**
 * The GitHub Pages build (`vite build --mode pages`). Pages serves static files only, so it can't run
 * the Spring Boot server. An in-browser store answers the same /api calls and keeps data in
 * localStorage, with no accounts. Only this build bundles it.
 */
const store = new Store();

/** @type {import('./server').Environment} */
export const environment = {
  browserBackend: true,
  transport: createBrowserTransport(store),
  localEvents: (listener) => store.subscribe(listener),
};
