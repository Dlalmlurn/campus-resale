.PHONY: dev down test build backend-test frontend-test

dev:
	docker compose up --build

down:
	docker compose down

test: backend-test frontend-test

build:
	cd backend && mvn package
	cd frontend && npm run build

backend-test:
	cd backend && mvn test

frontend-test:
	cd frontend && npm test -- --run
