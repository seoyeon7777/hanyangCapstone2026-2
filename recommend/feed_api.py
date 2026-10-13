"""7단계: 추천 피드 API (Flask Blueprint).

app.py에서 등록:  app.register_blueprint(feed_bp)

GET  /api/feed?user_id=u1&S=0.6&W=0.3&N=0.1&season=fall
POST /api/feed        체형은 셋 중 하나로 보낸다
                      {"user_id": "u1", "answers": {"q1": 1, ..., "q7": 3}, "measurements": {...}}  ← A 파트가 진단
                      {"user_id": "u1", "profile": A의 classify() 결과}
                      {"user_id": "u1", "body": {"ratio": {"S":0.6,"W":0.3,"N":0.1}}}
POST /api/feed/event  {"user_id": "u1", "item_id": "t01", "event": "like"}
GET  /api/feed/scroll?user_id=u1&S=0.6&W=0.3&N=0.1&page=0&size=10   ← 홈 화면 무한 스크롤용
     /api/feed, /api/feed/scroll 둘 다 mode로 홈 화면 토글을 받는다
       mode=body    (기본) 토글 ON: 골격 맞춤 추천 — 체형 필요
       mode=general        토글 OFF: 일반 쇼핑몰 추천(인기·자주 누른 스타일·계절) — 체형 없어도 됨
GET  /feed-demo       연습용 스크롤 화면 (친구들 홈 화면이 오기 전 확인용)

사용자 취향(pref)과 반응 기록(history)은 서버 메모리에 저장한다(서버를 끄면 사라짐 — 시연용).
"""
from datetime import date

from flask import Blueprint, jsonify, render_template, request

from rec_config import (
    SEASONS, SEASON_BY_MONTH, REDIAG_MESSAGE, SCROLL_PAGE_SIZE, SCROLL_MAX_PAGE_SIZE, MODES,
)
from fake_matching import classify_kind
from feed import (load_items, score_by_mode, home_feed, feed_order, reason,
                  update_pref, need_rediagnosis)
from matching_b import from_profile
from body_profile import classify, InputError  # A 파트 (matching_b가 저장소 맨 위 폴더를 경로에 넣어 둠)

# 연습용 화면은 recommend/templates/에 둔다 (팀 공용 templates/와 안 겹치게)
feed_bp = Blueprint("feed", __name__, template_folder="templates")

ITEMS = load_items()
ITEMS_BY_ID = {item["id"]: item for item in ITEMS}

# user_id → {"body": {...}, "pref": {...}, "history": [...]}
USERS = {}


class BadRequest(Exception):
    pass


def current_season():
    return SEASON_BY_MONTH[date.today().month]


def make_body(ratio):
    """S·W·N 비율 → body. 합이 1이 아니면 1이 되게 맞춘다."""
    try:
        ratio = {t: float(ratio.get(t, 0)) for t in ("S", "W", "N")}
    except (TypeError, ValueError, AttributeError):
        raise BadRequest("ratio는 S, W, N 숫자여야 해요")
    total = sum(ratio.values())
    if total <= 0 or any(v < 0 for v in ratio.values()):
        raise BadRequest("ratio는 0 이상이고 합이 0보다 커야 해요")
    ratio = {t: v / total for t, v in ratio.items()}
    kind, primary = classify_kind(ratio)
    return {"ratio": ratio, "kind": kind, "primary": primary}


def with_reasons(feed):
    """화면에 바로 쓰도록 각 옷에 추천 이유(reason)를 붙인다."""
    for section in feed["sections"]:
        for entry in section["items"]:
            if "top" in entry:  # 추천 코디
                for part in ("top", "bottom"):
                    entry[part] = {**entry[part], "reason": reason(entry[part])}
            else:
                entry["reason"] = reason(entry)
    return feed


@feed_bp.errorhandler(BadRequest)
def bad_request(e):
    # B 파트 API와 같은 오류 모양 {"ok": false, "error": "..."} — 앱에서 한 가지로 처리할 수 있게
    return jsonify({"ok": False, "error": str(e)}), 400


def json_body():
    """POST 본문(JSON 객체). 비어 있으면 {}, 객체가 아니면(목록·문자열 등) 400."""
    data = request.get_json(silent=True)
    if data is None:
        return {}
    if not isinstance(data, dict):
        raise BadRequest("요청 본문은 JSON 객체({ ... })여야 해요")
    return data


