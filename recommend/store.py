"""11단계: 상품·사용자 저장소.

DB 주소가 있으면 PostgreSQL(Supabase, 표 모양은 db/db.sql), 없으면 JSON 파일 + 서버 메모리.
feed_api.py는 어느 쪽이든 같은 함수만 부른다.

    items()                      추천 후보 상품 목록 (C의 item 모양)
    item(product_id)             상품 하나 (없으면 None)
    user(user_id)                {"body", "pref", "history", "history_since_body"} (처음 보는 사용자면 None)
                                 history_since_body = 지금 체형으로 진단된 뒤의 반응만 (재진단 안내 판단용)
    ensure_user(user_id)         사용자가 없으면 만든다
    save_body(user_id, body, answers)   체형 저장 (DB에서는 body_profiles, 바뀐 경우만 새 행)
    add_event(user_id, item, event)     반응 기록 (DB에서는 user_events). 같은 상품 찜(like)은 한 번만 저장
    remove_like(user_id, product_id)    찜 해제: 그 상품의 찜 기록을 지운다 → 취향 점수가 찜 전으로 돌아간다

DB에서는 취향(pref)을 따로 저장하지 않는다. user_events를 순서대로 update_pref에 다시 넣어 매번 계산한다
(가중치를 바꿔도 기록과 어긋나지 않게 — db/db.sql의 user_events 설명과 같다).
"""
import json
import os
import threading
import time

from rec_config import (
    OUTFIT_GROUP, DATABASE_URL_FILE, DB_ITEMS_CACHE_SECONDS, DB_DEMO_USER_NAME,
)
from feed import load_items, update_pref

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 추천이 다룰 수 있는 종류. DB의 jumpsuit는 B·C에 대응이 없어 뺀다 (넣으면 추천 요청 전체가 오류)
SUPPORTED_TYPES = sorted(t for t in OUTFIT_GROUP if t != "bottom")


class StoreError(Exception):
    """DB에 연결하지 못했거나 DB 작업이 실패함 → API는 503으로 돌려준다."""


def database_url():
    """DATABASE_URL 환경 변수 → 저장소 맨 위 database_url.txt 순서로 찾는다. 없으면 None."""
    url = os.environ.get("DATABASE_URL", "").strip()
    if url:
        return url
    path = os.path.join(ROOT, DATABASE_URL_FILE)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            url = f.read().strip()
    return url or None


def make_store():
    url = database_url()
    return PgStore(url) if url else MemoryStore(load_items())


def _new_user():
    return {"body": None, "pref": {}, "history": [], "history_since_body": []}


def _pref_from(history):
    """반응 기록을 처음부터 다시 넣어 취향 점수를 계산한다 (기록을 지워도 정확히 되돌아가게)."""
    pref = {}
    for h in history:
        pref = update_pref(pref, h["item"], h["event"])
    return pref


def _has_like(history, product_id):
    return any(h["event"] == "like" and h["item"]["product_id"] == product_id for h in history)


class MemoryStore:
    """시연용: 상품은 items.json, 사용자는 서버 메모리 (서버를 끄면 사라짐)."""

    kind = "memory"

    def __init__(self, items):
        self._items = list(items)
        self._by_id = {i["product_id"]: i for i in self._items}
        self._users = {}
        self._seq = 0   # 반응 순서 번호 (체형이 바뀐 뒤의 반응을 가려낼 때 쓴다)

    def items(self):
        return self._items

    def item(self, product_id):
        return self._by_id.get(product_id)

    def user(self, user_id):
        return self._users.get(user_id)

    def ensure_user(self, user_id):
        self._users.setdefault(user_id, _new_user())

    def save_body(self, user_id, body, answers=None):
        user = self._users.setdefault(user_id, _new_user())
        # 앱은 스크롤할 때마다 같은 답을 보낸다 → 진단이 바뀐 경우만 "다시 진단"으로 본다 (DB와 같은 기준)
        same = (user["body"] is not None and user["body"]["primary"] == body["primary"]
                and all(abs(user["body"]["ratio"][t] - body["ratio"][t]) < 1e-9 for t in ("S", "W", "N"))
                and (user.get("answers") == answers or answers is None))
        user["body"] = body
        if answers is not None:
            user["answers"] = answers
        if not same:
            user["body_since"] = self._seq
        self._refresh(user)

    def add_event(self, user_id, item, event):
        user = self._users.setdefault(user_id, _new_user())
        update_pref({}, item, event)  # 모르는 event면 저장하기 전에 ValueError
        if event == "like" and _has_like(user["history"], item["product_id"]):
            return  # 같은 상품의 찜은 한 번만 센다
        self._seq += 1
        user["history"].append({"item": item, "event": event, "seq": self._seq})
        self._refresh(user)

    def remove_like(self, user_id, product_id):
        user = self._users.setdefault(user_id, _new_user())
        user["history"] = [h for h in user["history"]
                           if not (h["event"] == "like" and h["item"]["product_id"] == product_id)]
        self._refresh(user)

    @staticmethod
    def _refresh(user):
        user["pref"] = _pref_from(user["history"])
        since = user.get("body_since", 0)
        user["history_since_body"] = [h for h in user["history"] if h["seq"] > since]

    def reset(self):
        """테스트용: 사용자 기록을 모두 지운다."""
        self._users.clear()


