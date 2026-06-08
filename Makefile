.PHONY: dev dev-build down be-reload test build backend-test frontend-test

dev:
	docker compose up

dev-build:
	docker compose up --build

down:
	docker compose down

be-reload:
	docker compose exec backend mvn -B -s settings.xml compile

test: backend-test frontend-test

build:
	cd backend && mvn package
	cd frontend && npm run build

backend-test:
	cd backend && mvn test

frontend-test:
	cd frontend && npm test -- --run
