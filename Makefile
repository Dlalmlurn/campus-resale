.PHONY: dev dev-build down be-reload test build backend-test frontend-test seed-goods

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

# 导入公开数据集商品（图片真正上传 MinIO）。用法：
#   make seed-goods DATASET=scripts/seed_goods/datasets/goods.jsonl [IMAGE_MODE=picsum|url|local]
# 详见 scripts/seed_goods/README.md
DATASET ?= scripts/seed_goods/datasets/goods.jsonl
IMAGE_MODE ?= picsum
seed-goods:
	python3 scripts/seed_goods/import_goods.py $(DATASET) --image-mode $(IMAGE_MODE)