class PgStore:
    """PostgreSQL(Supabase). 표: products·categories·brands·product_tags·users·body_profiles·user_events."""

    kind = "postgres"

    def __init__(self, url):
        self.url = url
        self._conn = None
        self._lock = threading.Lock()   # Flask가 요청을 여러 스레드로 받아도 연결 하나를 차례로 쓴다
        self._items = None
        self._by_id = {}
        self._loaded_at = 0.0

    # ── 연결 ──
    def _connect(self):
        try:
            import psycopg
        except ImportError:
            raise StoreError("DB를 쓰려면 psycopg가 필요해요: pip install -r requirements.txt")
        try:
            # prepare_threshold=None: Supabase 연결 풀러(pgbouncer)에서 prepared statement 오류가 나지 않게
            return psycopg.connect(self.url, autocommit=True, prepare_threshold=None, connect_timeout=10)
        except psycopg.Error as e:
            raise StoreError(f"DB에 연결할 수 없어요 ({type(e).__name__})")

    def _run(self, work):
        """work(cursor)를 실행한다. 연결이 끊겼으면 한 번 다시 연결한다."""
        import psycopg
        with self._lock:
            for attempt in (1, 2):
                if self._conn is None or self._conn.closed:
                    self._conn = self._connect()
                try:
                    with self._conn.transaction(), self._conn.cursor() as cur:
                        return work(cur)
                except psycopg.OperationalError as e:
                    self._conn = None
                    if attempt == 2:
                        raise StoreError(f"DB 작업이 실패했어요 ({type(e).__name__})")
                except psycopg.Error as e:
                    raise StoreError(f"DB 작업이 실패했어요 ({type(e).__name__}: {e})")

    # ── 상품 ──
    def items(self):
        if self._items is None or time.monotonic() - self._loaded_at > DB_ITEMS_CACHE_SECONDS:
            self._load_items()
        return self._items

    def item(self, product_id):
        self.items()
        return self._by_id.get(product_id)

    def _load_items(self):
        def work(cur):
            # 판매중 + 삭제 안 됨 + 태그가 있는 상품만 (product_tags가 없으면 B가 점수를 못 낸다)
            cur.execute(
                """
                SELECT p.product_id, p.product_name, c.clothing_type, t.tags, p.seasons, p.popularity,
                       p.price, b.brand_name, p.main_image_url
                FROM products p
                JOIN categories c ON c.category_id = p.category_id
                JOIN product_tags t ON t.product_id = p.product_id
                LEFT JOIN brands b ON b.brand_id = p.brand_id
                WHERE p.deleted_at IS NULL AND p.status = '판매중' AND c.clothing_type = ANY(%s)
                ORDER BY p.product_id
                """,
                (SUPPORTED_TYPES,),
            )
            return cur.fetchall()

        items = [{
            "product_id": pid, "product_name": name, "clothing_type": ctype,
            "tags": list(tags), "seasons": list(seasons), "popularity": float(popularity),
            "price": price, "brand": brand or "", "image_url": image or "",
        } for pid, name, ctype, tags, seasons, popularity, price, brand, image in self._run(work)]
        self._items = items
        self._by_id = {i["product_id"]: i for i in items}
        self._loaded_at = time.monotonic()

    # ── 사용자 ──
    def user(self, user_id):
        self.items()
        items_by_id = self._by_id

        def work(cur):
            cur.execute("SELECT 1 FROM users WHERE user_id = %s", (user_id,))
            if cur.fetchone() is None:
                return None
            cur.execute(
                """SELECT ratio_s, ratio_w, ratio_n, primary_type, is_mixed, created_at FROM body_profiles
                   WHERE user_id = %s AND is_current""",
                (user_id,),
            )
            body_row = cur.fetchone()
            cur.execute("SELECT product_id, event, created_at FROM user_events WHERE user_id = %s ORDER BY event_id",
                        (user_id,))
            return body_row, cur.fetchall()

        found = self._run(work)
        if found is None:
            return None
        body_row, events = found
        user = _new_user()
        body_at = None
        if body_row:
            s, w, n, primary, is_mixed, body_at = body_row
            user["body"] = {"ratio": {"S": s, "W": w, "N": n}, "primary": primary,
                            "kind": "mixed" if is_mixed else "single"}
        for product_id, event, created_at in events:
            item = items_by_id.get(product_id)
            if item is None:          # 지금은 판매하지 않는 상품의 기록은 건너뛴다
                continue
            user["pref"] = update_pref(user["pref"], item, event)
            entry = {"item": item, "event": event}
            user["history"].append(entry)
            if body_at is None or created_at >= body_at:   # 지금 체형으로 진단된 뒤의 반응
                user["history_since_body"].append(entry)
        return user

    def ensure_user(self, user_id):
        def work(cur):
            # 회원가입 전 앱 사용자: 비밀번호 없이 이름만 넣는다 (users의 NOT NULL 칸 채우기)
            cur.execute(
                """INSERT INTO users (user_id, password, name, role) VALUES (%s, '', %s, 'user')
                   ON CONFLICT (user_id) DO NOTHING""",
                (user_id, DB_DEMO_USER_NAME),
            )
        self._run(work)

    def save_body(self, user_id, body, answers=None):
        from body_profile import MIX_GAP  # A 파트 혼합형 기준 (db.sql 제약 is_mixed = confidence < 0.10과 같음)

        ratio = body["ratio"]
        ranked = sorted(("S", "W", "N"), key=lambda t: -ratio[t])
        confidence = min(1.0, max(0.0, ratio[ranked[0]] - ratio[ranked[1]]))
        row = (ratio["S"], ratio["W"], ratio["N"], body["primary"], ranked[1], confidence,
               confidence < MIX_GAP, json.dumps(answers) if answers is not None else None)

        def work(cur):
            cur.execute(
                """SELECT ratio_s, ratio_w, ratio_n, primary_type, answers FROM body_profiles
                   WHERE user_id = %s AND is_current""",
                (user_id,),
            )
            current = cur.fetchone()
            # 앱은 스크롤할 때마다 같은 설문 답을 보낸다 → 진단이 같으면 새 행을 만들지 않는다
            if current and all(abs(current[i] - row[i]) < 1e-9 for i in range(3)) \
                    and current[3] == row[3] and (current[4] == answers or answers is None):
                return
            cur.execute("UPDATE body_profiles SET is_current = false WHERE user_id = %s AND is_current",
                        (user_id,))
            cur.execute(
                """INSERT INTO body_profiles (user_id, ratio_s, ratio_w, ratio_n, primary_type, secondary_type,
                                              confidence, is_mixed, answers)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb)""",
                (user_id, *row),
            )
        self._run(work)

    def add_event(self, user_id, item, event):
        update_pref({}, item, event)  # 모르는 event면 저장하기 전에 ValueError

        def work(cur):
            # 같은 상품의 찜은 한 번만 저장한다 (하트를 껐다 켜도 쌓이지 않게)
            cur.execute(
                """INSERT INTO user_events (user_id, product_id, event)
                   SELECT %s, %s, %s
                   WHERE %s <> 'like' OR NOT EXISTS (
                       SELECT 1 FROM user_events WHERE user_id = %s AND product_id = %s AND event = 'like')""",
                (user_id, item["product_id"], event, event, user_id, item["product_id"]),
            )
        self._run(work)

    def remove_like(self, user_id, product_id):
        def work(cur):
            cur.execute("DELETE FROM user_events WHERE user_id = %s AND product_id = %s AND event = 'like'",
                        (user_id, product_id))
        self._run(work)
