"""11단계 DB 저장소 테스트 (PostgreSQL + psycopg 필요).

빈 PostgreSQL 서버 주소를 TEST_DATABASE_URL로 주면, 그 서버에 테스트용 DB(rec_store_test)를 새로 만들고
db/db.sql로 표를 만든 뒤 시연 상품 30개를 넣어 확인한다. 끝나면 테스트용 DB를 지운다.
주의: Supabase(팀 DB) 주소를 넣지 말 것. 테스트는 로컬 PostgreSQL에서만 돌린다.

실행: TEST_DATABASE_URL=postgresql://postgres@127.0.0.1:5433/postgres python test_db.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

ADMIN_URL = os.environ.get("TEST_DATABASE_URL")
if not ADMIN_URL:
    print("TEST_DATABASE_URL이 없어서 DB 테스트를 건너뛰어요 (로컬 PostgreSQL 주소를 넣으면 실행)")
    raise SystemExit(0)

import psycopg  # noqa: E402
from psycopg.conninfo import make_conninfo  # noqa: E402

from app import app  # noqa: E402
import feed_api  # noqa: E402
from feed import load_items  # noqa: E402
from seed_db import seed  # noqa: E402
from store import MemoryStore, PgStore  # noqa: E402

TEST_DB = "rec_store_test"
TEST_URL = make_conninfo(ADMIN_URL, dbname=TEST_DB)
ANSWERS_S = {f"q{i}": 1 for i in range(1, 8)}      # 전부 1번 → 스트레이트
ANSWERS_W = {f"q{i}": 2 for i in range(1, 8)}      # 전부 2번 → 웨이브

client = app.test_client()


def setup_db():
    with psycopg.connect(ADMIN_URL, autocommit=True) as admin:
        admin.execute(f"DROP DATABASE IF EXISTS {TEST_DB}")
        admin.execute(f"CREATE DATABASE {TEST_DB}")
    with open(os.path.join(ROOT, "db", "db.sql"), encoding="utf-8") as f:
        schema = f.read()
    with psycopg.connect(TEST_URL, autocommit=True) as conn:
        conn.execute(schema)
        seed(conn, load_items())


def drop_db():
    with psycopg.connect(ADMIN_URL, autocommit=True) as admin:
        admin.execute(f"DROP DATABASE IF EXISTS {TEST_DB} WITH (FORCE)")


def sql(query, args=()):
    """테스트 DB에 바로 쿼리. SELECT면 결과 행, 아니면 None."""
    with psycopg.connect(TEST_URL) as conn:
        cur = conn.execute(query, args)
        return cur.fetchall() if cur.description else None


def use_db():
    """서버를 새로 켠 것처럼 PgStore를 새로 만든다 (메모리에 남은 것 없음)."""
    feed_api.STORE = PgStore(TEST_URL)


def scroll_ids(json_body):
    res = client.post("/api/feed/scroll", json={**json_body, "size": 50})
    assert res.status_code == 200, res.get_json()
    return [i["product_id"] for i in res.get_json()["items"]]


def test_items_same_as_json():
    use_db()
    from_db = {i["product_id"]: i for i in feed_api.STORE.items()}
    from_json = {i["product_id"]: i for i in load_items()}
    assert from_db.keys() == from_json.keys()
    for pid, item in from_json.items():
        got = from_db[pid]
        for key in ("product_name", "clothing_type", "tags", "seasons", "popularity"):
            assert got[key] == item[key], (pid, key)


def test_jumpsuit_and_untagged_products_left_out():
    sql("""INSERT INTO categories (category_name, clothing_type) VALUES ('점프수트', 'jumpsuit')""")
    sql("""INSERT INTO products (product_id, brand_id, fabric_id, category_id, product_name, price)
           SELECT 'j01', brand_id, fabric_id, (SELECT category_id FROM categories WHERE clothing_type = 'jumpsuit'),
                  '점프수트', 0 FROM products WHERE product_id = 't01'""")
    sql("""INSERT INTO product_tags (product_id, tags, source) VALUES ('j01', '["fit:slim"]', 'manual')""")
    sql("""INSERT INTO products (product_id, brand_id, fabric_id, category_id, product_name, price)
           SELECT 'n01', brand_id, fabric_id, category_id, '태그 없는 셔츠', 0 FROM products WHERE product_id = 't01'""")
    use_db()
    ids = {i["product_id"] for i in feed_api.STORE.items()}
    assert "j01" not in ids and "n01" not in ids and len(ids) == 30
    # 점프수트가 DB에 있어도 추천 요청이 오류 나지 않음
    assert len(scroll_ids({"mode": "general"})) == 30


def test_order_same_as_memory():
    body = {"body": {"ratio": {"S": 0.6, "W": 0.3, "N": 0.1}}, "season": "fall"}
    feed_api.STORE = MemoryStore(load_items())
    expected = scroll_ids(body)
    use_db()
    assert scroll_ids(body) == expected


def test_body_saved_once_and_replaced_on_rediagnosis():
    use_db()
    for _ in range(3):  # 앱은 스크롤할 때마다 같은 설문 답을 보낸다
        scroll_ids({"user_id": "app-1", "answers": ANSWERS_S})
    rows = sql("SELECT primary_type, is_current FROM body_profiles WHERE user_id = 'app-1'")
    assert rows == [("S", True)]
    scroll_ids({"user_id": "app-1", "answers": ANSWERS_W})   # 설문을 다시 함
    rows = sql("SELECT primary_type, is_current FROM body_profiles WHERE user_id = 'app-1' ORDER BY profile_id")
    assert rows == [("S", False), ("W", True)]


def test_body_and_pref_survive_restart():
    use_db()
    scroll_ids({"user_id": "app-2", "answers": ANSWERS_S})
    for pid, event in (("t04", "like"), ("t04", "purchase"), ("b03", "click")):
        res = client.post("/api/feed/event", json={"user_id": "app-2", "product_id": pid, "event": event})
        assert res.status_code == 200
    pref_before = res.get_json()["pref"]
    assert sql("SELECT count(*) FROM user_events WHERE user_id = 'app-2'") == [(3,)]

    use_db()  # 서버 재시작
    user = feed_api.STORE.user("app-2")
    assert user["body"]["primary"] == "S"
    assert user["pref"] == pref_before
    # 체형을 다시 안 보내도 저장된 체형으로 추천 (토글 ON)
    res = client.get("/api/feed/scroll?user_id=app-2&mode=body")
    assert res.status_code == 200 and res.get_json()["body"]["primary"] == "S"


def test_pref_same_as_memory():
    events = [("t01", "like"), ("b02", "purchase"), ("t01", "return"), ("o01", "click")]
    results = []
    for store in (MemoryStore(load_items()), PgStore(TEST_URL)):
        feed_api.STORE = store
        scroll_ids({"user_id": "app-3", "answers": ANSWERS_W})
        for pid, event in events:
            res = client.post("/api/feed/event", json={"user_id": "app-3", "product_id": pid, "event": event})
        results.append((res.get_json()["pref"], scroll_ids({"user_id": "app-3"})))
    assert results[0] == results[1]


def test_like_once_unlike_and_rediag_after_new_diagnosis():
    """찜은 상품마다 한 줄만, 찜 해제는 그 줄을 지움, 다시 진단하면 그 뒤 반응만으로 재진단 판단."""
    use_db()
    scroll_ids({"user_id": "app-5", "answers": ANSWERS_S})
    ev = lambda pid, event="like": client.post(
        "/api/feed/event", json={"user_id": "app-5", "product_id": pid, "event": event}).get_json()
    for _ in range(5):
        ev("t04")
    assert sql("SELECT count(*) FROM user_events WHERE user_id = 'app-5' AND event = 'like'") == [(1,)]
    assert ev("t04", "unlike")["pref"] == {}
    assert sql("SELECT count(*) FROM user_events WHERE user_id = 'app-5'") == [(0,)]

    for pid in ["t06", "t08", "t09", "t10", "t11", "o04", "o06", "d04", "t01", "b02"]:
        r = ev(pid)
    assert r["need_rediagnosis"] is True
    scroll_ids({"user_id": "app-5", "answers": ANSWERS_W})   # 설문을 다시 해서 진단이 바뀜
    assert ev("t01", "click")["need_rediagnosis"] is False


def test_bad_event_not_saved():
    use_db()
    scroll_ids({"user_id": "app-4", "mode": "general"})
    res = client.post("/api/feed/event", json={"user_id": "app-4", "product_id": "t01", "event": "share"})
    assert res.status_code == 400
    assert sql("SELECT count(*) FROM user_events WHERE user_id = 'app-4'") == [(0,)]


def test_db_down_gives_503_json():
    feed_api.STORE = PgStore(make_conninfo(ADMIN_URL, port="1", connect_timeout="2"))
    res = client.post("/api/feed/scroll", json={"mode": "general"})
    assert res.status_code == 503
    data = res.get_json()
    assert data["ok"] is False and "DB" in data["error"]


if __name__ == "__main__":
    setup_db()
    tests = [(n, f) for n, f in globals().items() if n.startswith("test_")]  # 적은 순서대로 (DB 상태가 이어짐)
    failed = 0
    try:
        for name, fn in tests:
            try:
                fn()
                print(f"  PASS  {name}")
            except AssertionError as e:
                failed += 1
                print(f"  FAIL  {name} {e}")
            except Exception as e:
                failed += 1
                print(f"  ERROR {name}: {type(e).__name__}: {e}")
    finally:
        feed_api.STORE = None
        drop_db()
    print(f"\n{len(tests) - failed}/{len(tests)} 통과")
    raise SystemExit(1 if failed else 0)
