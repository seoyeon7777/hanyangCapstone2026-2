"""11단계: 상품·사용자 저장소.

DB 주소가 있으면 PostgreSQL(Supabase, 표 모양은 db/db.sql), 없으면 JSON 파일 + 서버 메모리.
feed_api.py는 어느 쪽이든 같은 함수만 부른다.

    items()                      추천 후보 상품 목록 (C의 item 모양)
    item(product_id)             상품 하나 (없으면 None)
    user(user_id)                {"body", "answers", "pref", "history", "history_since_body"} (처음 보는 사용자면 None)
                                 history_since_body = 지금 체형으로 진단된 뒤의 반응만 (재진단 안내 판단용)
    ensure_user(user_id)         사용자가 없으면 만든다
    save_body(user_id, body, answers)   체형 저장 (DB에서는 body_profiles, 바뀐 경우만 새 행)
    add_event(user_id, item, event)     반응 기록 (DB에서는 user_events). 같은 상품 찜(like)은 한 번만 저장
    remove_like(user_id, product_id)    찜 해제: 그 상품의 찜 기록을 지운다 → 취향 점수가 찜 전으로 돌아간다

취향(pref)은 따로 저장하지 않는다. 반응 기록(history)에서 feed.pref_from_history로 매번 계산한다
(최근 PREF_WINDOW개만. 가중치를 바꿔도 기록과 어긋나지 않게 — db/db.sql의 user_events 설명과 같다).
"""
import json
import os
import queue
import threading
import time

from rec_config import (
    OUTFIT_GROUP, DATABASE_URL_FILE, DB_ITEMS_CACHE_SECONDS, DB_DEMO_USER_NAME, DB_POOL_SIZE,
    SELLER_MEASUREMENTS, REGISTERED_ITEMS_FILE,
)
from feed import load_items, update_pref, pref_from_history

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


REGISTERED_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", REGISTERED_ITEMS_FILE)

# clothing_type -> categories.category_name (seed_db.py와 같다. categories는 이름이 겹치면 안 된다)
CATEGORY_NAMES = {"top": "상의", "outer": "아우터", "pants": "바지", "skirt": "치마", "dress": "원피스"}


def make_store():
    url = database_url()
    if url:
        return PgStore(url)
    return MemoryStore(load_items() + _load_registered(REGISTERED_PATH), registered_path=REGISTERED_PATH)


