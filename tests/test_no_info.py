"""정보 없음(가중치 태그 0개 + 핏 못 읽음) 상품은 추천에서 빼고, 핏을 읽은 기본 상품은 남긴다."""
import dataclasses

import matching
import server

R = matching.load_rules()
PROFILE = {"ratios": {"S": .6, "W": .1, "N": .3}, "primary": "S", "is_mixed": False}


def item(i, tags, fit=None, cat="top"):
    d = {"item_id": i, "category": cat, "name": "x", "tags": tags}
    if fit:
        d["fit_stated"] = fit
    return d


def test_no_tags_and_unknown_fit_is_no_info():
    m = matching.match_item(item(1, []), PROFILE, R)
    assert m["no_info"] and m["excluded"] and m["body_fit"] is None


def test_unweighted_tags_only_is_still_no_info():
    m = matching.match_item(item(1, ["fit:regular_not_in_table"]), PROFILE, R)
    assert m["no_info"]


def test_regular_fit_plain_item_is_kept_at_neutral():
    m = matching.match_item(item(1, [], fit="regular"), PROFILE, R)
    assert not m["no_info"] and not m["excluded"] and abs(m["body_fit"] - 1 / 3) < 1e-9


def test_item_with_weighted_tag_is_kept():
    m = matching.match_item(item(1, ["mat:cotton"]), PROFILE, R)
    assert not m["no_info"] and m["body_fit"] is not None


def test_switch_off_keeps_neutral():
    R2 = dataclasses.replace(R, config={**R.config, "exclude_no_info": False})
    m = matching.match_item(item(1, []), PROFILE, R2)
    assert not m["no_info"] and m["body_fit"] is not None


def test_recommend_reports_no_info_and_skips_in_ranking_and_outfits():
    items = [item(1, ["fit:slim"]), item(2, []), item(3, ["sil:IH"], cat="pants"), item(4, [], cat="pants"), item(5, [], fit="regular", cat="pants")]
    out = matching.recommend(PROFILE, items, R)
    assert out["no_info"] == [2, 4]
    assert [r["item_id"] for r in out["ranking"]["top"]] == [1]
    assert {r["item_id"] for r in out["ranking"]["bottom"]} == {3, 5}
    assert all(o["top"] == 1 and o["bottom"] in (3, 5) for o in out["outfits"])


def test_form_registered_plain_item_is_not_no_info():
    c = server.create_app().test_client()
    r = c.post("/api/items", json={"source": "manual", "category": "top", "name": "기본 티", "attrs": {"category": "top", "fit": "레귤러", "length_top": "기본"},
                                    "composition_percent": [{"fiber": "polyester", "percent": 100}]})
    assert r.status_code == 201 and r.get_json()["item"]["tags"] == []
    rec = c.post("/api/recommend", json={"profile": PROFILE}).get_json()
    assert rec["no_info"] == [] and rec["ranking"]["top"][0]["item_id"] == 1
