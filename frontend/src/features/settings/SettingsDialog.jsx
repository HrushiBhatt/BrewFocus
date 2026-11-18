import { Modal } from '../../components/Modal';
import { auth, brew, browserOnly, useAuth, useSettings } from '../../state/services';
import { AccountSecurity } from './AccountSecurity';
import './SettingsDialog.css';

const TOGGLES = [
  { key: 'sound', label: 'Sound effects', hint: '8-bit chime when a focus block or break runs out.' },
  { key: 'notify', label: 'Notifications', hint: 'Ping me when time is up and this tab is in the background.' },
  { key: 'autoBreak', label: 'Auto-start breaks', hint: 'Begin the break as soon as you finish your reflection.' },
  { key: 'strict', label: 'Strict mode', hint: 'Log every tab switch mid-focus as a distraction, and ask before closing the tab.' },
];

/** Preferences, saved as they change, and the account. The Pages build has no account. */
export function SettingsDialog() {
  const settings = useSettings();
  const email = useAuth((s) => s.user?.email);
  const close = () => brew.getState().setDialog(null);

  async function logout() {
    close();
    await auth.getState().logout();
  }

  return (
    <div className="settings-dialog">
      <Modal title="Settings" onClose={close}>
        {TOGGLES.map((t) => (
          <label className="setting" key={t.key}>
            <span className="setting-label">{t.label}</span>
            <span className="setting-hint">{t.hint}</span>
            <input
              type="checkbox"
              className="switch"
              checked={settings[t.key]}
              onChange={(e) => void brew.getState().updateSettings({ [t.key]: e.target.checked })}
            />
          </label>
        ))}
        <p className="shortcuts">
          <kbd>Space</kbd> start / pause · <kbd>R</kbd> reset · <kbd>S</kbd> finish or skip
        </p>
        {browserOnly ? (
          <p className="setting-hint">Everything stays in this browser and is never sent anywhere. Clearing this site&apos;s data deletes it.</p>
        ) : (
          <>
            <p className="setting-hint">Settings are saved to your account, so they follow you to every device.</p>
            <div className="account">
              <span>
                Signed in as <b>{email}</b>
              </span>
              <button className="btn btn-sm px" onClick={logout}>
                Log out
              </button>
            </div>
            <AccountSecurity />
          </>
        )}
        <button className="btn btn-danger px" onClick={() => void brew.getState().clearData()}>
          Delete all sessions
        </button>
      </Modal>
    </div>
  );
}
