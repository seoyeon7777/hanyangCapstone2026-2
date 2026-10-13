import pytest

import matching
import server

PROFILE = {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False, "secondary": "N"}


@pytest.fixture
def client():
    return server.create_app(matching.load_rules()).test_client()


def shirt(**kw):
    body = {"category": "top", "name": "슬림 셔츠", "source": "direct",
            "attrs": {"category": "top", "fit": "슬림", "length_top": "기본"},
            "composition_percent": [{"fiber": "cotton", "percent": 60}, {"fiber": "polyester", "percent": 40}]}
    body.update(kw)
    return body


def test_form_definition(client):
    r = client.get("/api/form").get_json()
    keys = {f["key"]: f for f in r["fields"]}
    assert keys["fit"]["required"] and keys["fit"]["options"] == ["슬림", "레귤러", "오버(루즈)"]
    assert "확인 불가" in r["composition_statuses"] and "해당 없음" in keys["details"]["options"]


def test_validate_preview_does_not_store(client):
    r = client.post("/api/items/validate", json=shirt(name="와이드 데님 셔츠", desc="하이웨이스트")).get_json()
    assert r["ok"] and "fit:slim" in r["tags"] and "mat:cotton" in r["tags"] and "mat:denim" in r["text_added"]
    assert r["fit_stated"] == "slim"
    assert client.get("/api/items").get_json()["items"] == []


def test_validation_errors_are_400_with_message(client):
    bad = shirt()
    bad["attrs"] = {"category": "top", "fit": "슬림"}                      # 기장 누락
    r = client.post("/api/items/validate", json=bad)
    assert r.status_code == 400 and "기장" in r.get_json()["error"]
    both = shirt()
    both["attrs"]["composition"] = [{"fiber": "cotton", "share": 1.0}]      # composition과 percent 동시
    assert client.post("/api/items/validate", json=both).status_code == 400
    over = shirt(composition_percent=[{"fiber": "cotton", "percent": 60}, {"fiber": "polyester", "percent": 30}])    # 합 90%
    assert client.post("/api/items/validate", json=over).status_code == 400


def test_register_and_recommend_end_to_end(client):
    pants = {"category": "pants", "name": "일자 슬랙스", "source": "manual", "item_id": 20,
             "attrs": {"category": "pants", "fit": "레귤러", "length_pants": "풀 길이", "silhouette": "일자(스트레이트)", "composition_status": "확인 불가"}}
    assert client.post("/api/items", json=shirt(item_id=10)).status_code == 201
    assert client.post("/api/items", json=pants).status_code == 201
    assert client.post("/api/items", json=pants).status_code == 409                       # 중복 id
    assert len(client.get("/api/items").get_json()["items"]) == 2
    r = client.post("/api/recommend", json={"profile": PROFILE, "top_n": 1}).get_json()
    assert r["ok"] and r["outfits"][0]["top"] == 10 and r["outfits"][0]["bottom"] == 20
    by_id = {m["item_id"]: m for m in r["items"]}
    assert by_id[20]["fit_stated"] == "regular" and "sil:IH" in by_id[20]["tags"] and by_id[10]["reasons"]
    assert r["fit_unknown"] == []


def test_register_rejects_bad_requests(client):
    assert client.post("/api/items", json=shirt(source="import")).status_code == 400            # 출처
    assert client.post("/api/items", json=shirt(category="pants")).status_code == 400           # category 불일치
    assert client.post("/api/items", json={**shirt(), "name": ""}).status_code == 400
    assert client.post("/api/recommend", json={}).status_code == 400                            # profile 없음
    assert client.post("/api/recommend", json={"profile": {"ratios": {"S": 1}}}).status_code == 400
    assert client.post("/api/recommend", json={"profile": PROFILE, "top_n": 0}).status_code == 400


def test_recommend_with_inline_items_for_feed(client):
    items = [{"item_id": 1, "category": "top", "name": "슬림 칼라 셔츠", "tags": ["fit:slim"]},
             {"item_id": 2, "category": "pants", "name": "일자 팬츠", "tags": ["sil:IH"]}]
    r = client.post("/api/recommend", json={"profile": PROFILE, "items": items}).get_json()
    assert r["ok"] and len(r["outfits"]) == 1 and r["fit_unknown"] == [2]                          # 저장소를 쓰지 않아도 된다


def test_recommend_rejects_text_only_items(client):
    items = [{"item_id": 1, "category": "top", "name": "슬림 칼라 셔츠", "desc": "슬림핏"}]       # 가져온 글만 있는 상품은 서비스 경로에 없다
    r = client.post("/api/recommend", json={"profile": PROFILE, "items": items})
    assert r.status_code == 400 and "글만 있는" in r.get_json()["error"]
