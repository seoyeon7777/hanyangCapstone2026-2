"""등록(태깅) -> 저장 -> A의 체형 결과(혼합형 포함) -> 추천까지 이어서 확인한다.
체형 입력은 A(body_profile.classify)를 실제로 돌려 얻은 출력이다 (tests/fixtures/a_profiles.json)."""
import json
from pathlib import Path

import pytest

import matching
import server

PROFILES = json.loads((Path(__file__).parent / "fixtures" / "a_profiles.json").read_text(encoding="utf-8"))
C = lambda f="cotton": [{"fiber": f, "percent": 100}]
PRODUCTS = [   # (id, category, name, attrs, 혼용률)
    ("T1", "top", "슬림 셔츠", {"fit": "슬림", "length_top": "기본"}, C()),
    ("T2", "top", "오버 린넨 티", {"fit": "오버(루즈)", "length_top": "롱"}, C("linen")),
    ("T3", "top", "크롭 러플 블라우스", {"fit": "레귤러", "length_top": "크롭", "details": ["러플·프릴"]}, C("polyester")),
    ("T4", "top", "기본 티", {"fit": "레귤러", "length_top": "기본"}, C()),
    ("B1", "pants", "와이드 팬츠", {"fit": "오버(루즈)", "length_pants": "풀 길이", "silhouette": "와이드"}, C()),
    ("B2", "skirt", "A라인 미디", {"fit": "레귤러", "length_skirt": "미디", "silhouette": "A라인"}, C("polyester")),
    ("B3", "pants", "일자 슬랙스", {"fit": "슬림", "length_pants": "풀 길이", "silhouette": "일자(스트레이트)"}, C("wool")),
    ("O1", "outer", "롱 오버 코트", {"fit": "오버(루즈)", "length_top": "롱"}, C("wool")),
    ("O2", "outer", "슬림 가죽 재킷", {"fit": "슬림", "length_top": "기본", "fabrics": ["가죽"]}, C("polyester")),
]


@pytest.fixture()
def client():
    c = server.create_app(matching.load_rules()).test_client()
    for i, cat, name, attrs, comp in PRODUCTS:
        r = c.post("/api/items", json={"item_id": i, "category": cat, "name": name, "source": "direct",
                                       "attrs": {"category": cat, **attrs}, "composition_percent": comp})
        assert r.status_code == 201, r.get_json()
    return c


def recommend(c, name, **kw):
    r = c.post("/api/recommend", json={"profile": PROFILES[name]["result"], **kw})
    assert r.status_code == 200, r.get_json()
    return r.get_json()


def ids(out, group):
    return [x["item_id"] for x in out["ranking"][group]]


def test_tags_are_stored_at_registration(client):
    stored = {it["item_id"]: it for it in client.get("/api/items").get_json()["items"]}
    assert stored["T1"]["tags"] == ["fit:slim", "mat:cotton"] and stored["T1"]["fit_stated"] == "slim"
    assert stored["B1"]["tags"] == ["fit:over", "sil:wide", "mat:cotton"]
    assert stored["T4"]["fit_stated"] == "regular"          # 레귤러는 점수 태그가 없어도 '밝힘'으로 남는다


def test_recommend_reads_stored_tags_only(client, monkeypatch):
    def boom(*a, **k):
        raise AssertionError("추천 단계에서 태깅이 다시 돌았다")
    monkeypatch.setattr(matching, "tag_item_full", boom)
    monkeypatch.setattr(matching, "tag_from_attributes", boom)
    out = recommend(client, "S_N")
    assert out["items"] and out["fit_unknown"] == []


@pytest.mark.parametrize("name,top,bottom,outer", [("pure_S", "T1", "B3", "O2"), ("pure_W", "T3", "B2", "O2"), ("pure_N", "T2", "B1", "O1")])
def test_pure_types_pick_their_own_items(client, name, top, bottom, outer):
    out = recommend(client, name)
    assert (ids(out, "top")[0], ids(out, "bottom")[0], ids(out, "outer")[0]) == (top, bottom, outer)
    assert (out["outfits"][0]["top"], out["outfits"][0]["bottom"]) == (top, bottom)


@pytest.mark.parametrize("name", ["S_N", "S_W", "W_N", "N_S"])
def test_mixed_profile_is_between_the_pure_scores(client, name):
    """혼합형 점수는 각 유형 점수를 비율로 섞은 값이라, 어떤 상품이든 구성 유형 점수의 최솟값~최댓값 사이에 있다."""
    prof = PROFILES[name]["result"]
    mixed = {m["item_id"]: m["body_fit"] for m in recommend(client, name)["items"]}
    for t in "SWN":
        assert prof["ratios"][t] >= 0
    pure = {t: {m["item_id"]: m["body_fit"] for m in recommend(client, f"pure_{t}")["items"]} for t in "SWN"}
    used = [t for t in "SWN" if prof["ratios"][t] > 0.05]
    for i, v in mixed.items():
        lo, hi = min(pure[t][i] for t in used), max(pure[t][i] for t in used)
        assert lo - 0.15 <= v <= hi + 0.15, (name, i, v, lo, hi)      # pure_N은 S가 0.03 섞여 있어 완전한 순수형이 아니므로 여유를 둔다


def test_mixed_quota_serves_both_types(client):
    out = recommend(client, "S_N", mixed_strategy="quota")
    picked = {x["picked_for"] for g in out["ranking"].values() for x in g}
    assert picked == {"S", "N"}
    top = out["ranking"]["top"]
    assert {x["item_id"] for x in top[:2]} >= {"T1", "T2"}        # S 몫 1위(슬림)와 N 몫 1위(오버·롱)가 둘 다 앞에 든다


def test_quota_safe_never_puts_item_bad_for_either_type_first(client):
    out = recommend(client, "S_N", mixed_strategy="quota_safe")
    assert out["ranking"]["top"][0]["item_id"] != "T3"            # 러플·크롭은 S -1, N -1(크롭)이라 안전 후보가 아니다


def test_unknown_strategy_and_bad_profile_are_400(client):
    assert client.post("/api/recommend", json={"profile": PROFILES["S_N"]["result"], "mixed_strategy": "x"}).status_code == 400
    bad = {"ratios": {"S": 0.5, "W": 0.5}, "primary": "S", "is_mixed": True}
    assert client.post("/api/recommend", json={"profile": bad}).status_code == 400


def test_reasons_follow_mixed_profile(client):
    items = {m["item_id"]: m for m in recommend(client, "S_N")["items"]}
    assert any("슬림" in r for r in items["T1"]["reasons"])        # S 몫
    assert any("넉넉" in r or "여유" in r for r in items["B1"]["reasons"])     # N 몫

