import { ApiError } from '../api/http';
import { StoreError } from './store';

/**
 * Answers /api requests in the browser, for the GitHub Pages build. The screens can't tell the
 * difference: they make the same calls they make to the Spring Boot server, and get the same shapes
 * and the same kinds of errors back. Only the Pages build includes this file.
 *
 * @param {import('./store').Store} store
 */
export function createBrowserTransport(store) {
  return async function browserTransport(method, url, body) {
    try {
      return route(store, method, url, body ?? {});
    } catch (err) {
      const status = err instanceof NotFound ? 404 : err instanceof StoreError ? 400 : 500;
      throw new ApiError(status, err instanceof Error ? err.message : 'something went wrong');
    }
  };
}

class NotFound extends Error {
  constructor() {
    super('not found');
  }
}

/** The same routes as the server's controllers, minus accounts. */
function route(store, method, url, body) {
  const [path, query] = url.replace(/^\/api\//, '').split('?');
  const parts = path.split('/');
  const id = Number(parts[1]);
  const params = new URLSearchParams(query);
  switch (`${method} ${parts[0]}${parts.length > 1 ? '/' : ''}`) {
    case 'GET me': // there's no account here, but the app still needs its settings
      return { id: 1, email: '', name: '', settings: store.settings(), password_set: false };
    case 'GET auth/':
      if (parts[1] === 'providers') return [];
      break;
    case 'PUT me/':
      if (parts[1] === 'settings') return store.saveSettings(body);
      break;
    case 'GET presets':
      return store.presets();
    case 'POST presets':
      return store.createPreset(body);
    case 'DELETE presets/':
      return store.deletePreset(id);
    case 'GET tasks':
      return store.tasks();
    case 'POST tasks':
      return store.createTask(body.title, body.estimate);
    case 'PATCH tasks/':
      return store.setTaskDone(id, body.done);
    case 'DELETE tasks/':
      return store.deleteTask(id);
    case 'GET timer':
      return store.timer();
    case 'POST timer/':
      if (parts[1] === 'focus') return store.startFocus(body);
      if (parts[1] === 'break') return store.startBreak(body.length);
      return store.control(parts[1]);
    case 'GET sessions':
      return store.sessions(Number(params.get('limit')) || 30);
    case 'PATCH sessions/':
      return store.reflect(id, body.reflection);
    case 'DELETE sessions':
      return store.clearSessions();
    case 'GET stats':
      return store.stats(params.get('today') ?? undefined);
  }
  throw new NotFound();
}
