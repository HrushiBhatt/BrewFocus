import { Suspense, lazy, useEffect, useRef } from 'react';
import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router';
import { Sprite } from './components/Sprite';
import { ICONS } from './components/sprites';
import { BreathingDialog } from './features/focus/BreathingDialog';
import { IntentionDialog } from './features/focus/IntentionDialog';
import { ReflectionDialog } from './features/focus/ReflectionDialog';
import { SettingsDialog } from './features/settings/SettingsDialog';
import { useBrewEffects } from './hooks/useBrewEffects';
import { auth, brew, live, theme, useAuth, useBrew, useLive, useTheme, useToasts } from './state/services';
import './App.css';

// Pages load on demand, so the login screen doesn't ship the heatmap.
const LoginPage = lazy(() => import('./features/login/LoginPage'));
const BrewPage = lazy(() => import('./features/brew/BrewPage'));
const ProgressPage = lazy(() => import('./features/progress/ProgressPage'));

/** Pages behind a login. Nothing shows until the first check of the session has answered. */
function RequireAccount({ children }) {
  const user = useAuth((s) => s.user);
  if (user === undefined) return null;
  return user ? children : <Navigate to="/login" replace />;
}

/** The login page, only for signed-out visitors. */
function GuestOnly({ children }) {
  const user = useAuth((s) => s.user);
  if (user === undefined) return null;
  return user ? <Navigate to="/" replace /> : children;
}

/** A new page starts at the top, as a full page load would. */
function useScrollToTopOnNavigate() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);
}

/** The header on every signed-in page: the logo, the two pages, and the account controls. */
function Header({ user }) {
  const connected = useLive((s) => s.connected);
  const current = useTheme((s) => s.current);
  return (
    <header className="header">
      <Link className="logo" to="/">
        <span className="logo-icon" aria-hidden="true">
          ☕
        </span>
        <span className="logo-text">Brew Focus</span>
      </Link>
      <nav className="tabs" aria-label="Views">
        <NavLink className={({ isActive }) => `tab px${isActive ? ' active' : ''}`} to="/" end>
          Brew
        </NavLink>
        <NavLink className={({ isActive }) => `tab px${isActive ? ' active' : ''}`} to="/progress">
          Progress
        </NavLink>
      </nav>
      <div className="account">
        {/* Server status and the signed-in name mean nothing without a server. */}
        {!live.isLocal && (
          <>
            <span
              className={`live${connected ? ' on' : ''}`}
              title={connected ? 'Synced live with your other tabs and devices' : 'Reconnecting to the server…'}
            >
              {connected ? 'LIVE' : 'OFFLINE'}
            </span>
            <span className="user" title={user.email}>
              {user.name}
            </span>
          </>
        )}
        <button
          className="icon-btn head-btn px"
          onClick={() => theme.getState().toggle()}
          aria-label={current === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          <Sprite className="icon" rows={current === 'dark' ? ICONS.sun : ICONS.moon} />
        </button>
        <button className="icon-btn head-btn settings-btn px" onClick={() => brew.getState().setDialog('settings')} aria-label="Settings">
          <Sprite className="icon" rows={ICONS.gear} />
        </button>
      </div>
    </header>
  );
}

/** The small status message. It's a popover, so it shows above any open dialog (both are in the top layer). */
function Toast() {
  const { message, visible } = useToasts((s) => s);
  const ref = useRef(null);
  useEffect(() => {
    const el = ref.current;
    if (!visible || !el.showPopover) return;
    if (el.matches(':popover-open')) el.hidePopover();
    el.showPopover();
  }, [visible, message]);
  return (
    <div ref={ref} className={`toast px${visible ? ' show' : ''}`} popover="manual" role="status" aria-live="polite">
      {message}
    </div>
  );
}

const DIALOGS = {
  intention: IntentionDialog,
  breathe: BreathingDialog,
  reflect: ReflectionDialog,
  settings: SettingsDialog,
};

/** The page frame: the header on every signed-in page, the routed page, and the dialogs. */
export default function App() {
  const user = useAuth((s) => s.user);
  const dialog = useBrew((s) => s.dialog);
  const Dialog = dialog ? DIALOGS[dialog] : null;
  useBrewEffects();
  useScrollToTopOnNavigate();

  useEffect(() => {
    if (auth.getState().user === undefined) void auth.getState().restore();
  }, []);

  return (
    <div className="app">
      {user && <Header user={user} />}
      <Suspense fallback={null}>
        <Routes>
          <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
          <Route path="/" element={<RequireAccount><BrewPage /></RequireAccount>} />
          <Route path="/progress" element={<RequireAccount><ProgressPage /></RequireAccount>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
      {Dialog && <Dialog />}
      <Toast />
    </div>
  );
}