def resolve_request():
    """요청에서 (data, user, body, season, mode)를 꺼낸다. /api/feed와 /api/feed/scroll 공통.

    user_id가 있으면 사용자를 만들거나 불러와 체형을 저장하고, 비율을 안 보내면 저장된 체형을 쓴다.
    mode="general"(토글 OFF)이면 체형이 없어도 된다 (body는 None일 수 있음).
    """
    body = None
    if request.method == "POST":
        data = json_body()
        body_in = data.get("body") or {}
        if not isinstance(body_in, dict):
            raise BadRequest('body는 {"ratio": {"S": .., "W": .., "N": ..}} 모양이어야 해요')
        ratio = body_in.get("ratio")
        if data.get("answers") is not None:      # A 파트로 바로 진단
            if not isinstance(data["answers"], dict):
                raise BadRequest('answers는 {"q1": 1, ..., "q7": 3} 모양이어야 해요')
            if data.get("measurements") is not None and not isinstance(data["measurements"], dict):
                raise BadRequest("measurements는 치수 5개를 담은 객체여야 해요")
            try:
                body = from_profile(classify(data["answers"], data.get("measurements")))
            except InputError as e:
                raise BadRequest(str(e))
        elif data.get("profile") is not None:    # 이미 진단한 A 결과
            try:
                body = make_body(data["profile"]["ratios"])
                body["kind"] = "mixed" if data["profile"]["is_mixed"] else "single"
            except (KeyError, TypeError):
                raise BadRequest("profile은 A 파트 classify() 결과 모양이어야 해요 (ratios, is_mixed)")
    else:
        data = request.args
        # 빠진 값은 0 (POST와 같게). 예: ?S=1 → S 1, W 0, N 0
        ratio = {t: data.get(t, 0) for t in ("S", "W", "N")} if any(t in data for t in "SWN") else None

    user_id = data.get("user_id")
    if user_id is not None and not isinstance(user_id, str):
        raise BadRequest("user_id는 문자열이어야 해요")
    user = USERS.get(user_id) if user_id else None
    mode = data.get("mode") or "body"
    if mode not in MODES:
        raise BadRequest(f"mode는 {', '.join(MODES)} 중 하나여야 해요 (body = 토글 ON, general = 토글 OFF)")

    if body is not None:
        pass
    elif ratio is not None:
        body = make_body(ratio)
    elif user and user.get("body"):
        body = user["body"]  # 이전에 보낸 체형 재사용
    elif mode == "body":
        raise BadRequest("체형 비율(S, W, N)이 필요해요 (체형 없이 보려면 mode=general)")

    season = data.get("season") or current_season()
    if season not in SEASONS:
        raise BadRequest(f"season은 {', '.join(SEASONS)} 중 하나여야 해요")

    if user_id:
        user = USERS.setdefault(user_id, {"pref": {}, "history": [], "body": None})
        if body is not None:
            user["body"] = body
    return data, user, body, season, mode


def int_arg(data, name, default, low, high):
    try:
        value = int(data.get(name, default))
    except (TypeError, ValueError):
        raise BadRequest(f"{name}는 정수여야 해요")
    if not low <= value <= high:
        raise BadRequest(f"{name}는 {low}~{high} 사이여야 해요")
    return value


@feed_bp.route("/api/feed", methods=["GET", "POST"])
def get_feed():
    _, user, body, season, mode = resolve_request()
    pref = user["pref"] if user else None
    feed = with_reasons(home_feed(body, ITEMS, pref, season, mode=mode))
    return jsonify({"mode": mode, "body": body, "season": season, **feed})


@feed_bp.route("/api/feed/scroll", methods=["GET", "POST"])
def scroll_feed():
    """홈 화면 무한 스크롤: 알고리즘 순서대로 page번째 묶음(size개)을 돌려준다.

    page=0이 맨 위(= 오늘의 추천). 화면이 바닥에 닿으면 page를 1씩 올려 다시 부른다.
    취향이 바뀌면 순서도 바뀌므로, 반응(event) 뒤에는 page=0부터 다시 부르는 게 정확하다.
    """
    data, user, body, season, mode = resolve_request()
    page = int_arg(data, "page", 0, 0, 10_000)
    size = int_arg(data, "size", SCROLL_PAGE_SIZE, 1, SCROLL_MAX_PAGE_SIZE)
    pref = user["pref"] if user else None

    ordered = feed_order(score_by_mode(mode, body, ITEMS, pref, season), page_size=size)
    chunk = ordered[page * size:(page + 1) * size]
    return jsonify({
        "mode": mode,
        "body": body,
        "season": season,
        "page": page,
        "size": size,
        "total": len(ordered),
        "has_more": (page + 1) * size < len(ordered),
        "items": [{**r, "reason": reason(r)} for r in chunk],
    })


@feed_bp.route("/feed-demo")
def feed_demo():
    return render_template("feed_demo.html")


@feed_bp.route("/api/feed/event", methods=["POST"])
def post_event():
    data = json_body()
    user_id, item_id = data.get("user_id"), data.get("item_id")
    if not isinstance(user_id, str) or not isinstance(item_id, str):
        raise BadRequest("user_id와 item_id는 문자열이어야 해요")
    user = USERS.get(user_id)
    if not user:
        raise BadRequest("먼저 /api/feed 또는 /api/feed/scroll을 user_id와 함께 불러 주세요")
    item = ITEMS_BY_ID.get(item_id)
    if not item:
        raise BadRequest("없는 상품이에요")
    try:
        user["pref"] = update_pref(user["pref"], item, data.get("event"))
    except ValueError as e:
        raise BadRequest(str(e))
    user["history"].append({"item": item, "event": data["event"]})

    rediag = need_rediagnosis(user["history"], user["body"])
    return jsonify({
        "pref": user["pref"],
        "need_rediagnosis": rediag,
        "message": REDIAG_MESSAGE if rediag else "",
    })
