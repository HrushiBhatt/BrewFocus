# Brew Focus: design decisions

This is the reasoning behind how Brew Focus is built: what I chose, what I turned down, and what each choice costs. The [README](README.md) is the tour; this is the why.

## Contents

1. [What I'm building, and what I'm not](#1-what-im-building-and-what-im-not)
2. [The shape of the system](#2-the-shape-of-the-system)
3. [Why Spring Boot and React](#3-why-spring-boot-and-react)
4. [Backend](#4-backend)
5. [Security](#5-security)
6. [Frontend](#6-frontend)
7. [Testing](#7-testing)
8. [Build, CI and deployment](#8-build-ci-and-deployment)
9. [Moving from Go and Angular](#9-moving-from-go-and-angular)
10. [Trade-offs, and what I'd change at scale](#10-trade-offs-and-what-id-change-at-scale)

## 1. What I'm building, and what I'm not

Brew Focus is a pomodoro timer and a tracker for the time it measures. A block has a shape: set an intention, optionally breathe, focus while a pixel-art mug drains, write one sentence about what got done, then take a break while the mug refills. Tasks, custom modes, streaks, a year heatmap and a small café that fills as you finish full blocks sit around that.

The requirements that drive the design:

- **The timer has to be right.** A block keeps going when the tab closes, survives a server restart, and shows the same seconds on every device.
- **Accounts have to be safe.** People sign in, so passwords, sessions and data belong to them alone.
- **Anyone should be able to try it without signing up.** The GitHub Pages build runs entirely in the browser.
- **It stays simple.** This app once had Google Calendar and an AI planner. I removed both because a timer doesn't need them, and I hold the line on that: no feature or infrastructure goes in unless the timer and tracker need it.

Non-goals: calendar integration, AI features, horizontal scaling, native apps.

## 2. The shape of the system

**One process serves everything.** Spring Boot serves the JSON API under `/api`, the live event stream, the OAuth 2.0 endpoints and the built React app. The browser sees one origin, so there's no CORS to configure, the session cookie is first-party, and there's one thing to deploy.

**Two builds share every screen.** The React app talks to `/api` either way. In the normal build that's the Spring Boot server. In the Pages build, an in-browser store answers the same calls from `localStorage`. The screens can't tell the difference, so there's one UI to maintain, and the Pages site is a real demo rather than a mock.

```
React (Vite)  ──HTTP + JWT cookie──▶  Spring Boot
     ▲                                  ├─ Spring Security filter chain
     │                                  ├─ controllers ─▶ services ─▶ JdbcClient ─▶ SQLite
     └──── Server-Sent Events ◀──────── └─ TimerEngine ─▶ EventHub
```

## 3. Why Spring Boot and React

I moved the backend from Go to Spring Boot and the frontend from Angular to React. The behavior is identical, down to the JSON shapes and error messages. Here's what each side brings.

**Spring Boot (Java 21)**

- **Security I don't have to write.** Spring Security gives a reviewed filter chain for headers, CSRF and authorization, and its OAuth 2.0 client handles the parts of a sign-in that are easy to get subtly wrong: the state parameter, PKCE, token exchange and OpenID Connect ID-token validation. In Go I hand-wrote CSRF, headers and middleware; here I configure them.
- **Virtual threads keep concurrency simple.** Every request, every open event stream and every timer expiry runs on a virtual thread. Code can block on the database without thinking about thread pools, and thousands of open streams cost little more than their buffers. This was Go's big advantage; Java 21 closed most of the gap.
- **The ecosystem fits the problem.** Bean Validation for request bodies, Flyway for migrations, JUnit and Spring's test support for driving the real server, and Testcontainers or PostgreSQL later if I ever need them.

What I gave up: Go built a 27 MB static binary that started instantly in a tiny image. The Spring Boot jar is 43 MB and needs a JVM, so the image is bigger and startup takes about two seconds. For a single long-running server, I'll take that for the security libraries.

**React (JavaScript)**

- **Small components and plain functions.** Each screen is a function component with its own stylesheet, and every rule the UI follows (when the mug is full, what "busy" means) is a pure function I can test without rendering anything.
- **State that lives outside the component tree.** Most changes arrive from the server's event stream, not from a click. Zustand stores sit outside React, receive those events, and let components subscribe to just the slice they show.
- **A fast, simple toolchain.** Vite builds both versions of the app from one flag, and Vitest runs the unit tests with the same configuration.

I kept the frontend in JavaScript rather than TypeScript, with JSDoc types on the API shapes and store factories. The editor still knows the shapes, and there's no compile step or type configuration to maintain.

## 4. Backend

### Packages by feature

`account`, `security` (with `oauth` and `ratelimit`), `timer`, `events`, `preset`, `task`, `journal`, `web` and `config`. Each feature owns its controller, rules and repository, so a change to tasks stays in `task/`. Controllers stay thin: read the request, call a service or repository, write the reply. Rules about time live in `TimerEngine`, rules about passwords in `PasswordPolicy`, and rules about streaks in `StatsCalculator`, so each can be unit-tested alone.

### Storage: SQLite, JdbcClient and Flyway

- **SQLite**, because a single file needs no database server, and one process is all this app needs. WAL mode lets reads proceed during a write, and a busy timeout makes a writer wait instead of failing.
- **One pooled connection.** SQLite allows one writer at a time anyway, and `:memory:` (used by the tests) gives every connection its own empty database.
- **`JdbcClient`, not JPA.** There are about thirty-five queries, all simple. Writing them as SQL keeps them visible in each repository, every value is a bound parameter (so SQL injection is ruled out), and there's no ORM dialect to fight on SQLite. Records map rows to objects.
- **Flyway migrations** version the schema with the code. Instead of the schema being inferred from classes at startup, `V1__schema.sql` states it, comments included.
- **Times are epoch milliseconds.** SQLite has no date type, and numbers compare and sort without parsing.
- **Constraint errors are translated.** Spring doesn't know SQLite's error codes, so `SqliteExceptionTranslator` maps a unique-index violation to `DuplicateKeyException`. That's how two simultaneous sign-ups for one email end as one account plus a clear 409, rather than a 500.
- **Foreign keys cascade from users**, but a session's `task_id` deliberately has none, so deleting a task keeps the time logged against it.

The database file is created readable by its owner only, because it holds password hashes.

### The timer engine

The engine is the part of the app that has to be exactly right.

- **A deadline, not a counter.** A running timer stores when it ends. Time left is always worked out from that deadline, so nothing drifts or is lost to a slow request, a busy CPU or a restart.
- **One lock per timer.** Pause, resume, finish, cancel and distraction commands, and the timer's own alarm, all take the timer's lock. A pause and an expiring deadline that arrive in the same millisecond are applied one after the other, so they can't race. Different users' timers never wait on each other. The lock is a `ReentrantLock` rather than `synchronized` because on Java 21 a virtual thread blocked inside `synchronized` pins its carrier thread, and the work inside the lock writes to the database.
- **One scheduler thread keeps time; virtual threads do the work.** When a deadline arrives, the scheduler starts a virtual thread to finish that block, so a slow write for one user can't delay another user's deadline. The alarm re-checks the deadline under the lock, because a pause or resume may have moved it.
- **Every change is saved as it happens**, and on startup the engine resumes every running or paused block. A block whose deadline passed while the server was down completes at once with full credit: the time really did elapse.
- **Startup and shutdown order is explicit.** The engine and the event hub are `SmartLifecycle` beans. The engine starts (and restores timers) before the web server accepts requests, and stops after the server has drained. The hub stops first of all, closing every event stream so a graceful shutdown isn't held up by connections that would otherwise stay open for hours.
- **One timer per user.** Starting a second is a 409 that carries the real timer state, so an out-of-date tab can catch up from the reply.

### Live updates: Server-Sent Events

- **SSE, not WebSockets.** Clients only listen, since commands go over normal HTTP requests. SSE is plain HTTP, which browsers reconnect to on their own, and it passes through proxies untouched.
- **Each stream opens with a snapshot** of the timer, taken after subscribing, so a reconnecting tab is in sync at once and misses nothing in between.
- **Publishing never blocks.** The hub drops each event into every subscriber's small queue (16 events) and returns. A tab that falls that far behind is closed rather than waited for; its browser reconnects and starts from a fresh snapshot.
- **Streams re-check their session.** Every keepalive (25 seconds) the stream checks that its session row still exists, so "sign out everywhere" also cuts off streams that were already open.

### Requests, validation and errors

- **Request bodies are records** with Bean Validation constraints, and each constraint carries its own message (`"%s must be at most {max} characters"`). `ValidationMessages` reports the first failing field in declaration order, by its JSON name, so the UI can show the sentence as it is.
- **One error shape everywhere:** `{"error": "a sentence"}`. Controller errors go through `ApiExceptionHandler`, filter errors through `JsonReplies`, and anything Tomcat itself rejects through `JsonErrorController`. No stack traces, SQL or class names reach the client.
- **Missing and foreign records are the same 404**, so IDs reveal nothing about other users. A malformed ID or the wrong method on a known path is also a 404, as it was in Go.
- **Configuration comes from environment variables**, bound to one record (`BrewFocusProperties`). Anything invalid stops the server at startup: a short JWT secret, a malformed `TRUSTED_PROXIES` entry, or a missing secret under the `prod` profile.

## 5. Security

The threat model is a public web app with accounts: credential stuffing and password guessing, cross-site request forgery, cross-site scripting, stolen cookies, one user reaching for another's data, and secrets leaking through logs or git.

### Sessions

| Decision | Why |
|---|---|
| A JWT in an **HttpOnly** cookie | Page scripts can't read it, so injected code can't steal it, and the frontend has no token code at all |
| **HS256** with one server-side key | Only this service verifies tokens; public-key signing would add keys to manage for nothing |
| The algorithm is **pinned**, and `exp` and `jti` are **required** | A forged token can't choose `alg: none`, and every token names a session |
| Every JWT names a **`login_sessions` row** | A bare JWT can't be revoked before it expires. Deleting the row makes logout, "sign out everywhere" and password changes take effect at once |
| Signing in again **ends the browser's old session** | An old cookie can't outlive a new sign-in |
| **`SameSite=Lax`**, and **`__Host-`** plus **`Secure`** over HTTPS | Cross-site requests don't carry the cookie, and a sibling subdomain can't plant or overwrite it |
| The server **won't start in production without a strong key** | A guessable key would let anyone forge a session for any user |

### Cross-site requests

Two independent checks, so a mistake in one doesn't open the door:

1. **CSRF double-submit.** The server sets an `XSRF-TOKEN` cookie that page scripts can read, and every POST, PUT, PATCH and DELETE must echo it in `X-XSRF-TOKEN`. Another site can make a browser send the cookie but can't read it to copy into a header. The cookie is set on every response (not lazily), so a fresh page's very first sign-in already has one.
2. **A cross-origin check** (`CrossOriginProtectionFilter`) refuses any state-changing request whose `Sec-Fetch-Site` or `Origin` says it came from another site.

### Passwords

- **Rules follow NIST SP 800-63B:** at least 12 characters, at most 72 bytes (bcrypt's limit, counted in bytes so accented and emoji passwords are measured correctly), and a zxcvbn score of 3 or more. The score counts the account's own email, name and the site's name as easy guesses. There are no composition rules: a few unrelated words beat `P@ssw0rd!`.
- **bcrypt at cost 12.** At most one hash per CPU core runs at a time, so a burst of sign-ins queues instead of starving the timers.
- **Unknown emails take as long as wrong passwords**, through a dummy hash, so timing doesn't reveal who has an account.
- **Changing a password needs the current one**, and ends every other session.
- **Existing passwords aren't re-checked at sign-in**, so tightening the rules never locks anyone out.

### Rate limits

| Limit | Scope | Why |
|---|---|---|
| 600 a minute | everything under `/api`, per address | a blunt cap on floods |
| 20 a minute | sign-in, sign-up and OAuth handshakes, per address | slows password spraying |
| 10 per 10 minutes | sign-in, per account | spreading guesses over many addresses doesn't help |
| 5 per 10 minutes | password changes, per user | a stolen session can't be used to guess the current password |

Limits are keyed on the visitor's real address. `X-Forwarded-For` is believed only from a proxy listed in `TRUSTED_PROXIES`, and read from the right, so a client can't fake its own address.

### OAuth 2.0

- **Authorization code flow with PKCE** for both providers. Spring Security checks the `state` parameter against the one it issued, and for Google validates the OpenID Connect ID token (issuer, audience, signature, nonce).
- **Providers are optional.** Each appears only when its client ID and secret are set, so the app runs exactly as before with none configured.
- **The handshake is the only use of a servlet session.** The state and PKCE verifier live there for the few seconds of the round trip, in a cookie named `bf_oauth` that's HttpOnly and SameSite=Lax. The session is invalidated as soon as sign-in finishes, and from then on the JWT cookie is all there is.
- **Provider tokens are discarded** (`NoTokenStore`). I use OAuth only to learn who someone is, never to call Google or GitHub on their behalf, so keeping tokens would hold something sensitive for no reason.
- **Only verified emails.** Google states `email_verified`; for GitHub I ask its `/user/emails` endpoint for the primary, verified address, because the public profile email may be missing or unverified.
- **No merging accounts by email.** A provider account seen before opens the account it created. A new verified email gets a new account with no password. If the email already has a password account, the sign-in is refused with an explanation. Sign-up here doesn't verify emails, so merging by email would let someone register a victim's address with their own password, then read everything the victim logs after signing in with Google ("pre-account takeover"). Refusing costs a little convenience and closes that hole.
- **`PUBLIC_URL`** states the callback address when the server sits behind a proxy and can't see its own public URL.

### Headers and the page policy

Every response carries a strict Content-Security-Policy (`default-src 'none'; script-src 'self'; …; frame-ancestors 'none'`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a Permissions-Policy, and cross-origin opener and resource policies. HSTS is added whenever the app is configured for HTTPS. The built page carries the same policy as a meta tag, because GitHub Pages can't send headers. API responses are `Cache-Control: no-store`; hashed bundles are cached for a year.

The build is shaped around the policy: no inline scripts (the theme is set by a file, `theme-init.js`), and fonts are never inlined as `data:` URLs.

### Other hardening

- **Request bodies are capped at 256 KB**, by declared length up front and by counting for chunked bodies.
- **Logs carry the method, path, status and time, never the query string or headers.** OAuth callbacks carry one-time codes in the query, and headers carry the session cookie. A test checks this.
- **Every query names the user**, and tests check that one account can't read, change or delete another's data.
- **Supply chain:** GitHub Actions pinned to commits, Docker base images pinned to digests, Dependabot for Maven, npm, actions and images, CodeQL for Java and JavaScript, Trivy on the built image, gitleaks over the whole git history, and `npm audit` on what ships to browsers.

## 6. Frontend

### State: Zustand stores outside React

The app has a few stores: `auth` (the signed-in user), `timer` (the server's timer as this device sees it), `brew` (the session flow and the user's data), `toasts`, `theme` and `ambience`. Each is built by a factory that takes its dependencies (the API client, the event stream, the other stores) as arguments. `state/services.js` wires the real ones once, and tests build their own with fakes. That's dependency injection without a framework.

Components read stores through small hooks (`useBrew`, `useTimerView`, …) and re-render only when the slice they selected changes. What the timer screen shows (phase, clock, progress, the mug's fill, whether modes are locked) comes from pure functions in `state/view.js`, so those rules are tested without rendering.

### The timer on the client

The server owns the clock; the client only renders it. Each timer state carries `server_time`, and the client remembers the difference from its own clock, so the countdown is right even when the device's clock isn't. States that arrive out of order are ignored. The store refreshes four times a second only while a timer runs.

### Two builds from one flag

`vite.config.js` points the `@env` import at `env/server.js` or, with `--mode pages`, at `env/pages.js`. The Pages environment brings the in-browser store and a transport that answers `/api` calls from it. The server build doesn't include that code at all, because nothing in it imports `env/pages.js`. The in-browser store follows the server's rules, and three things keep its tabs in step: `localStorage` is the one source of truth, events reach other tabs over a `BroadcastChannel`, and a Web Lock makes exactly one tab finish a block whose deadline arrives.

### Styles: the original stylesheets, scoped

I kept the pixel-art stylesheets from the Angular app rather than rewriting them. Angular scoped each component's CSS automatically; here each file is wrapped in its component's root class with native CSS nesting (`.timer-stage { … }`, with `:host` becoming `&`), which gives the same scoping with no CSS-in-JS library. The global design tokens, light and dark themes and shared classes (`.panel`, `.btn`, `.px`) are unchanged in `styles/global.css`.

### Accessibility and the platform

Dialogs are native `<dialog>` elements, which give focus trapping, Esc to close and a backdrop. Toasts use the Popover API so they show above an open dialog. Controls have labels, progress is a `progressbar`, and pixel art is hidden from screen readers unless it means something. The tab title counts down and the favicon drains, so a running block is visible from another tab. Every sound is synthesized with the Web Audio API, so there are no audio files to ship or license.

## 7. Testing

The tests sit in three layers, each answering a different question.

| Layer | Tools | What it proves |
|---|---|---|
| **Backend unit** (70) | JUnit 5, AssertJ, Mockito | the rules on their own: the timer state machine (with a hand-moved clock), streaks, password policy, rate limits, client addresses, validation messages, OAuth account rules |
| **Backend integration** (42) | `@SpringBootTest` on a random port, real HTTP | the whole app as a browser meets it: accounts and sessions, CSRF and cross-site checks, headers, forged JWTs, data isolation, the timer over HTTP, the event stream, static files, and a full OAuth 2.0 sign-in against a fake provider |
| **Frontend unit** (55) | Vitest, Testing Library | the stores and their session flow, the API client, the in-browser store, and components the way people use them |
| **Browser** (5 + 15) | Playwright | the built app against the real Spring Boot jar, and the Pages build served the way Pages serves it |

Some choices that keep these honest:

- **Integration tests use a real server and a real HTTP client**, not mocks of Spring. A small `Browser` helper keeps its own cookies and echoes the CSRF token, exactly as the React app does.
- **The OAuth 2.0 test runs the whole flow.** A tiny provider in the test answers token and user-info requests, so the redirect with state and PKCE, the code exchange, the user lookup and the final session cookie are all exercised. It also checks that a forged `state` is rejected.
- **Unit tests split by the clock.** Deadline tests use short real timers; anything about elapsed time moves a `MutableClock` instead of sleeping.
- **Each integration-test browser gets its own client address** through a trusted-proxy header, so tests don't share rate limits.
- Surefire runs `*Test` classes and Failsafe runs `*IT` classes, so `./mvnw verify` runs the fast ones first.

## 8. Build, CI and deployment

- **Docker:** a multi-stage build. Node builds the React app, Maven builds the jar, and only those two results go into a distroless Java 21 image that runs as a non-root user with no shell. Compose runs it read-only, with no extra privileges, a named volume for the database and an in-memory `/tmp`. The heap is sized from the container's memory limit.
- **CI**, on every push: a secret scan, backend tests, frontend lint, tests and build, then browser tests against the real server and a scan of the built image. CodeQL runs alongside. The Pages job waits on all of it except the image scan, so a newly published base-image vulnerability can't take the live site offline.
- **GitHub Pages** serves the browser-only build at `/BrewFocus/`, with a copy of the page at each route so direct links and refreshes return 200. After publishing, CI checks that the live URL really serves the new bundle.

## 9. Moving from Go and Angular

What stayed the same:

- **Every endpoint, request body, JSON field and error message**, so the frontend's API calls didn't change and the Pages store needed no changes to its rules.
- **The security model:** JWT cookie plus session row, bcrypt and zxcvbn, CSRF, the cross-origin check, the rate limits, the headers.
- **The UI:** the same screens, stylesheets, pixel art, sounds and keyboard shortcuts, and the same browser-test scenarios.

What's new or different:

- **OAuth 2.0 sign-in** with Google and GitHub, the `GET /api/auth/providers` endpoint, and a `password_set` field on the user so the settings dialog hides the password form for OAuth-only accounts.
- **An unknown path under `/api` answers 401 to a signed-out visitor**, because Spring Security authorizes before routing. Signed-in users still get a JSON 404. I prefer this: it tells anonymous visitors nothing about which routes exist.
- **The database schema is new** (Flyway-managed, renamed tables, epoch-millisecond times), so a database file from the Go server isn't read. The Go server was never hosted publicly, and the live Pages site keeps its data in the browser under the same storage key, so no real data is affected.
- **Configuration:** `ADDR` became `PORT` and `BIND_ADDRESS`, and `GIN_MODE=release` became the `prod` Spring profile.

## 10. Trade-offs, and what I'd change at scale

This design is for one server. If Brew Focus ever needed several, these are the pieces that would change, and each is isolated so the change stays local:

| Today | At scale | Where |
|---|---|---|
| SQLite, one connection | PostgreSQL with a real pool | `DatabaseConfig`; the SQL is standard |
| Rate limits in memory | Redis or the gateway's limiter | `RateLimiter` |
| Timers in one process | a lease per timer (or one owner per user) in the database | `TimerEngine` |
| Events fanned out in memory | Redis pub/sub or Postgres `LISTEN/NOTIFY` between instances | `EventHub` |
| Email isn't verified at sign-up | verification emails, then safe account linking for OAuth | `AccountService`, `OAuthAccountService` |

Things I chose not to add, because a timer doesn't need them yet: an ORM, a message broker, server-side rendering, a component library, and TypeScript.
