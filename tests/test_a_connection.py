"""A(body_profile.classify)에서 B(추천)까지 이어지는 경로. A 파일이 같은 폴더에 없으면 연결 시험은 건너뛴다."""
import sys
import types

import pytest

import server

ANSWERS = {f"q{i}": 1 for i in range(1, 8)}
ANSWERS_N = {"q1": 3, "q2": 3, "q3": 3, "q4": 3, "q5": 3, "q6": 3, "q7": 3}
MEAS = {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1, "upper_body_length": 47.0, "leg_length": 86.1}
ITEMS = [{"item_id": 1, "category": "top", "name": "a", "tags": ["fit:slim"]},
         {"item_id": 2, "category": "top", "name": "b", "tags": ["fit:over"]},
         {"item_id": 3, "category": "pants", "name": "c", "tags": ["sil:wide"]}]


@pytest.fixture
def client():
    return server.create_app().test_client()


def test_both_profile_and_answers_rejected(client):
    r = client.post("/api/recommend", json={"profile": {}, "answers": ANSWERS, "items": ITEMS})
    assert r.status_code == 400


def test_neither_profile_nor_answers_rejected(client):
    assert client.post("/api/recommend", json={"items": ITEMS}).status_code == 400


def test_missing_a_module_is_clear_400(client, monkeypatch):
    monkeypatch.setitem(sys.modules, "body_profile", None)       # import 실패
    r = client.post("/api/recommend", json={"answers": ANSWERS, "items": ITEMS})
    assert r.status_code == 400 and "body_profile" in r.get_json()["error"]


def test_fake_a_module_flow(client, monkeypatch):
    fake = types.ModuleType("body_profile")

    class InputError(ValueError):
        pass
    fake.InputError = InputError
    fake.classify = lambda a, m=None: {"ratios": {"S": .2, "W": .1, "N": .7}, "primary": "N", "secondary": "S", "confidence": .5, "is_mixed": False}
    monkeypatch.setitem(sys.modules, "body_profile", fake)
    r = client.post("/api/recommend", json={"answers": ANSWERS, "items": ITEMS})
    assert r.status_code == 200
    j = r.get_json()
    assert j["profile"]["primary"] == "N" and j["ranking"]["top"][0]["item_id"] == 2          # N은 오버핏

    def boom(a, m=None):
        raise InputError("q1 값이 이상하다")
    fake.classify = boom
    r = client.post("/api/recommend", json={"answers": ANSWERS, "items": ITEMS})
    assert r.status_code == 400 and "q1" in r.get_json()["error"]


def test_bad_answers_shapes_are_400(client, monkeypatch):
    fake = types.ModuleType("body_profile")
    fake.InputError = type("InputError", (ValueError,), {})
    fake.classify = lambda a, m=None: (_ for _ in ()).throw(AttributeError("x"))
    monkeypatch.setitem(sys.modules, "body_profile", fake)
    for payload in ({"answers": []}, {"answers": "x"}, {"answers": ANSWERS, "measurements": [1]}, {"answers": ANSWERS, "measurements": {"height": []}}):
        r = client.post("/api/recommend", json={**payload, "items": ITEMS})
        assert r.status_code == 400, payload


# ---- 진짜 A 코드가 있을 때
def test_real_a_to_b(client):
    bp = pytest.importorskip("body_profile")
    for answers, meas in ((ANSWERS, None), (ANSWERS_N, MEAS), (ANSWERS, MEAS)):
        a = bp.classify(answers, meas)
        direct = client.post("/api/recommend", json={"profile": a, "items": ITEMS}).get_json()
        via = client.post("/api/recommend", json={"answers": answers, "measurements": meas, "items": ITEMS}).get_json()
        assert via["ok"] and via["ranking"] == direct["ranking"] and via["outfits"] == direct["outfits"]
        assert via["profile"]["primary"] == a["primary"]


def test_real_a_bad_input(client):
    pytest.importorskip("body_profile")
    r = client.post("/api/recommend", json={"answers": {"q1": 9}, "items": ITEMS})
    assert r.status_code == 400
    r = client.post("/api/recommend", json={"answers": ANSWERS, "measurements": {"height": [1]}, "items": ITEMS})
    assert r.status_code == 400
