# ─────────────────────────────────────────────────────────────
#  Weekly Commit — local dev runner
#  Usage:
#    make dev            build deps, create DB, start everything
#    make setup          install deps + create DB only
#    make stop           kill processes on :8080 and :5173
#    make clean          stop + wipe build artifacts
#    make reseed         wipe runtime data + restore demo seed (no restart needed)
#    make reset          clean + drop DB + setup (true blank slate)
#    make check          lint + typecheck + tests
#    make model-compare  run N-way model leaderboard  (requires OPENROUTER_API_KEY)
#    make ab-eval        run 2-model A/B comparison   (requires OPENROUTER_API_KEY)
#    make perf           run backend + frontend performance benchmarks
# ─────────────────────────────────────────────────────────────

# ── Load .env (PINECONE_API_KEY, OPENROUTER_API_KEY, DB_USER, …) ─────────────
# -include silently skips if .env is absent; export forwards all vars to children.
-include .env
export

.PHONY: dev setup stop clean reset reseed check check-deps

# ── tool paths (keg-only Homebrew installs) ───────────────────
JAVA_HOME   := /opt/homebrew/opt/openjdk@21
PSQL        := /opt/homebrew/opt/postgresql@16/bin/psql

# ── configurable — override on the command line if needed ─────
#   e.g.  make dev DB_USER=myuser DB_PASS=secret
DB_NAME   ?= weekly_commit
DB_USER   ?= $(shell whoami)   # Homebrew postgres defaults to your macOS user
DB_PASS   ?=                   # Homebrew postgres uses trust auth (no password)
BACKEND_PORT  ?= 8080
FRONTEND_PORT ?= 5173

