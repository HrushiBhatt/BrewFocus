import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import { STORAGE_KEY, Store } from './store';
import { createBrowserTransport } from './transport';

describe('the browser transport (GitHub Pages)', () => {
  let call;
  const failure = (promise) => promise.then(() => null, (e) => e);

  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    call = createBrowserTransport(new Store());
  });
  afterEach(() => localStorage.removeItem(STORAGE_KEY));

  it('answers the same calls the server does, with no password anywhere', async () => {
    const me = await call('GET', '/api/me');
    expect(me.settings.tick).toBe('off');
    expect(await call('GET', '/api/auth/providers')).toEqual([]);

    const task = await call('POST', '/api/tasks', { title: 'Write', estimate: 2 });
    expect(task).toMatchObject({ title: 'Write', brews: 0 });
    await call('PATCH', `/api/tasks/${task.id}`, { done: true });
    expect((await call('GET', '/api/tasks'))[0].done).toBe(true);

    const timer = await call('POST', '/api/timer/focus', { mode: 'Pomodoro', planned: 1500, intention: '', task_id: null, day: '2026-11-04' });
    expect(timer.status).toBe('running');
    expect((await call('POST', '/api/timer/pause')).status).toBe('paused');
  });

  it('turns mistakes into the same errors the server sends', async () => {
    const bad = await failure(call('POST', '/api/tasks', { title: '  ', estimate: 1 }));
    expect(bad).toBeInstanceOf(ApiError);
    expect(bad.status).toBe(400);
    expect(bad.message).toBe('Title is required.');
    expect((await failure(call('GET', '/api/nope'))).status).toBe(404);
  });
});
