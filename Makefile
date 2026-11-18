.PHONY: install api web build serve test e2e lint audit docker pages pages-build

# Secrets and local config (JWT_SECRET, OAuth clients, ...) come from an untracked .env; see .env.example.
-include .env
export JWT_SECRET SECURE_COOKIES TRUSTED_PROXIES PUBLIC_URL PORT BIND_ADDRESS DB_PATH \
	GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET

# Switch to the Node version in .nvmrc when nvm is installed, so frontend targets work even in a
# terminal still holding an older Node on its PATH.
NVM_SH = $${NVM_DIR:-$$HOME/.nvm}/nvm.sh
# CI sets up its own Node, and GitHub's runners have an nvm that doesn't know that version.
NODE = if [ -z "$$CI" ] && [ -s "$(NVM_SH)" ]; then . "$(NVM_SH)" --no-use && nvm use --silent >/dev/null \
	|| { echo "Node $$(cat .nvmrc) isn't installed. Run: nvm install" >&2; exit 1; }; fi;

# The backend needs Java 21. On macOS, pick it even when a newer default JDK is installed.
JAVA = if [ -z "$$CI" ] && [ -x /usr/libexec/java_home ]; then export JAVA_HOME="$$(/usr/libexec/java_home -v 21)"; fi;
MVN = $(JAVA) cd backend && ./mvnw -q -B

install: ## fetch Maven and npm dependencies
	$(MVN) dependency:go-offline
	$(NODE) cd frontend && npm install

api: ## Spring Boot API on :5001 (Ctrl+C shuts down gracefully; running timers resume on restart)
	$(MVN) spring-boot:run

web: ## React dev server on :4200, proxying /api and the OAuth 2.0 endpoints to :5001
	$(NODE) cd frontend && npm run dev

build: ## production frontend bundle + the backend jar
	$(NODE) cd frontend && npm run build
	$(MVN) -DskipTests package

serve: build ## one process: Spring Boot serves the API and the built app on :5001 (needs JWT_SECRET)
	$(JAVA) cd backend && SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-prod} STATIC_DIR=../frontend/dist \
		java -jar target/brew-focus.jar

test: ## backend unit + integration tests, then frontend lint and unit tests
	$(MVN) verify
	$(NODE) cd frontend && npm run lint && npm test

e2e: ## browser tests against the real Spring Boot server
	$(NODE) $(JAVA) cd frontend && npm run e2e

lint: ## the frontend's ESLint rules
	$(NODE) cd frontend && npm run lint

audit: ## the security scans CI runs: vulnerable dependencies and leaked secrets (gitleaks, Trivy when installed)
	$(NODE) cd frontend && npm audit --omit=dev --audit-level=high
	@if command -v gitleaks >/dev/null; then gitleaks git --redact --no-banner --config .gitleaks.toml .; \
	else echo "gitleaks isn't installed, so history wasn't scanned for secrets here (CI scans it)."; fi
	@if command -v trivy >/dev/null; then trivy fs --scanners vuln --severity HIGH,CRITICAL backend frontend; \
	else echo "Trivy isn't installed, so the Maven dependencies weren't scanned here (CI scans them)."; fi

docker: ## build and run the production image with docker compose, on :5001
	docker compose up --build

pages: ## the browser-only GitHub Pages build on :4200, with no server
	$(NODE) cd frontend && npx vite --mode pages

# The site CI publishes. BASE must be the repo's path on github.io, or nothing loads. CI works it
# out from the repo's name, so renaming the repo can't leave the live site blank.
BASE ?= /BrewFocus/
pages-build: ## the GitHub Pages site, in frontend/dist; test it with `cd frontend && npm run e2e:pages`
	$(NODE) cd frontend && npx vite build --mode pages --base "$(BASE)"
	# Pages has no server to send every path to the app. A copy of the page for each route makes
	# direct links and refreshes a real 200, and 404.html still boots the app on anything else.
	# .nojekyll serves files as they are.
	cd frontend/dist && cp index.html 404.html && touch .nojekyll && \
		for route in progress login; do mkdir -p "$$route" && cp index.html "$$route/index.html"; done
