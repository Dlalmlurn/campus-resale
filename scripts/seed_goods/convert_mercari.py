#!/usr/bin/env python3
"""把 Mercari Price Suggestion 数据集（train.tsv）转换成导入器认的 JSONL。

Mercari 是真实二手交易平台的公开数据集（Kaggle: mercari-price-suggestion-challenge），
字段：train_id, name, item_condition_id, category_name, brand_name, price, shipping, item_description。
文本是真实二手商品标题/描述，正好用来替换机械的 "演示商品 NNNN"。该数据集不含图片，
导入时用 image_mode=picsum 取真实占位照片即可。

用法：
    python convert_mercari.py /path/to/train.tsv > datasets/goods.jsonl
    # 可选 --limit 控制条数
"""
from __future__ import annotations

import argparse
import csv
import json
import sys

CONDITION_MAP = {
    "1": "NEW",
    "2": "LIKE_NEW",
    "3": "LIGHTLY_USED",
    "4": "NOTICEABLY_USED",
    "5": "NOTICEABLY_USED",
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Mercari train.tsv -> 导入器 JSONL")
    parser.add_argument("tsv", help="Mercari train.tsv 路径")
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()

    written = 0
    with open(args.tsv, "r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            if args.limit is not None and written >= args.limit:
                break
            name = (row.get("name") or "").strip()
            if not name or name == "No description yet":
                continue
            description = (row.get("item_description") or "").strip()
            if description == "No description yet":
                description = name
            record = {
                "external_id": f"mercari-{row.get('train_id')}",
                "title": name,
                "description": description,
                "price": row.get("price") or "0",
                "condition": CONDITION_MAP.get((row.get("item_condition_id") or "").strip(), "LIGHTLY_USED"),
                # Mercari 分类与本系统 categories.code 不一致，留空让导入器回退到默认分类。
                "category_code": "",
            }
            sys.stdout.write(json.dumps(record, ensure_ascii=False) + "\n")
            written += 1
    print(f"已写出 {written} 条", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
