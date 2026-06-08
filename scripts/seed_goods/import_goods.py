#!/usr/bin/env python3
"""校园二手集市 · 公开数据集商品导入器。

作用：把一个 JSONL 数据集导入为公开在售商品，并把每条商品的图片**真正上传到 MinIO**，
彻底消除 V24 那种"只插 stored_files 行、对象不存在"导致的 /api/files 死链。

设计要点：
- 数据集与代码解耦：只认一个固定 JSONL schema（见下），任何公开数据集先转成该 schema 再导入
  （示例转换器见 convert_mercari.py）。文本来自真实公开数据集，不是脚本编造。
- 图片三种来源（image_mode）：
    url    —— 用记录里的 image_url 下载真实图片；
    local  —— 从 --images-dir 按 image_file 取本地图片；
    picsum —— 无图片源时，用 Lorem Picsum 按稳定 seed 取真实照片（保证可用、各不相同）。
- 幂等：每条记录算一个稳定 external_id，落到 storage_key = seed/import/{external_id}.<ext>；
  已存在同 key 的 stored_files 则跳过，可安全重复运行。
- 导入的商品 trade_place_detail 统一标记为 '公开数据集导入'，便于将来定向清理。

JSONL 每行一个对象（字段缺省见代码）：
    {
      "external_id":   "可选，缺省用 title+price 的 hash",
      "title":         "商品标题（必填）",
      "description":   "商品描述",
      "price":         "12.50  或数字",
      "condition":     "NEW | LIKE_NEW | LIGHTLY_USED | NOTICEABLY_USED",
      "category_code": "对应 categories.code，找不到则用首个可用分类",
      "image_url":     "image_mode=url 时使用",
      "image_file":    "image_mode=local 时，相对 --images-dir 的文件名"
    }

连接参数全部可用环境变量覆盖，默认对齐 compose.yaml 的本机端口映射。
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import mimetypes
import os
import sys
from dataclasses import dataclass

try:
    import boto3
    from botocore.client import Config
    import psycopg
    import requests
except ImportError as exc:  # pragma: no cover - 仅给出友好提示
    sys.exit(f"缺少依赖：{exc}. 请先 `pip install -r scripts/seed_goods/requirements.txt`")


VALID_CONDITIONS = {"NEW", "LIKE_NEW", "LIGHTLY_USED", "NOTICEABLY_USED"}


@dataclass
class Settings:
    dsn: str
    s3_endpoint: str
    s3_bucket: str
    s3_access_key: str
    s3_secret_key: str
    seller_username: str
    image_mode: str
    images_dir: str | None
    limit: int | None
    dataset: str


def load_settings(args: argparse.Namespace) -> Settings:
    db_host = os.environ.get("SEED_DB_HOST", "127.0.0.1")
    db_port = os.environ.get("SEED_DB_PORT", "15432")  # compose 把容器 5432 映射到宿主 15432
    db_name = os.environ.get("SEED_DB_NAME", "campus_resale")
    db_user = os.environ.get("SEED_DB_USER", "campus_resale")
    db_pass = os.environ.get("SEED_DB_PASSWORD", "campus_resale_dev_password")
    return Settings(
        dsn=f"host={db_host} port={db_port} dbname={db_name} user={db_user} password={db_pass}",
        s3_endpoint=os.environ.get("SEED_S3_ENDPOINT", "http://127.0.0.1:9000"),
        s3_bucket=os.environ.get("SEED_S3_BUCKET", "campus-resale-dev"),
        s3_access_key=os.environ.get("SEED_S3_ACCESS_KEY", "campus_resale_minio"),
        s3_secret_key=os.environ.get("SEED_S3_SECRET_KEY", "campus_resale_minio_password"),
        seller_username=args.seller,
        image_mode=args.image_mode,
        images_dir=args.images_dir,
        limit=args.limit,
        dataset=args.dataset,
    )


def s3_client(settings: Settings):
    # MinIO 必须用 path-style 寻址，且 region 任意。
    return boto3.client(
        "s3",
        endpoint_url=settings.s3_endpoint,
        aws_access_key_id=settings.s3_access_key,
        aws_secret_access_key=settings.s3_secret_key,
        region_name="us-east-1",
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )


def ensure_bucket(s3, bucket: str) -> None:
    existing = {b["Name"] for b in s3.list_buckets().get("Buckets", [])}
    if bucket not in existing:
        s3.create_bucket(Bucket=bucket)


def external_id_of(record: dict) -> str:
    raw = record.get("external_id")
    if raw:
        return str(raw)
    basis = f"{record.get('title','')}|{record.get('price','')}".encode("utf-8")
    return hashlib.sha1(basis).hexdigest()[:16]


def fetch_image(record: dict, settings: Settings) -> tuple[bytes, str]:
    """返回 (图片字节, content_type)。失败抛异常，由调用方决定跳过。"""
    if settings.image_mode == "local":
        name = record.get("image_file")
        if not name or not settings.images_dir:
            raise ValueError("local 模式需要 image_file 和 --images-dir")
        path = os.path.join(settings.images_dir, name)
        with open(path, "rb") as handle:
            data = handle.read()
        return data, mimetypes.guess_type(path)[0] or "image/jpeg"

    if settings.image_mode == "url":
        url = record.get("image_url")
        if not url:
            raise ValueError("url 模式需要 image_url")
    else:  # picsum：用 external_id 作为稳定 seed，拿到真实且各异的照片
        url = f"https://picsum.photos/seed/{external_id_of(record)}/800/600"

    response = requests.get(url, timeout=20)
    response.raise_for_status()
    return response.content, response.headers.get("Content-Type", "image/jpeg")


def normalize(record: dict) -> dict | None:
    title = (record.get("title") or "").strip()
    if not title:
        return None
    condition = (record.get("condition") or "LIGHTLY_USED").upper()
    if condition not in VALID_CONDITIONS:
        condition = "LIGHTLY_USED"
    try:
        price = round(float(record.get("price") or 0), 2)
    except (TypeError, ValueError):
        price = 0.0
    if price <= 0:
        price = 9.9
    description = (record.get("description") or title).strip()
    return {
        "external_id": external_id_of(record),
        "title": title[:120],
        "description": description[:2000],
        "price": price,
        "condition": condition,
        "category_code": (record.get("category_code") or "").strip(),
        "image_url": record.get("image_url"),
        "image_file": record.get("image_file"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="导入公开数据集商品并把图片上传到 MinIO")
    parser.add_argument("dataset", help="JSONL 数据集路径，每行一个商品对象")
    parser.add_argument("--seller", default=os.environ.get("SEED_SELLER", "seller_demo"),
                        help="挂载商品的卖家用户名（需已认证可发布）")
    parser.add_argument("--image-mode", choices=["picsum", "url", "local"], default="picsum",
                        dest="image_mode")
    parser.add_argument("--images-dir", default=None, dest="images_dir",
                        help="local 模式下的本地图片目录")
    parser.add_argument("--limit", type=int, default=None, help="最多导入条数，便于试跑")
    args = parser.parse_args()

    settings = load_settings(args)
    s3 = s3_client(settings)
    ensure_bucket(s3, settings.s3_bucket)

    inserted = skipped = failed = 0
    with psycopg.connect(settings.dsn) as conn:
        seller_id = lookup_one(conn, "SELECT id FROM users WHERE username = %s", (settings.seller_username,))
        if seller_id is None:
            sys.exit(f"找不到卖家 {settings.seller_username}")
        default_category = lookup_one(
            conn,
            "SELECT id FROM categories WHERE enabled = TRUE AND prohibited_flag = FALSE ORDER BY sort_order, id LIMIT 1",
            (),
        )
        default_place = lookup_one(
            conn, "SELECT id FROM campus_places WHERE enabled = TRUE ORDER BY sort_order, id LIMIT 1", ()
        )

        with open(settings.dataset, "r", encoding="utf-8") as handle:
            for line_no, line in enumerate(handle, start=1):
                line = line.strip()
                if not line:
                    continue
                if settings.limit is not None and inserted >= settings.limit:
                    break
                try:
                    record = normalize(json.loads(line))
                except json.JSONDecodeError:
                    failed += 1
                    print(f"[{line_no}] JSON 解析失败，跳过")
                    continue
                if record is None:
                    skipped += 1
                    continue

                storage_key = f"seed/import/{record['external_id']}"
                if stored_file_exists(conn, settings.s3_bucket, storage_key):
                    skipped += 1
                    continue

                try:
                    data, content_type = fetch_image(record, settings)
                except Exception as exc:  # noqa: BLE001 - 单条失败不应中断整批
                    failed += 1
                    print(f"[{line_no}] 取图失败（{exc}），跳过 {record['title']!r}")
                    continue

                ext = ".png" if "png" in content_type else ".jpg"
                key = storage_key + ext
                s3.upload_fileobj(io.BytesIO(data), settings.s3_bucket, key,
                                  ExtraArgs={"ContentType": content_type})

                category_id = resolve_category(conn, record["category_code"]) or default_category
                insert_goods_with_image(
                    conn,
                    seller_id=seller_id,
                    category_id=category_id,
                    place_id=default_place,
                    record=record,
                    bucket=settings.s3_bucket,
                    key=key,
                    content_type=content_type,
                    byte_size=len(data),
                    checksum=hashlib.sha256(data).hexdigest(),
                )
                conn.commit()
                inserted += 1
                if inserted % 50 == 0:
                    print(f"已导入 {inserted} 条…")

    print(f"完成：导入 {inserted}，跳过 {skipped}，失败 {failed}")
    return 0


def lookup_one(conn, sql: str, params: tuple):
    with conn.cursor() as cur:
        cur.execute(sql, params)
        row = cur.fetchone()
        return row[0] if row else None


def resolve_category(conn, code: str):
    if not code:
        return None
    return lookup_one(conn, "SELECT id FROM categories WHERE code = %s AND enabled = TRUE", (code,))


def stored_file_exists(conn, bucket: str, key_prefix: str) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT 1 FROM stored_files WHERE storage_bucket = %s AND storage_key LIKE %s LIMIT 1",
            (bucket, key_prefix + "%"),
        )
        return cur.fetchone() is not None


def insert_goods_with_image(conn, *, seller_id, category_id, place_id, record, bucket, key,
                            content_type, byte_size, checksum) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO goods (
                seller_id, category_id, title, description, condition_level, list_price,
                trade_place_id, trade_place_detail, available_time_text,
                status, audit_status, published_at, created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s,
                %s, '公开数据集导入', '工作日傍晚',
                'ON_SALE', 'APPROVED', now(), now(), now()
            ) RETURNING id
            """,
            (seller_id, category_id, record["title"], record["description"],
             record["condition"], record["price"], place_id),
        )
        goods_id = cur.fetchone()[0]
        cur.execute(
            """
            INSERT INTO stored_files (
                storage_bucket, storage_key, original_name, content_type, byte_size, checksum,
                file_kind, visibility_scope, owner_user_id, business_type, business_id,
                audit_status, created_at
            ) VALUES (
                %s, %s, %s, %s, %s, %s,
                'GOODS_IMAGE', 'PUBLIC', %s, 'GOODS', %s,
                'APPROVED', now()
            ) RETURNING id
            """,
            (bucket, key, os.path.basename(key), content_type, byte_size, checksum,
             seller_id, goods_id),
        )
        file_id = cur.fetchone()[0]
        cur.execute(
            "INSERT INTO goods_images (goods_id, file_id, sort_order, is_primary, created_at) "
            "VALUES (%s, %s, 0, TRUE, now())",
            (goods_id, file_id),
        )


if __name__ == "__main__":
    raise SystemExit(main())