def _load_registered(path):
    """판매자가 등록해 파일에 보관해 둔 상품들 (없거나 깨졌으면 빈 목록)."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        return [i for i in data if isinstance(i, dict) and "product_id" in i]
    except (OSError, ValueError):
        return []


def reference_size(sizes):
    """DB의 치수 표는 상품당 한 줄이라, 사이즈별 치수 중 하나를 대표로 고른다: 가운데 사이즈."""
    return sizes[(len(sizes) - 1) // 2] if sizes else None


def _new_user():
    return {"body": None, "answers": None, "pref": {}, "history": [], "history_since_body": []}


def _has_like(history, product_id):
    return any(h["event"] == "like" and h["item"]["product_id"] == product_id for h in history)


class MemoryStore:
    """시연용: 상품은 items.json, 사용자는 서버 메모리 (서버를 끄면 사라짐)."""

    kind = "memory"

    def __init__(self, items, registered_path=None):
        self._items = list(items)
        self._by_id = {i["product_id"]: i for i in self._items}
        self._users = {}
        self._seq = 0   # 반응 순서 번호 (체형이 바뀐 뒤의 반응을 가려낼 때 쓴다)
        self._registered_path = registered_path   # 있으면 판매자가 등록한 상품을 이 파일에 보관한다
        self._lock = threading.Lock()

    def items(self):
        return self._items

    def item(self, product_id):
        return self._by_id.get(product_id)

    def add_product(self, product):
        """판매자가 등록한 상품을 추가한다. product_id는 여기서 정한다. 저장한 상품을 돌려준다."""
        with self._lock:
            n = len(self._items) + 1
            while f"r{n}" in self._by_id:
                n += 1
            stored = {**product, "product_id": f"r{n}"}
            self._items.append(stored)
            self._by_id[stored["product_id"]] = stored
            self._save_registered()
            return stored

    def products_by_brand(self, brand):
        return [i for i in self._items if i.get("brand") == brand and i.get("registered")]

    def _save_registered(self):
        if not self._registered_path:
            return
        mine = [i for i in self._items if i.get("registered")]
        os.makedirs(os.path.dirname(self._registered_path), exist_ok=True)
        with open(self._registered_path, "w", encoding="utf-8") as f:
            json.dump(mine, f, ensure_ascii=False, indent=1)

    def user(self, user_id):
        return self._users.get(user_id)

    def ensure_user(self, user_id):
        self._users.setdefault(user_id, _new_user())

    def save_body(self, user_id, body, answers=None):
        user = self._users.setdefault(user_id, _new_user())
        # 진단이 바뀐 경우만 "다시 진단"으로 본다 (DB와 같은 기준: 비율·1위·설문 답)
        same = (user["body"] is not None and user["body"]["primary"] == body["primary"]
                and all(abs(user["body"]["ratio"][t] - body["ratio"][t]) < 1e-9 for t in ("S", "W", "N"))
                and (user.get("answers") == answers or answers is None))
        user["body"] = body
        user["answers"] = answers
        if not same:
            user["body_since"] = self._seq
        self._refresh(user)

    def add_event(self, user_id, item, event):
        update_pref({}, item, event)  # 모르는 event면 저장하기 전에 ValueError
        user = self._users.setdefault(user_id, _new_user())
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
        user["pref"] = pref_from_history(user["history"])
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
        # 연결을 최대 DB_POOL_SIZE개까지 열어 두고 요청마다 하나씩 빌려 쓴다.
        # (연결 하나를 모두가 돌아가며 쓰면, 여러 사람이 동시에 쓸 때 차례를 기다리느라 느려진다)
        self._idle = queue.LifoQueue()
        self._opened = 0
        self._opened_lock = threading.Lock()
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

    def _borrow(self):
        """쉬는 연결을 빌린다. 없으면 새로 열고, 이미 DB_POOL_SIZE개가 열려 있으면 하나가 돌아올 때까지 기다린다."""
        try:
            return self._idle.get_nowait()
        except queue.Empty:
            pass
        with self._opened_lock:
            can_open = self._opened < DB_POOL_SIZE
            if can_open:
                self._opened += 1
        if not can_open:
            try:
                return self._idle.get(timeout=30)
            except queue.Empty:
                raise StoreError("DB 연결을 기다리다 시간이 초과됐어요")
        try:
            return self._connect()
        except StoreError:
            with self._opened_lock:
                self._opened -= 1
            raise

    def _give_back(self, conn, broken=False):
        if broken or conn.closed:
            with self._opened_lock:
                self._opened -= 1
            try:
                conn.close()
            except Exception:
                pass
        else:
            self._idle.put(conn)

    def _run(self, work, write=False):
        """빌린 연결로 work(cursor)를 실행한다. 연결이 끊겼으면 새 연결로 한 번 더 한다.

        write=True면 트랜잭션으로 묶는다 (여러 문장이 다 되거나 다 안 되게).
        읽기만 하면 트랜잭션 없이 보낸다 → 시작·끝 신호만큼 DB 왕복이 줄어든다.
        """
        import psycopg
        for attempt in (1, 2):
            conn = self._borrow()
            try:
                if write:
                    with conn.transaction(), conn.cursor() as cur:
                        result = work(cur)
                else:
                    with conn.cursor() as cur:
                        result = work(cur)
            except psycopg.OperationalError as e:
                self._give_back(conn, broken=True)
                if attempt == 2:
                    raise StoreError(f"DB 작업이 실패했어요 ({type(e).__name__})")
                continue
            except psycopg.Error as e:
                self._give_back(conn)
                raise StoreError(f"DB 작업이 실패했어요 ({type(e).__name__}: {e})")
            except BaseException:
                self._give_back(conn, broken=True)
                raise
            self._give_back(conn)
            return result

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
        self._attach_seller_details(items)
        self._by_id = {i["product_id"]: i for i in items}
        self._items = items
        self._loaded_at = time.monotonic()

    def _attach_seller_details(self, items):
        """판매자가 등록한 상품(source=direct)에 사이즈·색상·사이즈별 치수를 붙인다. 시연용 상품은 그대로 둔다."""
        if not items:
            return

        def work(cur):
            cur.execute("SELECT product_id, attrs, source FROM product_tags WHERE product_id = ANY(%s)",
                        ([i["product_id"] for i in items],))
            return cur.fetchall()

        by_id = {i["product_id"]: i for i in items}
        for pid, attrs, source in self._run(work):
            extra = attrs.get("seller_extra") if isinstance(attrs, dict) else None
            item = by_id.get(pid)
            if source != "direct" or not extra or item is None:
                continue
            item.update(registered=True, sizes=extra.get("sizes", []), colors=extra.get("colors", []),
                        measurements=extra.get("measurements", {}), seller=extra.get("seller", ""))

    def add_product(self, product):
        """판매자가 등록한 상품을 DB에 넣는다 (brands·fabrics·categories는 없으면 만든다). 저장한 상품을 돌려준다."""
        sizes, colors = product.get("sizes") or [], product.get("colors") or []
        by_size = product.get("measurements") or {}
        extra = {"sizes": sizes, "colors": colors, "measurements": by_size, "seller": product.get("seller", "")}
        attrs = {**(product.get("attrs") or {}), "seller_extra": extra}

        def one_id(cur, select_sql, insert_sql, args):
            cur.execute(select_sql, args[:1])
            row = cur.fetchone()
            if row:
                return row[0]
            cur.execute(insert_sql, args)
            return cur.fetchone()[0]

        def work(cur):
            brand_id = one_id(cur, "SELECT brand_id FROM brands WHERE brand_name = %s",
                              "INSERT INTO brands (brand_name, created_at, updated_at) VALUES (%s, now(), now()) "
                              "RETURNING brand_id", (product["brand"],))
            fabric_id = one_id(cur, "SELECT fabric_id FROM fabrics WHERE fabric_name = %s",
                               "INSERT INTO fabrics (fabric_name) VALUES (%s) RETURNING fabric_id",
                               (product.get("fabric") or "기타",))
            ctype = product["clothing_type"]
            category_id = one_id(cur, "SELECT category_id FROM categories WHERE clothing_type = %s "
                                      "ORDER BY category_id LIMIT 1",
                                 "INSERT INTO categories (clothing_type, category_name) VALUES (%s, %s) "
                                 "RETURNING category_id", (ctype, CATEGORY_NAMES[ctype]))
            cur.execute(
                """INSERT INTO products (brand_id, fabric_id, category_id, product_name, price, description,
                                         main_image_url, seasons, popularity)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING product_id""",
                (brand_id, fabric_id, category_id, product["product_name"], product["price"],
                 product.get("description") or None, product.get("image_url") or None,
                 list(product.get("seasons") or []), product["popularity"]),
            )
            product_id = cur.fetchone()[0]
            for size in sizes:
                for color in colors:
                    cur.execute("INSERT INTO product_options (product_id, size, color) VALUES (%s, %s, %s)",
                                (product_id, size, color))
            table, fields = SELLER_MEASUREMENTS[ctype]
            ref = by_size.get(reference_size(sizes) or "") or {}
            values = [ref.get(key) for key, _ in fields]
            if any(v is not None for v in values):
                cols = ", ".join(key for key, _ in fields)
                marks = ", ".join(["%s"] * len(fields))
                cur.execute(f"INSERT INTO {table} (product_id, {cols}) VALUES (%s, {marks})", (product_id, *values))
            cur.execute(
                """INSERT INTO product_tags (product_id, tags, text_added, fit_stated, material_basis, attrs, source,
                                              rules_version)
                   VALUES (%s, %s::jsonb, %s::jsonb, %s, %s, %s::jsonb, %s, %s)""",
                (product_id, json.dumps(product["tags"]), json.dumps(product.get("text_added") or []),
                 product.get("fit_stated") or "unknown", product.get("material_basis"),
                 json.dumps(attrs, ensure_ascii=False), product.get("source") or "direct",
                 product.get("rules_version")),
            )
            return product_id

        product_id = self._run(work, write=True)
        self._loaded_at = 0.0      # 다음 요청 때 목록을 다시 불러온다 (캐시 무효화)
        return {**product, "product_id": product_id}

    def products_by_brand(self, brand):
        return [i for i in self.items() if i.get("brand") == brand and i.get("registered")]

    # ── 사용자 ──
    def user(self, user_id):
        self.items()
        items_by_id = self._by_id

        def work(cur):
            # 사용자 확인과 지금 체형을 한 번에 읽는다 (없는 사용자면 행이 없음)
            cur.execute(
                """SELECT b.ratio_s, b.ratio_w, b.ratio_n, b.primary_type, b.is_mixed, b.answers, b.created_at
                   FROM users u
                   LEFT JOIN body_profiles b ON b.user_id = u.user_id AND b.is_current
                   WHERE u.user_id = %s""",
                (user_id,),
            )
            body_row = cur.fetchone()
            if body_row is None:
                return None
            cur.execute("SELECT product_id, event, created_at FROM user_events WHERE user_id = %s ORDER BY event_id",
                        (user_id,))
            return body_row, cur.fetchall()

        found = self._run(work)
        if found is None:
            return None
        (s, w, n, primary, is_mixed, answers, body_at), events = found
        user = _new_user()
        if primary is not None:
            user["body"] = {"ratio": {"S": s, "W": w, "N": n}, "primary": primary,
                            "kind": "mixed" if is_mixed else "single"}
            user["answers"] = answers
        for product_id, event, created_at in events:
            item = items_by_id.get(product_id)
            if item is None:          # 지금은 판매하지 않는 상품의 기록은 건너뛴다
                continue
            entry = {"item": item, "event": event}
            user["history"].append(entry)
            if body_at is None or created_at >= body_at:   # 지금 체형으로 진단된 뒤의 반응
                user["history_since_body"].append(entry)
        user["pref"] = pref_from_history(user["history"])
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
        self._run(work, write=True)

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
