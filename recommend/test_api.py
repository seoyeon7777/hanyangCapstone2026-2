"""API 테스트 (Flask 필요). 저장소 맨 위 app.py(A+B+C 통합 서버)를 그대로 불러와 확인한다.

실행: python test_api.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from app import app  # noqa: E402
import feed_api  # noqa: E402
from feed import load_items  # noqa: E402
from store import MemoryStore  # noqa: E402

# database_url.txt가 있어도 팀 DB(Supabase)를 건드리지 않게 테스트는 항상 메모리 저장소로
feed_api.STORE = MemoryStore(load_items())
client = app.test_client()


def reset():
    feed_api.STORE.reset()


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
    score = lambda feed: next(r["score"] for r in feed["items"] if r["product_id"] == "t04")

    res = client.post("/api/feed/event", json={"user_id": "u1", "product_id": "t04", "event": "purchase"})
    assert res.status_code == 200
    assert res.get_json()["pref"] == {"len:crop": 0.2, "mat:knit": 0.2}

    after = client.get(whole).get_json()  # 체형은 저장된 값 재사용
    # 취향 0.5 → 0.6 → 최종 +0.25 * 0.1 = +0.025
    assert abs((score(after) - score(before)) - 0.025) < 1e-3

    bad = client.post("/api/feed/event", json={"user_id": "u1", "product_id": "t04", "event": "share"})
    assert bad.status_code == 400
    assert client.post("/api/feed/event", json={"user_id": "u1", "product_id": "zzz", "event": "like"}).status_code == 400


def test_event_triggers_rediagnosis():
    reset()
    client.post("/api/feed", json={"user_id": "u2", "body": {"ratio": {"S": 1, "W": 0, "N": 0}}})
    for _ in range(9):
        r = client.post("/api/feed/event", json={"user_id": "u2", "product_id": "t06", "event": "like"}).get_json()
        assert r["need_rediagnosis"] is False  # 10개 전까지는 판단 안 함
    r = client.post("/api/feed/event", json={"user_id": "u2", "product_id": "t06", "event": "like"}).get_json()
    assert r["need_rediagnosis"] is True and r["message"]


def test_routes_registered():
    rules = {r.rule for r in app.url_map.iter_rules()}
    assert {"/api/feed", "/api/feed/event", "/api/feed/scroll", "/api/diagnose", "/feed-demo"} <= rules   # C
    assert {"/api/form", "/api/items", "/api/items/validate", "/api/recommend"} <= rules  # B


ANSWERS = {"q1": 1, "q2": 3, "q3": 1, "q4": 2, "q5": 1, "q6": 1, "q7": 3}   # try_classify.py 예시
MEASURES = {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1,
            "upper_body_length": 47.0, "leg_length": 86.1}


def test_feed_from_survey_answers():
    # A(설문 진단) → B(적합도) → C(피드)가 한 번에 이어지는지. 치수는 보내도 진단에 안 쓴다 (설문만, 팀 결정)
    reset()
    res = client.post("/api/feed", json={"user_id": "a1", "answers": ANSWERS, "measurements": MEASURES})
    assert res.status_code == 200
    data = res.get_json()
    from body_profile import classify
    expected = classify(ANSWERS)
    assert data["body"]["primary"] == expected["primary"] == "S"
    assert data["body"]["kind"] == ("mixed" if expected["is_mixed"] else "single")
    assert abs(data["body"]["ratio"]["S"] - expected["ratios"]["S"]) < 1e-9
    # 진단한 체형이 저장돼서 스크롤도 바로 됨
    scroll = client.get("/api/feed/scroll?user_id=a1&page=0").get_json()
    assert [r["product_id"] for r in scroll["items"]] == [r["product_id"] for r in data["sections"][0]["items"]]


def test_feed_from_profile():
    reset()
    profile = {"ratios": {"S": 0.4, "W": 0.35, "N": 0.25}, "primary": "S", "secondary": "W",
               "confidence": 0.05, "is_mixed": True}
    data = client.post("/api/feed", json={"profile": profile}).get_json()
    assert data["body"]["kind"] == "mixed" and data["body"]["primary"] == "S"


def test_diagnose_same_as_home_feed():
    # 앱 설문 결과 화면(/api/diagnose)과 홈 추천(/api/feed)이 같은 계산(A 파트, 설문만)을 쓴다
    reset()
    from body_profile import classify
    expected = classify(ANSWERS)
    for body in ({"answers": ANSWERS}, {"answers": ANSWERS, "measurements": MEASURES}):
        d = client.post("/api/diagnose", json=body).get_json()
        assert d["ok"] and d["primary"] == expected["primary"] and d["is_mixed"] == expected["is_mixed"]
        assert d["ratios"] == expected["ratios"]                       # 치수를 보내도 설문만
        assert d["used_measurements"] is False and d["notice"] == ""
        feed = client.post("/api/feed", json=body).get_json()
        assert feed["body"]["primary"] == d["primary"]
        assert all(abs(feed["body"]["ratio"][t] - d["ratios"][t]) < 1e-9 for t in "SWN")


def test_measurements_switch_on():
    # 나중에 치수를 다시 켜면(rec_config) 설문+치수, 치수가 이상하면 그때도 설문만으로
    import rec_config
    from body_profile import classify
    reset()
    rec_config.DIAGNOSIS_USE_MEASUREMENTS = True
    try:
        d = client.post("/api/diagnose", json={"answers": ANSWERS, "measurements": MEASURES}).get_json()
        assert d["used_measurements"] is True and d["ratios"] == classify(ANSWERS, MEASURES)["ratios"]
        for bad in ({**MEASURES, "shoulder_width": 80}, {"height": 165}):
            d = client.post("/api/diagnose", json={"answers": ANSWERS, "measurements": bad}).get_json()
            assert d["ok"] and d["used_measurements"] is False and "설문만" in d["notice"]
            assert d["ratios"] == classify(ANSWERS)["ratios"]
            res = client.post("/api/feed/scroll", json={"user_id": "m1", "answers": ANSWERS, "measurements": bad})
            assert res.status_code == 200
    finally:
        rec_config.DIAGNOSIS_USE_MEASUREMENTS = False


def test_diagnose_bad_input():
    reset()
    assert client.post("/api/diagnose", json={}).status_code == 400
    res = client.post("/api/diagnose", json={"answers": {"q1": 1}})
    assert res.status_code == 400 and "q2" in res.get_json()["error"]
    assert client.post("/api/diagnose", json={"answers": ANSWERS, "measurements": "abc"}).status_code == 400


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
    assert [r["product_id"] for r in first["items"]] == [r["product_id"] for r in home["sections"][0]["items"]]

    seen, page = [], 0
    while True:
        data = client.get(f"/api/feed/scroll?user_id=s1&season=fall&size=10&page={page}").get_json()
        seen += [r["product_id"] for r in data["items"]]
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


def test_toggle_off_without_body():
    # 토글 OFF: 체형 진단 없이 홈 피드·스크롤·반응 기록이 모두 됨
    reset()
    res = client.get("/api/feed/scroll?user_id=g1&mode=general&season=fall")
    assert res.status_code == 200
    data = res.get_json()
    assert data["mode"] == "general" and data["body"] is None and data["total"] == 30
    assert all(r["reason"] for r in data["items"])
    home = client.get("/api/feed?user_id=g1&mode=general&season=fall").get_json()
    assert [s["title"] for s in home["sections"]][-1] == "추천 코디"
    ev = client.post("/api/feed/event", json={"user_id": "g1", "product_id": "t01", "event": "like"})
    assert ev.status_code == 200 and ev.get_json()["need_rediagnosis"] is False
    # 토글 ON은 여전히 체형이 필요
    assert client.get("/api/feed/scroll?user_id=g1&mode=body").status_code == 400


def test_toggle_switch_keeps_history():
    # 같은 사용자가 ON ↔ OFF를 바꿔도 체형·취향 기록이 이어짐
    reset()
    on = client.get("/api/feed/scroll?user_id=t1&S=0.8&W=0.1&N=0.1&mode=body&season=fall&size=50").get_json()
    off = client.get("/api/feed/scroll?user_id=t1&mode=general&season=fall&size=50").get_json()
    assert on["mode"] == "body" and off["mode"] == "general"
    assert [r["product_id"] for r in on["items"][:10]] != [r["product_id"] for r in off["items"][:10]]   # 순서가 다름
    client.post("/api/feed/event", json={"user_id": "t1", "product_id": "t04", "event": "purchase"})
    on2 = client.get("/api/feed/scroll?user_id=t1&mode=body&season=fall").get_json()     # 체형 다시 안 보내도 됨
    assert on2["body"]["primary"] == "S"
    off2 = client.get("/api/feed/scroll?user_id=t1&mode=general&season=fall&size=50").get_json()
    t04 = lambda f: next(r for r in f["items"] if r["product_id"] == "t04")
    # OFF에서도 구매 기록 반영: 취향 0.5 → 0.6 → 0.3 × 0.1 = +0.03
    assert abs((t04(off2)["score"] - t04(off)["score"]) - 0.03) < 1e-3


def test_bad_mode():
    reset()
    res = client.get("/api/feed/scroll?S=1&W=0&N=0&mode=random")
    assert res.status_code == 400 and "mode" in res.get_json()["error"]


def test_bad_inputs_return_json_400_not_500():
    # 점검에서 서버 오류(500)가 났던 입력들 → 이제 모두 400 + JSON
    reset()
    client.post("/api/feed", json={"user_id": "x", "body": {"ratio": {"S": 1}}})
    cases = [
        ("/api/feed/event", {"user_id": "x", "product_id": ["t01"], "event": "like"}),
        ("/api/feed", {"answers": {f"q{i}": 1 for i in range(1, 8)}, "measurements": "abc"}),
        ("/api/feed", {"body": "S"}),
        ("/api/feed", [1, 2]),
        ("/api/feed/event", [1, 2]),
        ("/api/feed", {"user_id": ["x"], "body": {"ratio": {"S": 1}}}),
        ("/api/feed", {"answers": "abc"}),
    ]
    for url, payload in cases:
        res = client.post(url, json=payload)
        assert res.status_code == 400, (url, payload, res.status_code)
        data = res.get_json()
        assert data["ok"] is False and data["error"], (url, payload)


def test_errors_are_json_everywhere():
    res = client.get("/api/없는주소")
    assert res.status_code == 404 and res.get_json()["ok"] is False
    res = client.delete("/api/feed")
    assert res.status_code == 405 and res.get_json()["ok"] is False


def test_get_missing_ratio_values_are_zero():
    # ?S=1만 보내면 W·N은 0 (POST와 같게)
    reset()
    res = client.get("/api/feed?S=1&mode=body")
    assert res.status_code == 200
    assert res.get_json()["body"]["ratio"] == {"S": 1.0, "W": 0.0, "N": 0.0}


def test_scroll_order_same_for_any_page_size():
    # 앱이 한 번에 몇 개를 받든(5·10·30·50개) 이어 붙인 순서는 같아야 한다
    # (예전에는 "같은 스타일 3개까지" 묶음을 요청한 size로 잡아서, size=30이면 순서가 달라졌다)
    reset()
    client.post("/api/feed", json={"user_id": "z1", "body": {"ratio": {"S": 1, "W": 0, "N": 0}}})

    def whole(size):
        out, page = [], 0
        while True:
            d = client.get(f"/api/feed/scroll?user_id=z1&season=fall&size={size}&page={page}").get_json()
            out += [r["product_id"] for r in d["items"]]
            if not d["has_more"]:
                return out
            page += 1

    base = whole(10)
    assert len(base) == 30
    for size in (5, 7, 30, 50):
        assert whole(size) == base, size


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
