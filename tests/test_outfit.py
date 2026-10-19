"""코디 점수·밸런스 규칙 (PDF 8쪽). 규칙은 tests/fixtures 에 고정한 사본을 쓴다."""
from pathlib import Path

import pytest

import matching

FIXTURE_DIR = Path(__file__).parent / "fixtures"
S = {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False}
W = {"ratios": {"S": 0.15, "W": 0.65, "N": 0.20}, "primary": "W", "is_mixed": False}
N = {"ratios": {"S": 0.20, "W": 0.15, "N": 0.65}, "primary": "N", "is_mixed": False}
MIXED_SN = {"ratios": {"S": 0.45, "W": 0.10, "N": 0.45}, "primary": "S", "is_mixed": True}


@pytest.fixture
def rules():
    return matching.load_rules(data_dir=FIXTURE_DIR, weights_path=FIXTURE_DIR / "pdf_weights.json")


def item(i, score, tags=()):
    return {"product_id": i, "score": score, "tags": list(tags)}


def test_pdf_example_straight_ih_bonus(rules):  # (0.75 + 0.74) / 2 + 0.10 = 0.845 (PDF는 ≈0.84)
    top = item(1, 0.75, ["fit:slim", "mat:structured", "neck:collar", "det:vertical"])
    bottom = item(2, 0.74, ["sil:IH"])
    assert matching.outfit_score(top, bottom, S, rules) == pytest.approx(0.845)


def test_no_rule_is_plain_average(rules):
    assert matching.outfit_score(item(1, 0.6, ["len:crop"]), item(2, 0.4, ["sil:A"]), S, rules) == pytest.approx(0.5)


def test_straight_penalty_over_top_wide_bottom(rules):
    assert matching.outfit_score(item(1, 0.6, ["fit:over"]), item(2, 0.6, ["sil:wide"]), S, rules) == pytest.approx(0.45)


def test_wave_bonus_and_penalty(rules):
    assert matching.outfit_score(item(1, 0.6, ["len:crop"]), item(2, 0.6, ["waist:high"]), W, rules) == pytest.approx(0.70)
    assert matching.outfit_score(item(1, 0.6, ["fit:over"]), item(2, 0.6, ["fit:over"]), W, rules) == pytest.approx(0.45)


def test_natural_bonus_and_penalty(rules):
    assert matching.outfit_score(item(1, 0.6, ["fit:over"]), item(2, 0.6, ["sil:IH"]), N, rules) == pytest.approx(0.70)
    assert matching.outfit_score(item(1, 0.6, ["fit:slim"]), item(2, 0.6, ["sil:skinny"]), N, rules) == pytest.approx(0.45)


def test_rule_of_other_type_does_not_apply(rules):
    # 내추럴 보너스 조합(오버핏 상의 + 일자 하의)은 스트레이트에서는 I/H 보너스만 받는다
    assert matching.outfit_score(item(1, 0.6, ["fit:over"]), item(2, 0.6, ["sil:IH"]), S, rules) == pytest.approx(0.70)


def test_mixed_weights_rules_by_share(rules):
    # S 45% / N 45% -> 각 50%. 오버핏 상의 + 와이드 하의: S 감점 -0.15, N 보너스 +0.10
    got = matching.outfit_score(item(1, 0.6, ["fit:over"]), item(2, 0.6, ["sil:wide"]), MIXED_SN, rules)
    assert got == pytest.approx(0.6 + 0.5 * -0.15 + 0.5 * 0.10)


def test_score_is_clipped(rules):
    assert matching.outfit_score(item(1, 0.98, []), item(2, 0.98, ["sil:IH"]), S, rules) == 1.0
    assert matching.outfit_score(item(1, 0.05, ["fit:over"]), item(2, 0.05, ["sil:wide"]), S, rules) == 0.0


def test_body_fit_key_is_accepted(rules):  # match_item 결과를 그대로 넘겨도 된다
    a = {"product_id": 1, "body_fit": 0.6, "tags": []}
    b = {"product_id": 2, "body_fit": 0.4, "tags": []}
    assert matching.outfit_score(a, b, S, rules) == pytest.approx(0.5)


def test_excluded_item_raises(rules):
    with pytest.raises(ValueError):
        matching.outfit_score({"product_id": 1, "body_fit": None, "tags": []}, item(2, 0.5), S, rules)


# ---- recommend_outfits ----

def test_recommend_unique_top_and_order(rules):
    tops = [item("t1", 0.9), item("t2", 0.8), item("t3", 0.7)]
    bottoms = [item("b1", 0.9), item("b2", 0.5)]
    out = matching.recommend_outfits(tops, bottoms, S, rules)
    assert [o["top"] for o in out] == ["t1", "t2", "t3"]  # 같은 상의 중복 없음
    assert [o["bottom"] for o in out] == ["b1", "b1", "b1"]
    assert out[0]["score"] >= out[1]["score"] >= out[2]["score"]


def test_recommend_skips_excluded_and_limits_k(rules):
    tops = [{"product_id": "x", "body_fit": None, "tags": []}, item("t1", 0.5)]
    bottoms = [item("b1", 0.5)]
    out = matching.recommend_outfits(tops, bottoms, S, rules)
    assert [o["top"] for o in out] == ["t1"]
    cfg = {**rules.config, "outfit_top_k": 1}
    small = matching.Rules(rules.weights, rules.keywords, rules.reasons, cfg, rules.w_min, rules.w_max, rules.balance)
    out = matching.recommend_outfits([item("t1", 0.9), item("t2", 0.8)], [item("b1", 0.5)], S, small)
    assert [o["top"] for o in out] == ["t1"]  # 상위 1개만 후보


def test_recommend_attaches_best_outer_and_balance_info(rules):
    tops = [item("t1", 0.6, ["fit:over"])]
    bottoms = [item("b1", 0.6, ["sil:wide"])]
    outers = [item("o1", 0.4), item("o2", 0.8)]
    out = matching.recommend_outfits(tops, bottoms, S, rules, outers=outers)
    assert out[0]["outer"] == "o2"
    assert out[0]["balance"][0]["name"] == "오버핏 상의 + 와이드 하의"


def test_recommend_empty_inputs(rules):
    assert matching.recommend_outfits([], [item("b", 0.5)], S, rules) == []


# ---- 규칙 점검 ----

def test_validate_flags_balance_tag_without_keyword():
    live = matching.load_rules()
    assert matching.validate_rules(live) == []
    trimmed = {k: v for k, v in live.keywords.items() if "waist:high" not in v}
    broken = matching.Rules(live.weights, trimmed, live.reasons, live.config, live.w_min, live.w_max, live.balance)
    assert any("waist:high" in p for p in matching.validate_rules(broken))
