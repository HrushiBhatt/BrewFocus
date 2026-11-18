import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { api } from '../../api/api';
import { errorMessage } from '../../api/http';
import { PixelMug } from '../../components/PixelMug';
import { auth } from '../../state/services';
import './LoginPage.css';

const MIN_NEW_PASSWORD = 12;

/** What each OAuth 2.0 provider is called on its button. */
const PROVIDER_NAMES = { google: 'Google', github: 'GitHub' };

/** The server sends people back here with ?error=<code> when an OAuth 2.0 sign-in is refused. */
const OAUTH_ERRORS = {
  'email-in-use': 'That email already has an account here. Log in with your password instead.',
  'unverified-email': "That account has no verified email address, so it can't be used to sign in.",
  oauth: "Signing in with that provider didn't work. Please try again.",
};

/**
 * The first thing a signed-out visitor sees. The server checks the password (or the OAuth 2.0
 * provider vouches for the person), then sets the JWT in an HttpOnly cookie, so this page never sees
 * or stores a token.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState(() => OAUTH_ERRORS[params.get('error')] ?? (params.get('error') ? OAUTH_ERRORS.oauth : ''));
  const [busy, setBusy] = useState(false);
  const [providers, setProviders] = useState([]);
  const registering = mode === 'register';

  useEffect(() => {
    api.providers().then(setProviders, () => setProviders([]));
  }, []);

  const field = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  function switchTo(next) {
    setMode(next);
    setError('');
  }

  async function submit(e) {
    e.preventDefault();
    const { name, email, password } = form;
    // No length rule at sign-in: an account may predate the current one. The server judges a new
    // password's strength; this only saves a round trip on the obvious case.
    const emailOk = /^[^\s@]+@[^\s@]+$/.test(email);
    if (!emailOk || !password || (registering && [...password].length < MIN_NEW_PASSWORD)) {
      setError(registering ? `Enter a valid email and a password of at least ${MIN_NEW_PASSWORD} characters.` : 'Enter your email and password.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      if (registering) await auth.getState().register(email, password, name);
      else await auth.getState().login(email, password);
      navigate('/');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    // The name and mug on one side, the form on the other, so the page uses its whole width.
    <div className="login-page">
      <main className="welcome">
        <section className="brand">
          <PixelMug className="mug" fill={1} running />
          <h1 className="title">Brew Focus</h1>
          <p className="tagline">Pomodoro timers, tasks and streaks. Focus sessions that follow you across devices and add up to habits.</p>
        </section>
        <section className="card panel px">
          <div className="switch-row" role="tablist" aria-label="Account">
            <button className="seg px" type="button" role="tab" aria-selected={!registering} onClick={() => switchTo('login')}>
              Log in
            </button>
            <button className="seg px" type="button" role="tab" aria-selected={registering} onClick={() => switchTo('register')}>
              Sign up
            </button>
          </div>
          <form className="modal-form" onSubmit={submit} noValidate>
            {registering && (
              <label className="field">
                <span>
                  Name <span className="optional">(optional)</span>
                </span>
                <input className="input px" value={form.name} onChange={field('name')} autoComplete="name" maxLength={60} />
              </label>
            )}
            <label className="field">
              Email
              <input className="input px" type="email" value={form.email} onChange={field('email')} autoComplete="email" />
            </label>
            <label className="field">
              Password
              <input
                className="input px"
                type="password"
                value={form.password}
                onChange={field('password')}
                autoComplete={registering ? 'new-password' : 'current-password'}
                placeholder={registering ? `${MIN_NEW_PASSWORD}+ characters` : undefined}
              />
            </label>
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
            <button className="btn btn-primary px" disabled={busy}>
              {busy ? 'Brewing…' : registering ? 'Create account' : 'Log in'}
            </button>
          </form>
          {providers.length > 0 && (
            <div className="oauth">
              <p className="oauth-or">or</p>
              {/* A full-page visit, not fetch: the provider's sign-in page must open in this tab. */}
              {providers.map((id) => (
                <a key={id} className="btn px oauth-btn" href={`/oauth2/authorization/${id}`}>
                  Continue with {PROVIDER_NAMES[id] ?? id}
                </a>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
