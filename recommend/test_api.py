"""API 테스트 (Flask 필요). 저장소 맨 위 app.py(A+B+C 통합 서버)를 그대로 불러와 확인한다.

실행: python test_api.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from app import app  # noqa: E402
import feed_api  # noqa: E402

client = app.test_client()


def reset():
    feed_api.USERS.clear()


def test_get_feed_with_query():
    reset()
    res = client.get("/api/feed?S=0.8&W=0.1&N=0.1&season=fall")
    assert res.status_code == 200
    data = res.get_json()
    assert data["body"]["kind"] == "single" and data["body"]["primary"] == "S"
    titles = [s["title"] for s in data["sections"]]
    assert titles == ["오늘의 추천", "상의", "하의", "아우터", "원피스", "추천 코디"]
    first = data["sections"][0]["items"][0]
    assert first["reason"]  # 추천 이유가 붙어 있음
    look = data["sections"][-1]["items"][0]
    assert look["top"]["reason"] and look["bottom"]["reason"]


def test_post_feed_normalizes_ratio():
    reset()
    # 합이 1이 아니어도(60:30:10) 비율로 맞춤
    res = client.post("/api/feed", json={"body": {"ratio": {"S": 60, "W": 30, "N": 10}}, "season": "fall"})
    assert res.status_code == 200
    ratio = res.get_json()["body"]["ratio"]
    assert abs(ratio["S"] - 0.6) < 1e-9 and abs(sum(ratio.values()) - 1) < 1e-9


def test_bad_requests():
    reset()
    assert client.get("/api/feed").status_code == 400                       # 체형 없음
    assert client.get("/api/feed?S=1&season=monsoon").status_code == 400     # 없는 계절
    assert client.post("/api/feed", json={"body": {"ratio": {"S": "a"}}}).status_code == 400
    assert client.post("/api/feed/event", json={"user_id": "nobody"}).status_code == 400


def test_event_learns_and_feed_reflects():
    reset()
    client.post("/api/feed", json={"user_id": "u1", "body": {"ratio": {"S": 1, "W": 0, "N": 0}}, "season": "fall"})
    whole = "/api/feed/scroll?user_id=u1&season=fall&size=50"   # 전체 순서에서 크롭 니트 점수를 찾음
    before = client.get(whole).get_json()
    score = lambda feed: next(r["score"] for r in feed["items"] if r["id"] == "t04")

    res = client.post("/api/feed/event", json={"user_id": "u1", "item_id": "t04", "event": "purchase"})
    assert res.status_code == 200
    assert res.get_json()["pref"] == {"len:crop": 0.2, "mat:knit": 0.2}

    after = client.get(whole).get_json()  # 체형은 저장된 값 재사용
    # 취향 0.5 → 0.6 → 최종 +0.25 * 0.1 = +0.025
    assert abs((score(after) - score(before)) - 0.025) < 1e-3

    bad = client.post("/api/feed/event", json={"user_id": "u1", "item_id": "t04", "event": "share"})
    assert bad.status_code == 400
    assert client.post("/api/feed/event", json={"user_id": "u1", "item_id": "zzz", "event": "like"}).status_code == 400


def test_event_triggers_rediagnosis():
    reset()
    client.post("/api/feed", json={"user_id": "u2", "body": {"ratio": {"S": 1, "W": 0, "N": 0}}})
    for _ in range(9):
        r = client.post("/api/feed/event", json={"user_id": "u2", "item_id": "t06", "event": "like"}).get_json()
        assert r["need_rediagnosis"] is False  # 10개 전까지는 판단 안 함
    r = client.post("/api/feed/event", json={"user_id": "u2", "item_id": "t06", "event": "like"}).get_json()
    assert r["need_rediagnosis"] is True and r["message"]


def test_routes_registered():
    rules = {r.rule for r in app.url_map.iter_rules()}
    assert {"/api/feed", "/api/feed/event", "/api/feed/scroll", "/feed-demo"} <= rules   # C
    assert {"/api/form", "/api/items", "/api/items/validate", "/api/recommend"} <= rules  # B


ANSWERS = {"q1": 1, "q2": 3, "q3": 1, "q4": 2, "q5": 1, "q6": 1, "q7": 3}   # try_classify.py 예시
MEASURES = {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1,
            "upper_body_length": 47.0, "leg_length": 86.1}


def test_feed_from_survey_answers():
    # A(설문 진단) → B(적합도) → C(피드)가 한 번에 이어지는지
    reset()
    res = client.post("/api/feed", json={"user_id": "a1", "answers": ANSWERS, "measurements": MEASURES})
    assert res.status_code == 200
    data = res.get_json()
    from body_profile import classify
    expected = classify(ANSWERS, MEASURES)
    assert data["body"]["primary"] == expected["primary"] == "S"
    assert data["body"]["kind"] == ("mixed" if expected["is_mixed"] else "single")
    assert abs(data["body"]["ratio"]["S"] - expected["ratios"]["S"]) < 1e-9
    # 진단한 체형이 저장돼서 스크롤도 바로 됨
    scroll = client.get("/api/feed/scroll?user_id=a1&page=0").get_json()
    assert [r["id"] for r in scroll["items"]] == [r["id"] for r in data["sections"][0]["items"]]


def test_feed_from_profile():
    reset()
    profile = {"ratios": {"S": 0.4, "W": 0.35, "N": 0.25}, "primary": "S", "secondary": "W",
               "confidence": 0.05, "is_mixed": True}
    data = client.post("/api/feed", json={"profile": profile}).get_json()
    assert data["body"]["kind"] == "mixed" and data["body"]["primary"] == "S"


def test_bad_survey_answers():
    reset()
    res = client.post("/api/feed", json={"answers": {"q1": 1}})
    assert res.status_code == 400 and "q2" in res.get_json()["error"]
    assert client.post("/api/feed", json={"profile": {"x": 1}}).status_code == 400


def test_scroll_pages():
    reset()
    q = "user_id=s1&S=0.8&W=0.1&N=0.1&season=fall&size=10"
    first = client.get(f"/api/feed/scroll?{q}&page=0").get_json()
    total = first["total"]
    assert first["has_more"] is True and len(first["items"]) == 10
    assert all(r["reason"] for r in first["items"])
    # 첫 묶음 = 홈 "오늘의 추천"
    home = client.get("/api/feed?user_id=s1&season=fall").get_json()
    assert [r["id"] for r in first["items"]] == [r["id"] for r in home["sections"][0]["items"]]

    seen, page = [], 0
    while True:
        data = client.get(f"/api/feed/scroll?user_id=s1&season=fall&size=10&page={page}").get_json()
        seen += [r["id"] for r in data["items"]]
        if not data["has_more"]:
            break
        page += 1
    assert len(seen) == total == len(set(seen))  # 끝까지 내리면 전부 한 번씩
    after_end = client.get(f"/api/feed/scroll?user_id=s1&page={page + 1}").get_json()
    assert after_end["items"] == [] and after_end["has_more"] is False


def test_scroll_bad_args():
    reset()
    assert client.get("/api/feed/scroll?S=1&page=-1").status_code == 400
    assert client.get("/api/feed/scroll?S=1&size=0").status_code == 400
    assert client.get("/api/feed/scroll?S=1&size=999").status_code == 400
    assert client.get("/api/feed/scroll?S=1&page=abc").status_code == 400


def test_demo_page():
    res = client.get("/feed-demo")
    assert res.status_code == 200 and "/api/feed/scroll" in res.get_data(as_text=True)


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
