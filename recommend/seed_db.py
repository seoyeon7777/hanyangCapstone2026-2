"""시연용 상품 30개(recommend/data/items.json)를 DB(Supabase)에 넣는다.

먼저 db/db.sql로 표를 만든 뒤 실행:
    python recommend/seed_db.py
DB 주소는 서버와 같은 곳에서 찾는다 (DATABASE_URL 환경 변수 → 저장소 맨 위 database_url.txt).

여러 번 실행해도 된다: 같은 product_id(t01 …)가 있으면 이름·종류·계절·인기도·태그를 items.json 값으로 덮어쓴다.
실제 상품이 들어오면 이 스크립트 대신 상품 등록 기능으로 products·product_tags를 채운다.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from feed import load_items       # noqa: E402
from store import database_url    # noqa: E402

DEMO_BRAND = "시연용 브랜드"
DEMO_FABRIC = "시연용 소재"
# clothing_type → categories.category_name (categories는 이름이 겹치면 안 된다)
CATEGORY_NAMES = {"top": "상의", "outer": "아우터", "pants": "바지", "skirt": "치마", "dress": "원피스"}


def fit_stated(tags):
    """태그의 fit:… 값 → product_tags.fit_stated (slim / regular / over / unknown)."""
    for tag in tags:
        if tag.startswith("fit:"):
            value = tag[4:]
            return value if value in ("slim", "regular", "over") else "unknown"
    return "unknown"


def one_id(cur, select_sql, insert_sql, args):
    """select_sql로 행을 찾고, 없으면 insert_sql(… RETURNING id)로 만든 뒤 id를 돌려준다."""
    cur.execute(select_sql, args[:1])
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute(insert_sql, args)
    return cur.fetchone()[0]


def seed(conn, items):
    with conn.transaction(), conn.cursor() as cur:
        # brands.created_at·updated_at은 기본값이 없어서 직접 넣는다
        brand_id = one_id(cur, "SELECT brand_id FROM brands WHERE brand_name = %s",
                          "INSERT INTO brands (brand_name, created_at, updated_at) VALUES (%s, now(), now()) "
                          "RETURNING brand_id", (DEMO_BRAND,))
        fabric_id = one_id(cur, "SELECT fabric_id FROM fabrics WHERE fabric_name = %s",
                           "INSERT INTO fabrics (fabric_name) VALUES (%s) RETURNING fabric_id", (DEMO_FABRIC,))
        # 종류마다 카테고리 하나. 이미 그 종류의 카테고리가 있으면 그것을 쓴다
        category_ids = {
            ctype: one_id(cur, "SELECT category_id FROM categories WHERE clothing_type = %s "
                               "ORDER BY category_id LIMIT 1",
                          "INSERT INTO categories (clothing_type, category_name) VALUES (%s, %s) "
                          "RETURNING category_id", (ctype, name))
            for ctype, name in CATEGORY_NAMES.items()
        }

        for item in items:
            cur.execute(
                """INSERT INTO products (product_id, brand_id, fabric_id, category_id, product_name, price,
                                         seasons, popularity)
                   VALUES (%s, %s, %s, %s, %s, 0, %s, %s)
                   ON CONFLICT (product_id) DO UPDATE SET
                       category_id = EXCLUDED.category_id, product_name = EXCLUDED.product_name,
                       seasons = EXCLUDED.seasons, popularity = EXCLUDED.popularity""",
                (item["product_id"], brand_id, fabric_id, category_ids[item["clothing_type"]],
                 item["product_name"], item["seasons"], item["popularity"]),
            )
            cur.execute(
                """INSERT INTO product_tags (product_id, tags, fit_stated, source)
                   VALUES (%s, %s::jsonb, %s, 'manual')
                   ON CONFLICT (product_id) DO UPDATE SET tags = EXCLUDED.tags, fit_stated = EXCLUDED.fit_stated""",
                (item["product_id"], json.dumps(item["tags"]), fit_stated(item["tags"])),
            )


def main():
    url = database_url()
    if not url:
        sys.exit("DB 주소가 없어요. DATABASE_URL 환경 변수나 저장소 맨 위 database_url.txt에 넣어 주세요")
    import psycopg
    items = load_items()
    with psycopg.connect(url, prepare_threshold=None) as conn:
        seed(conn, items)
    print(f"시연용 상품 {len(items)}개를 DB에 넣었어요")


if __name__ == "__main__":
    main()
