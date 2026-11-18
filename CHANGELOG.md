# Changelog

Newest first. The git history has the full detail.

## 2026-11-18: Spring Boot, React and OAuth 2.0

**Changed**
- The backend is now Spring Boot on Java 21, replacing Go. Every endpoint, JSON field and error message is the same, so nothing the app sends or receives changed.
- The frontend is now React with JavaScript, built with Vite, replacing Angular. The screens, pixel art, stylesheets, sounds and keyboard shortcuts are unchanged.
- Timers run on a per-timer lock with virtual threads instead of a goroutine per timer, and still resume after a restart.
- The schema is versioned with Flyway, and queries go through Spring's JdbcClient instead of GORM.
- `ADDR` is now `PORT` and `BIND_ADDRESS`, and the `prod` Spring profile replaces `GIN_MODE=release`.
- An unknown path under `/api` now answers 401 until you sign in, because Spring Security authorizes before routing.

**Added**
- Sign-in with Google or GitHub over OAuth 2.0 (authorization code with PKCE). Each provider appears once its client ID and secret are set. Provider tokens are discarded, and an email that already has a password account isn't merged.
- `DESIGN.md`, explaining the design decisions and their trade-offs.
- Backend unit tests (JUnit, Mockito) and integration tests against the running app, including a full OAuth 2.0 sign-in against a fake provider.
- Frontend unit tests with Vitest and Testing Library, and ESLint.
- CodeQL now scans the Java, and Dependabot updates the Maven dependencies.

**Left out on purpose**
- Migrating the Go server's database file: the schema is new, the Go server was never hosted publicly, and the Pages site keeps its data in the browser under the same key.

## 2026-11-05: reliable GitHub Pages deploys

**Fixed**
- Direct links and refreshes on the live site returned a 404 status, though the app still appeared. Each route now has a real page, so they return 200.
- A Dependabot pull request could cancel a deploy from `main`, because both shared one concurrency group. Each branch now has its own.

**Added**
- After publishing, CI checks that the live URL is really serving the new build, so a green run means the site works.

## 2026-11-05: a simple Go backend, JWT sign-in and Docker

**Added**
- A Go server, restored from `b2e0356` and made smaller: a goroutine per timer, live updates over Server-Sent Events, Gin, and GORM with SQLite. Go's concurrency suits many timers running at once, and it builds to one static binary.
- JWT sign-in, with the token in an HttpOnly cookie and bcrypt for passwords. A session row behind each token makes logging out instant.
- A multi-stage Docker image and `docker-compose.yml`.
- Comments in every Go package explaining its design decisions.

**Changed**
- The GitHub Pages site stays browser-only, because Pages can't run Go. It shares every screen with the server build, and its in-browser store answers the same API calls.
- The Pages deploy now waits for the Go tests and the browser tests against the Go server.
- The README now explains why each Go tool was chosen.

**Left out on purpose**
- Google sign-in, the demo account, and the time-zone database driver, to keep the backend simple.

## 2026-11-04: browser only, with no sign-in

**Removed**
- Sign-in, including Google, along with accounts and the password rules.
- The Go server, Docker image and sample data. GitHub Pages can't run a server, and one without sign-in would show every visitor the same data.

**Changed**
- Everything is saved in the browser, and a first visit starts empty.
- The timer saves its deadline, so it survives reloads and closed tabs.
- The live site address follows the repo's new name: `/BrewFocus/`.
- The security policy now lives in the page, because GitHub Pages can't send headers.
- The README is now about design choices, with animated diagrams and measured charts.

**Fixed**
- The live site failed to load after the repo rename, and the deploy that would have fixed it was blocked by a browser test that depended on timing. That test is rewritten, and the suite was run under heavy CPU load.

**The trade-off:** data stays on one device and browser, with no sync between them.

## 2026-11-04: back to a simple timer and tracker

**Removed**
- Google Calendar, the Claude assistant, the scheduler and the planning screens.
- `AGENTS.md`, `ARCHITECTURE.md` and the diagrams that described the old design.

**Restored**
- The pixel-art Brew and Progress pages.

**Changed**
- Every page now spans the full width of the screen.
- Light and dark mode stay, with dark as the default and one button to switch.
- The code is organized into folders by feature, with comments on how it works.

## Earlier

- **2026-11-04:** Tried a calendar-first redesign, light and dark mode, and a liquid glass look. The redesign and the glass were later removed.
- **2026-11-03:** Hardened accounts and sessions, added Google sign-in and secret scanning, and pinned dependencies.
- **2026-10:** Added calendar-aware planning with Claude, since removed.
- **2026-09:** Added server-run timers, tasks and progress tracking.
- **2026-01:** First version.
