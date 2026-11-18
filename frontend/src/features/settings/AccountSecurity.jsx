import { useState } from 'react';
import { api } from '../../api/api';
import { errorMessage } from '../../api/http';
import { auth, brew, toasts, useAuth } from '../../state/services';
import './AccountSecurity.css';

/** The account part of the settings dialog: changing the password, and signed-in devices. */
export function AccountSecurity() {
  const passwordSet = useAuth((s) => s.user?.password_set ?? true);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  /** Runs one account action, showing its error here rather than as a toast. */
  async function run(action) {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  /** The server checks the current password, and ends every other session once it changes. */
  function savePassword(e) {
    e.preventDefault();
    if (!next) {
      setError('Enter a new password');
      return;
    }
    void run(async () => {
      await api.changePassword(current, next);
      setCurrent('');
      setNext('');
      await auth.getState().restore();
      toasts.getState().show('Password saved. Other devices signed out.');
    });
  }

  function signOutEverywhere() {
    void run(async () => {
      brew.getState().setDialog(null);
      await auth.getState().logoutEverywhere();
    });
  }

  return (
    <section className="account-security" aria-label="Account security">
      {passwordSet ? (
        <form className="pw" onSubmit={savePassword}>
          <label className="field">
            Current password
            <input className="input px" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" />
          </label>
          <label className="field">
            New password
            <input
              className="input px"
              type="password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              autoComplete="new-password"
              placeholder="12+ characters"
            />
          </label>
          {error && (
            <p className="err" role="alert">
              {error}
            </p>
          )}
          <button className="btn btn-sm px" disabled={busy}>
            Change password
          </button>
        </form>
      ) : (
        <p className="setting-hint">You sign in with Google or GitHub, so there&apos;s no password to change here.</p>
      )}
      <button className="btn btn-sm btn-danger px everywhere" onClick={signOutEverywhere} disabled={busy}>
        Sign out everywhere
      </button>
    </section>
  );
}
