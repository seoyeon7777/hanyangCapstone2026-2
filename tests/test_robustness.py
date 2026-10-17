"""로직 점검에서 재현된 결함의 회귀 시험 (입력 검증, id 충돌, 느린 정규식, 코디 후보 수)."""
import json
import time
from pathlib import Path

import pytest

import matching
import server
import tagging

R = matching.load_rules()
PROFILE = {"ratios": {"S": .5, "W": .3, "N": .2}, "primary": "S", "is_mixed": False}


# ---- 프로필
@pytest.mark.parametrize("bad", [float("nan"), float("inf"), -0.1])
def test_profile_rejects_nonfinite_and_negative(bad):
    p = {**PROFILE, "ratios": {"S": bad, "W": .3, "N": .2}}
    with pytest.raises(ValueError):
        matching.normalize_profile(p)


def test_profile_primary_must_be_largest():
    with pytest.raises(ValueError, match="primary"):
        matching.normalize_profile({**PROFILE, "primary": "N"})


def test_profile_primary_tie_allowed():
    p = {"ratios": {"S": .4, "W": .4, "N": .2}, "primary": "W", "is_mixed": True}
    assert matching.normalize_profile(p)["primary"] == "W"


# ---- 상품 형식
def item(**kw):
    return {"item_id": 1, "category": "top", "name": "a", "desc": "", "tags": ["fit:slim"], **kw}


@pytest.mark.parametrize("kw", [
    {"item_id": None}, {"item_id": [1]}, {"item_id": True}, {"item_id": ""},
    {"category": None}, {"category": "hat"},
    {"name": 5}, {"desc": 5}, {"desc": "x" * (tagging.MAX_DESC_CHARS + 1)},
    {"tags": "fit:slim"}, {"tags": [1, 2]}, {"tags": {"a": 1}},
])
def test_recommend_rejects_bad_item(kw):
    with pytest.raises(ValueError):
        matching.recommend(PROFILE, [item(**kw)], R)


def test_recommend_rejects_duplicate_id():
    with pytest.raises(ValueError, match="중복"):
        matching.recommend(PROFILE, [item(), item()], R)


# ---- 서버
@pytest.fixture
def client():
    return server.create_app().test_client()


FORM = {"source": "manual", "category": "top", "name": "t", "attrs": {"category": "top", "fit": "슬림", "length_top": "기본"}, "composition_percent": [{"fiber": "cotton", "percent": 100}]}


def test_server_auto_id_skips_manual_ids(client):
    assert client.post("/api/items", json={**FORM, "item_id": 2}).status_code == 201
    a = client.post("/api/items", json=FORM)
    b = client.post("/api/items", json=FORM)
    c = client.post("/api/items", json=FORM)
    assert [r.status_code for r in (a, b, c)] == [201, 201, 201]
    assert len({r.get_json()["item"]["item_id"] for r in (a, b, c)} | {2}) == 4


@pytest.mark.parametrize("payload", [[], "x", 3, {"name": 5}, {"item_id": [1]}])
def test_server_bad_bodies_are_400_not_500(client, payload):
    for url in ("/api/items", "/api/items/validate", "/api/recommend"):
        r = client.post(url, json=payload)
        assert r.status_code in (400, 409), (url, payload, r.status_code)


def test_server_bad_item_fields_400(client):
    assert client.post("/api/items", json={**FORM, "desc": 5}).status_code == 400
    assert client.post("/api/items", json={**FORM, "attrs": [1]}).status_code == 400
    assert client.post("/api/items", json={**FORM, "desc": "가" * 6000}).status_code == 400


def test_server_recommend_rejects_nan_profile(client):
    body = '{"profile": {"ratios": {"S": NaN, "W": 0.3, "N": 0.2}, "primary": "S", "is_mixed": false}, "items": []}'
    r = client.post("/api/recommend", data=body, content_type="application/json")
    assert r.status_code == 400


def test_server_recommend_items_not_list(client):
    r = client.post("/api/recommend", json={"profile": PROFILE, "items": {"a": 1}})
    assert r.status_code == 400


def test_server_request_size_limit(client):
    r = client.post("/api/items", data=" " * 1_100_000, content_type="application/json")
    assert r.status_code == 413


# ---- 느린 정규식
@pytest.mark.parametrize("ch", [" ", "\n", "가", "가,", " ,"])
def test_clean_desc_is_fast_on_long_runs(ch):
    t0 = time.time()
    tagging._clean_desc("슬림핏 " + ch * 100_000 + "x")
    assert time.time() - t0 < 5


# ---- 코디 후보 수
def test_outfit_top_k_covers_best_pair():
    ks = R.config["outfit_top_k"]
    assert ks >= 50


@pytest.mark.parametrize("comp", [{"cotton": 100}, "cotton", [1, 2], [{"fiber": "cotton"}], [{"fiber": "cotton", "percent": "x"}]])
def test_server_bad_composition_400(client, comp):
    r = client.post("/api/items", json={**FORM, "composition_percent": comp})
    assert r.status_code == 400, r.get_json()


@pytest.mark.parametrize("attrs", [
    {"category": "top", "fit": ["슬림"], "length_top": "기본"},
    {"category": "top", "fit": "슬림", "length_top": "기본", "composition": "x"},
    {"category": "top", "fit": "슬림", "length_top": "기본", "composition": [1]},
    {"category": "top", "fit": "슬림", "length_top": "기본", "composition": [{"fiber": "cotton", "share": "a"}]},
    {"category": "top", "fit": "슬림", "length_top": "기본", "details": "러플·프릴"},
    {"category": ["top"]},
])
def test_server_bad_attrs_400(client, attrs):
    r = client.post("/api/items/validate", json={"attrs": attrs})
    assert r.status_code == 400, (attrs, r.get_json())
