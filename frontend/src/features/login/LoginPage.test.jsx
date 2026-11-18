import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { auth } from '../../state/services';
import { mockFetch, reply } from '../../test/fakes';
import LoginPage from './LoginPage';

function open(path = '/login') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <LoginPage />
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  const email = () => document.querySelector('input[type=email]');
  const password = () => document.querySelector('input[type=password]');
  const submit = () => user.click(document.querySelector('form .btn-primary'));
  return { user, email, password, submit };
}

describe('LoginPage', () => {
  beforeEach(() => auth.setState({ user: null }));

  it('signs in with an email and password, and never handles a token itself', async () => {
    const calls = mockFetch(({ url }) => (url === '/api/auth/providers' ? reply(200, []) : reply(200, { id: 1, email: 'ada@example.com', name: 'Ada', settings: {} })));
    const { user, email, password, submit } = open();
    await user.type(email(), 'ada@example.com');
    await user.type(password(), 'correct horse battery');
    await submit();

    const login = calls.find((c) => c.url === '/api/auth/login');
    expect(login.body).toEqual({ email: 'ada@example.com', password: 'correct horse battery' });
    // The JWT arrives as an HttpOnly cookie, which scripts can't see; the store only learns who it is.
    await waitFor(() => expect(auth.getState().user?.email).toBe('ada@example.com'));
  });

  it('asks for both fields', async () => {
    const calls = mockFetch(() => reply(200, []));
    const { submit } = open();
    await submit();
    expect(calls.some((c) => c.url === '/api/auth/login')).toBe(false);
    expect(screen.getByRole('alert')).toHaveTextContent('email and password');
  });

  it('holds a short new password back at sign-up', async () => {
    const calls = mockFetch(() => reply(200, []));
    const { user, email, password, submit } = open();
    await user.click(screen.getByRole('tab', { name: 'Sign up' }));
    await user.type(email(), 'ada@example.com');
    await user.type(password(), 'too short');
    await submit();
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
    expect(screen.getByRole('alert')).toHaveTextContent('at least 12');
  });

  it("shows the server's reason when it refuses", async () => {
    mockFetch(({ url }) => (url === '/api/auth/providers' ? reply(200, []) : reply(401, { error: 'wrong email or password' })));
    const { user, email, password, submit } = open();
    await user.type(email(), 'ada@example.com');
    await user.type(password(), 'wrong password');
    await submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('wrong email or password');
  });

  it('offers each configured OAuth 2.0 provider as a full-page link', async () => {
    mockFetch(() => reply(200, ['google', 'github']));
    open();
    expect(await screen.findByRole('link', { name: 'Continue with Google' })).toHaveAttribute('href', '/oauth2/authorization/google');
    expect(screen.getByRole('link', { name: 'Continue with GitHub' })).toHaveAttribute('href', '/oauth2/authorization/github');
  });

  it('explains a refused OAuth 2.0 sign-in', () => {
    mockFetch(() => reply(200, []));
    open('/login?error=email-in-use');
    expect(screen.getByRole('alert')).toHaveTextContent('Log in with your password instead');
  });
});