# ── colours ───────────────────────────────────────────────────
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
BOLD   := \033[1m
NC     := \033[0m

export JAVA_HOME

.DEFAULT_GOAL := dev

# ── prereq check ──────────────────────────────────────────────
check-deps:
	@printf "$(BOLD)Checking prerequisites...$(NC)\n"
	@$(JAVA_HOME)/bin/java -version 2>&1 | grep -qE '"21\.' \
		|| { printf "$(RED)✗ Java 21 not found at $(JAVA_HOME)$(NC)\n"; exit 1; }
	@printf "$(GREEN)✓ Java 21 ($(JAVA_HOME))$(NC)\n"
	@node --version >/dev/null 2>&1 \
		|| { printf "$(RED)✗ Node.js not found$(NC)\n"; exit 1; }
	@printf "$(GREEN)✓ Node $$(node --version)$(NC)\n"
	@$(PSQL) --version >/dev/null 2>&1 \
		|| { printf "$(RED)✗ psql not found at $(PSQL)$(NC)\n"; exit 1; }
	@printf "$(GREEN)✓ PostgreSQL 16 ($(PSQL))$(NC)\n"

# ── setup: deps + DB ──────────────────────────────────────────
setup: check-deps
	@printf "\n$(BOLD)Installing Node dependencies...$(NC)\n"
	npm install

	@printf "\n$(BOLD)Ensuring 'postgres' role exists...$(NC)\n"
	@$(PSQL) -U $(shell whoami) postgres -tc \
		"SELECT 1 FROM pg_roles WHERE rolname='postgres'" \
		| grep -q 1 \
		&& printf "$(GREEN)✓ postgres role already exists$(NC)\n" \
		|| ( $(PSQL) -U $(shell whoami) postgres \
			-c "CREATE ROLE postgres SUPERUSER LOGIN;" \
			&& printf "$(GREEN)✓ postgres role created$(NC)\n" )

	@printf "\n$(BOLD)Ensuring database '$(DB_NAME)' exists...$(NC)\n"
	@$(PSQL) -U $(DB_USER) postgres -tc \
		"SELECT 1 FROM pg_database WHERE datname='$(DB_NAME)'" \
		| grep -q 1 \
		&& printf "$(GREEN)✓ Database already exists$(NC)\n" \
		|| ( $(PSQL) -U $(DB_USER) postgres \
			-c "CREATE DATABASE $(DB_NAME);" \
			&& printf "$(GREEN)✓ Database created$(NC)\n" )

	@printf "\n$(GREEN)$(BOLD)Setup complete — run 'make dev' to start$(NC)\n"

# ── dev: setup then run everything ────────────────────────────
dev: setup
	@printf "\n$(BOLD)Starting services...$(NC)\n"
	@printf "  $(GREEN)backend $(NC) → http://localhost:$(BACKEND_PORT)\n"
	@printf "  $(GREEN)frontend$(NC) → http://localhost:$(FRONTEND_PORT)\n"
	@printf "$(YELLOW)Ctrl+C stops both$(NC)\n\n"
	DB_USER=$(DB_USER) DB_PASSWORD=$(DB_PASS) APP_SCHEDULING_ENABLED=false JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH npm run dev

# ── stop: kill by port ────────────────────────────────────────
stop:
	@printf "$(YELLOW)Stopping services on :$(BACKEND_PORT) and :$(FRONTEND_PORT)...$(NC)\n"
	@lsof -ti:$(BACKEND_PORT) | xargs kill -9 2>/dev/null \
		&& printf "$(GREEN)✓ backend stopped$(NC)\n" \
		|| printf "  backend was not running\n"
	@lsof -ti:$(FRONTEND_PORT) | xargs kill -9 2>/dev/null \
		&& printf "$(GREEN)✓ frontend stopped$(NC)\n" \
		|| printf "  frontend was not running\n"

# ── clean: stop + wipe build output ───────────────────────────
clean: stop
	@printf "\n$(YELLOW)Cleaning build artifacts...$(NC)\n"
	rm -rf frontend/dist packages/shared/dist node_modules/.cache
	JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH cd backend && ./gradlew clean --quiet
	@printf "$(GREEN)✓ Clean complete$(NC)\n"

# ── reseed: wipe runtime data + restore demo seed (backend can stay running) ──
reseed:
	@printf "\n$(BOLD)Wiping runtime data...$(NC)\n"
	@$(PSQL) -U $(DB_USER) $(DB_NAME) -f scripts/reseed.sql -q \
		&& printf "$(GREEN)✓ Runtime data cleared, static seed restored$(NC)\n" \
		|| { printf "$(RED)✗ Cleanup failed — is the DB running?$(NC)\n"; exit 1; }
	@printf "$(BOLD)Restoring rich demo seed (V11)...$(NC)\n"
	@$(PSQL) -U $(DB_USER) $(DB_NAME) \
		-f backend/src/main/resources/db/migration/V11__rich_demo_seed.sql -q \
		&& printf "$(GREEN)✓ Demo seed restored$(NC)\n" \
		|| { printf "$(RED)✗ Demo seed failed$(NC)\n"; exit 1; }
	@printf "\n$(GREEN)$(BOLD)✓ Reseed complete — refresh the browser$(NC)\n\n"

# ── reset: drop DB + clean + setup ────────────────────────────
reset: clean
	@printf "\n$(BOLD)Dropping database '$(DB_NAME)'...$(NC)\n"
	@$(PSQL) -U $(DB_USER) postgres \
		-c "DROP DATABASE IF EXISTS $(DB_NAME);" 2>/dev/null \
		&& printf "$(GREEN)✓ Database dropped$(NC)\n" || true
	$(MAKE) setup
	@printf "\n$(GREEN)$(BOLD)Reset complete — run 'make dev' to start$(NC)\n"

# ── check: lint + typecheck + tests ───────────────────────────
check:
	@printf "\n$(BOLD)Running lint, typecheck, and tests...$(NC)\n"
	JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH npm run lint
	npm run typecheck
	DB_USER=$(DB_USER) DB_PASSWORD=$(DB_PASS) JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH npm run test

# ── model-compare: N-way leaderboard across all eval datasets ─
#   Ranks models by composite score (schema + quality + speed).
#   Results: backend/build/eval-results/model-compare-*.json
#
#   Override the model list:
#     COMPARE_MODELS="openai/gpt-4o,google/gemini-2.5-flash" make model-compare
model-compare:
	@printf "\n$(BOLD)Running model comparison leaderboard...$(NC)\n"
	@printf "  Models: $(YELLOW)$${COMPARE_MODELS:-default 7-model roster}$(NC)\n"
	@[ -n "$$OPENROUTER_API_KEY" ] || { printf "$(RED)✗ OPENROUTER_API_KEY not set$(NC)\n"; exit 1; }
	DB_USER=$(DB_USER) DB_PASSWORD=$(DB_PASS) JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH 		cd backend && ./gradlew modelCompareTest --info 2>&1 | grep -E "ModelEval|leaderboard|RANK|─|★|#[0-9]|BEST|FAST|QUALITY|Report:" || true
	@printf "\n$(GREEN)$(BOLD)✓ Report: backend/build/eval-results/$(NC)\n"

# ── ab-eval: classic 2-model A/B comparison ───────────────────
#   Compare exactly two models head-to-head. Set env vars:
#     AB_CONTROL_MODEL   (default: anthropic/claude-sonnet-4-20250514)
#     AB_TREATMENT_MODEL (default: google/gemini-2.5-flash-preview)
ab-eval:
	@printf "\n$(BOLD)Running A/B model comparison...$(NC)\n"
	@printf "  Control:   $(YELLOW)$${AB_CONTROL_MODEL:-anthropic/claude-sonnet-4-20250514}$(NC)\n"
	@printf "  Treatment: $(YELLOW)$${AB_TREATMENT_MODEL:-google/gemini-2.5-flash-preview}$(NC)\n"
	@[ -n "$$OPENROUTER_API_KEY" ] || { printf "$(RED)✗ OPENROUTER_API_KEY not set$(NC)\n"; exit 1; }
	DB_USER=$(DB_USER) DB_PASSWORD=$(DB_PASS) JAVA_HOME=$(JAVA_HOME) PATH=$(JAVA_HOME)/bin:$$PATH 		cd backend && ./gradlew abEvalTest
	@printf "\n$(GREEN)$(BOLD)✓ Report: backend/build/eval-results/$(NC)\n"

# ─────────────────────────────────────────────────────────────
#  Docker targets  (requires Docker Desktop or Docker Engine)
# ─────────────────────────────────────────────────────────────
.PHONY: perf docker-build docker-up docker-down docker-logs docker-reset

## Performance benchmarks (requires running dev stack)
perf:
	@printf "\n$(BOLD)Running backend API benchmarks (100 iterations)...$(NC)\n"
	./scripts/perf-benchmark-api.sh 100
	@printf "\n$(BOLD)Running frontend page load benchmarks (20 iterations)...$(NC)\n"
	PERF_ITERATIONS=20 npx playwright test --config=e2e/playwright.config.ts e2e/perf-benchmark.spec.ts
	@printf "\n$(GREEN)✓ Results: scripts/perf-results-api.json + scripts/perf-results-frontend.json$(NC)\n"
	@printf "See docs/performance-benchmarks.md for analysis.\n"

## Build all images (no cache: make docker-build CACHE=--no-cache)
docker-build:
	@printf "\n$(BOLD)Building Docker images...$(NC)\n"
	docker compose build $(CACHE)

## Build + start everything (http://localhost → frontend, :8080 → backend)
docker-up:
	@printf "\n$(BOLD)Starting via Docker Compose...$(NC)\n"
	@printf "  $(GREEN)frontend$(NC) → http://localhost\n"
	@printf "  $(GREEN)backend $(NC) → http://localhost:8080\n"
	@printf "$(YELLOW)Run 'make docker-down' to stop$(NC)\n\n"
	docker compose up --build

## Stop and remove containers (keeps the postgres volume)
docker-down:
	@printf "\n$(YELLOW)Stopping Docker services...$(NC)\n"
	docker compose down
	@printf "$(GREEN)✓ Done (postgres volume preserved)$(NC)\n"

## Stream logs from all containers
docker-logs:
	docker compose logs -f

## Full reset: stop, delete volume, rebuild, start fresh
docker-reset:
	@printf "\n$(YELLOW)Resetting Docker environment (drops DB volume)...$(NC)\n"
	docker compose down -v
	docker compose up --build
