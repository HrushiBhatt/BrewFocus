import { describe, expect, it, vi } from 'vitest';
import { mockFetch, reply } from '../test/fakes';
import { ApiError, errorMessage, fetchTransport } from './http';

describe('fetchTransport', () => {
  it('echoes the CSRF cookie on changes, and only on changes', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';
    const calls = mockFetch(() => reply(200, { ok: true }));

    await fetchTransport('GET', '/api/tasks');
    await fetchTransport('POST', '/api/tasks', { title: 'Write' });

    expect(calls[0].headers['X-XSRF-TOKEN']).toBeUndefined();
    expect(calls[1].headers['X-XSRF-TOKEN']).toBe('token-123');
    expect(calls[1].body).toEqual({ title: 'Write' });
  });

  it("rejects with the server's own sentence", async () => {
    mockFetch(() => reply(400, { error: 'title is required' }));
    const err = await fetchTransport('POST', '/api/tasks', {}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(errorMessage(err)).toBe('title is required');
  });

  it('says so when the server is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    const err = await fetchTransport('GET', '/api/me').catch((e) => e);
    expect(err.status).toBe(0);
    expect(err.message).toBe("Can't reach the Brew Focus server.");
  });

  it('resolves 204 to nothing', async () => {
    mockFetch(() => new Response(null, { status: 204 }));
    await expect(fetchTransport('DELETE', '/api/tasks/1')).resolves.toBeUndefined();
  });
});
