# 商品演示数据导入器（公开数据集 → 真实图片入 MinIO）

解决意见 23 / Q2：原 `V24` 用 `generate_series` 造了 1000 条 "演示商品 NNNN" 且图片是死链
（只插 `stored_files` 行、MinIO 无对象，导致 `/api/files` 刷屏 404）。`V25` 迁移已删除那批假数据；
真实演示数据改由本导入器从**公开数据集**导入，并把图片**真正上传到 MinIO**，不再有死链。

## 它做什么

读取一个 JSONL 数据集（每行一个商品），对每条：
1. 取图片（三选一，见下）→ `PUT` 到 MinIO `campus-resale-dev` 桶；
2. 插入 `goods`（`ON_SALE` + `APPROVED`）+ `stored_files`（`GOODS_IMAGE/PUBLIC/APPROVED`）+ `goods_images`；
3. 幂等：按稳定 `external_id` 落 `storage_key=seed/import/{id}`，已存在则跳过，可重复运行。

导入的商品 `trade_place_detail` 标记为 `公开数据集导入`，方便将来定向清理。

## 前置条件

- 开发栈已启动：`make dev`（Postgres 映射宿主 `15432`、MinIO 映射 `9000`）。
- Python 3.10+，安装依赖：
  ```bash
  pip install -r scripts/seed_goods/requirements.txt
  ```
- 运行机器能访问外网（`picsum`/`image_url` 模式需要；`local` 模式不需要）。

## JSONL 数据集格式

每行一个对象（`title` 必填，其余可缺省）：

```json
{"external_id":"可选","title":"标题","description":"描述","price":12.5,
 "condition":"NEW|LIKE_NEW|LIGHTLY_USED|NOTICEABLY_USED","category_code":"对应 categories.code",
 "image_url":"url 模式用","image_file":"local 模式用，相对 --images-dir 的文件名"}
```

`datasets/format_example.jsonl` 只是**格式示例**，请用真实数据集替换。

## 推荐数据集：Mercari（真实二手交易平台公开数据）

Kaggle `mercari-price-suggestion-challenge` 的 `train.tsv` 是真实二手商品的标题/成色/分类/价格/描述，
正好替代机械文案。它不含图片，导入时用 `picsum` 取真实占位照片即可。

```bash
# 1) 从 Kaggle 下载并解压得到 train.tsv
# 2) 转成导入器 JSONL（可 --limit 控制量）
python scripts/seed_goods/convert_mercari.py /path/to/train.tsv --limit 1000 \
    > scripts/seed_goods/datasets/goods.jsonl
# 3) 导入（图片走 picsum：真实、各异、可用）
python scripts/seed_goods/import_goods.py scripts/seed_goods/datasets/goods.jsonl --image-mode picsum
```

也可以用任何其它公开数据集，只要先转成上面的 JSONL schema。

## 图片来源（`--image-mode`）

- `picsum`（默认）：用 `external_id` 作稳定 seed 从 Lorem Picsum 取真实照片，保证可用且各不相同
  （图片与商品语义不强相关，但彻底消除死链）。
- `url`：下载记录里的 `image_url`（数据集自带真实商品图时用，语义最贴合）。
- `local`：从 `--images-dir` 按 `image_file` 取本地图片（完全离线）。

## 常用命令

```bash
make seed-goods DATASET=scripts/seed_goods/datasets/goods.jsonl            # 默认 picsum
make seed-goods DATASET=scripts/seed_goods/datasets/goods.jsonl IMAGE_MODE=url
python scripts/seed_goods/import_goods.py <jsonl> --seller seller_demo --limit 50   # 先小批试跑
```

连接参数（默认对齐 compose 本机端口）可用环境变量覆盖：
`SEED_DB_HOST/PORT/NAME/USER/PASSWORD`、`SEED_S3_ENDPOINT/BUCKET/ACCESS_KEY/SECRET_KEY`、`SEED_SELLER`。

## 注意

- 卖家默认 `seller_demo`，需是可发布（已认证）账号。
- Mercari 价格单位是美元，仅作演示数量级；如需人民币口径可在转换时换算。
- 商品分类：Mercari 分类与本系统 `categories.code` 不一致，导入器会回退到首个可用分类；
  如需精确归类，可在转换脚本里补一张分类映射表。
