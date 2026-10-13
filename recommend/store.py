"""사용자 데이터 저장 (SQLite). 서버를 껐다 켜도 진단 결과와 반응 기록이 남는다.

SQLite는 파이썬에 기본으로 들어 있는 데이터베이스다. 설치할 것 없이 파일 하나(feed.db)에 저장한다.
파일 위치: 환경변수 FEED_DB, 없으면 recommend/data/feed.db (저장소에는 올리지 않음 — .gitignore)

표 두 개
    users  (user_id, body, created_at, updated_at)   body = 진단 결과 JSON (토글 OFF만 쓴 사용자는 NULL)
    events (id, user_id, item_id, event, created_at)  클릭·찜·구매·반품 기록 (오래된 것 → 최근 순)

취향 점수(pref)는 따로 저장하지 않고 events로 매번 다시 계산한다 (feed_api.load_user).
그래서 학습 가중치(rec_config.EVENT_WEIGHTS)를 바꾸면 예전 기록에도 새 규칙이 그대로 적용된다.
"""
import json
import os
import sqlite3

DEFAULT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "feed.db")
_path = os.environ.get("FEED_DB") or DEFAULT_PATH

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    user_id    TEXT PRIMARY KEY,
    body       TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS events (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT NOT NULL REFERENCES users(user_id),
    item_id    TEXT NOT NULL,
    event      TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS events_by_user ON events(user_id, id);
"""


def set_path(path):
    """저장 파일을 바꾼다 (테스트용). 바꾼 파일에 표가 없으면 만든다."""
    global _path
    _path = path
    init()


def _connect():
    # 요청마다 새로 연결한다: Flask가 요청을 여러 스레드에서 처리해도 안전하다
    conn = sqlite3.connect(_path)
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init():
    os.makedirs(os.path.dirname(os.path.abspath(_path)), exist_ok=True)
    with _connect() as conn:
        conn.executescript(SCHEMA)


def ensure_user(user_id):
    """사용자가 없으면 만든다 (진단 결과 없이)."""
    with _connect() as conn:
        conn.execute("INSERT OR IGNORE INTO users (user_id) VALUES (?)", (user_id,))


def save_body(user_id, body):
    """진단 결과를 저장한다 (있으면 덮어씀)."""
    with _connect() as conn:
        conn.execute(
            "INSERT INTO users (user_id, body) VALUES (?, ?) "
            "ON CONFLICT(user_id) DO UPDATE SET body = excluded.body, updated_at = CURRENT_TIMESTAMP",
            (user_id, json.dumps(body, ensure_ascii=False)),
        )


def add_event(user_id, item_id, event):
    """반응 기록 하나를 더한다. 사용자가 없으면 먼저 만든다."""
    with _connect() as conn:
        conn.execute("INSERT OR IGNORE INTO users (user_id) VALUES (?)", (user_id,))
        conn.execute("INSERT INTO events (user_id, item_id, event) VALUES (?, ?, ?)",
                     (user_id, item_id, event))


def get_user(user_id):
    """{"body": dict 또는 None, "events": [(item_id, event), ...]} — 없는 사용자면 None."""
    with _connect() as conn:
        row = conn.execute("SELECT body FROM users WHERE user_id = ?", (user_id,)).fetchone()
        if row is None:
            return None
        events = conn.execute("SELECT item_id, event FROM events WHERE user_id = ? ORDER BY id",
                              (user_id,)).fetchall()
    return {"body": json.loads(row[0]) if row[0] else None, "events": events}


def reset():
    """모든 기록을 지운다 (테스트·시연 초기화용)."""
    with _connect() as conn:
        conn.execute("DELETE FROM events")
        conn.execute("DELETE FROM users")


init()
