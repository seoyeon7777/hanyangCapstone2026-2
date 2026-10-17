"""store.py (SQLite 저장) 테스트. 표준 라이브러리만.

실행: python test_store.py
"""
import os
import sqlite3
import tempfile

# 진짜 feed.db는 건드리지 않게, store를 불러오기 전에 임시 파일로 정한다
os.environ["FEED_DB"] = os.path.join(tempfile.mkdtemp(), "t.db")
import store  # noqa: E402
BODY = {"ratio": {"S": 0.8, "W": 0.1, "N": 0.1}, "kind": "single", "primary": "S"}


def test_new_user_has_no_body():
    store.reset()
    assert store.get_user("nobody") is None
    store.ensure_user("u1")
    assert store.get_user("u1") == {"body": None, "events": []}
    store.ensure_user("u1")                       # 두 번 불러도 그대로
    assert store.get_user("u1")["body"] is None


def test_save_body_overwrites():
    store.reset()
    store.save_body("u1", BODY)
    assert store.get_user("u1")["body"] == BODY
    other = {**BODY, "primary": "W"}
    store.save_body("u1", other)
    assert store.get_user("u1")["body"]["primary"] == "W"


def test_events_keep_order_and_create_user():
    store.reset()
    store.add_event("u2", "t01", "click")         # 사용자가 없어도 만들어짐
    store.add_event("u2", "t04", "purchase")
    store.add_event("u2", "t01", "return")
    assert store.get_user("u2")["events"] == [("t01", "click"), ("t04", "purchase"), ("t01", "return")]


def test_korean_and_file_is_real_sqlite():
    store.reset()
    store.save_body("서연", BODY)
    assert store.get_user("서연")["body"] == BODY
    with sqlite3.connect(store._path) as conn:        # 다른 연결(=다른 프로그램)에서도 보임
        assert conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 1


def test_reset():
    store.add_event("u3", "t01", "like")
    store.reset()
    assert store.get_user("u3") is None


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_")]
    failed = 0
    for name, fn in tests:
        try:
            fn()
            print(f"  PASS  {name}")
        except AssertionError:
            failed += 1
            print(f"  FAIL  {name}")
        except Exception as e:
            failed += 1
            print(f"  ERROR {name}: {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} 통과")
    raise SystemExit(1 if failed else 0)
