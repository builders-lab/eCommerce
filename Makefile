# Convenience wrapper around docker compose.
#
# On Linux/macOS (or Git Bash on Windows):  make up
# On Windows without make, run the equivalent directly:
#     docker compose up --build -d

COMPOSE ?= docker compose
FRONTEND_URL ?= http://localhost:8080

.DEFAULT_GOAL := help

.PHONY: help up down restart logs ps rebuild db-reset db-shell redis-shell backend-shell frontend-shell test clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## Build images and start the whole stack in the background
	$(COMPOSE) up --build -d
	@echo ""
	@echo "  Store     $(FRONTEND_URL)"
	@echo "  Backend   http://localhost:8081/actuator/health"
	@echo ""
	@echo "  Follow logs with: make logs"

down: ## Stop the stack, keeping data volumes
	$(COMPOSE) down

restart: ## Restart the stack
	$(COMPOSE) restart

logs: ## Tail logs from all services (make logs backend to follow one)
	$(COMPOSE) logs -f

ps: ## Show container status
	$(COMPOSE) ps

rebuild: ## Rebuild images from scratch and restart
	$(COMPOSE) up --build -d --force-recreate

db-reset: ## Destroy the database volume and re-run DatabaseInit.sql on next start
	$(COMPOSE) down -v
	$(COMPOSE) up --build -d
	@echo "Database reset. Schema and seed data are reloaded on startup."

db-shell: ## Open a MySQL shell
	$(COMPOSE) exec mysql mysql -uroot -p$${MYSQL_ROOT_PASSWORD:-admin} ecommerce_db

redis-shell: ## Open a Redis CLI
	$(COMPOSE) exec redis redis-cli

backend-shell: ## Open a shell inside the backend container
	$(COMPOSE) exec backend sh

frontend-shell: ## Open a shell inside the frontend container
	$(COMPOSE) exec frontend sh

test: ## Run backend unit tests inside a container (no host JDK or Maven needed)
	docker run --rm -v "$(CURDIR)/backend-services:/workspace" -v m2-cache:/root/.m2 \
		-w /workspace maven:3.9-eclipse-temurin-25 mvn -B test

clean: ## Stop the stack and delete all volumes (destroys the database)
	$(COMPOSE) down -v --remove-orphans