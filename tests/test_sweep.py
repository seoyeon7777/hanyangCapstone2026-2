"""다양한 상품을 등록·태깅한 뒤, A가 낼 수 있는 여러 체형 결과(단일형·혼합형)로 추천이 일관되는지 본다.
체형 입력은 tests/fixtures/a_profiles.json (A의 실제 출력)에 더해, 비율을 바꿔 가며 만든 혼합형을 쓴다."""
import itertools

import pytest

import matching
import server

RULES = matching.load_rules()
FITS = ["슬림", "레귤러", "오버(루즈)"]
FIBERS = ["cotton", "wool", "linen", "polyester"]
SIL = ["와이드", "A라인", "일자(스트레이트)", "스키니", "해당 없음"]
EXTRA = [{}, {"details": ["러플·프릴"]}, {"details": ["리본"]}, {"fabrics": ["가죽"]}, {"fabrics": ["니트"]}]


@pytest.fixture(scope="module")
def items():
    c = server.create_app(RULES).test_client()
    n = 0

    def reg(cat, attrs, fib):
        nonlocal n
        n += 1
        r = c.post("/api/items", json={"item_id": n, "category": cat, "name": f"{cat}{n}", "source": "direct",
                                       "attrs": {"category": cat, **attrs}, "composition_percent": [{"fiber": fib, "percent": 100}]})
        assert r.status_code == 201, r.get_json()

    for cat in ("top", "outer"):
        for f, l, fib, ex in itertools.product(FITS, ["크롭", "기본", "롱"], FIBERS, EXTRA):
            reg(cat, {"fit": f, "length_top": l, **ex}, fib)
    for f, l, s, fib in itertools.product(FITS, ["무릎 길이", "풀 길이"], SIL, FIBERS):
        reg("pants", {"fit": f, "length_pants": l, "silhouette": s}, fib)
    for f, l, s, fib in itertools.product(FITS, ["미니", "미디", "맥시"], ["A라인", "일자(스트레이트)", "해당 없음"], FIBERS):
        reg("skirt", {"fit": f, "length_skirt": l, "silhouette": s}, fib)
    return c.get("/api/items").get_json()["items"]


def pure(t):
    return {"ratios": {x: 1.0 if x == t else 0.0 for x in "SWN"}, "primary": t, "is_mixed": False}


def mixed(a, b, share, gap_ok=True):
    r = {x: 0.0 for x in "SWN"}
    r[a], r[b] = share, 1 - share
    return {"ratios": r, "primary": a if share >= 0.5 else b, "secondary": b if share >= 0.5 else a, "confidence": abs(2 * share - 1), "is_mixed": abs(2 * share - 1) < 0.10}


PAIRS = [(a, b) for a in "SWN" for b in "SWN" if a != b]


def test_every_product_is_scored_for_every_type(items):
    for t in "SWN":
        out = matching.recommend(pure(t), items, RULES)
        assert len(out["items"]) == len(items) and all(m["body_fit"] is not None for m in out["items"])


@pytest.mark.parametrize("a,b", PAIRS)
@pytest.mark.parametrize("share", [0.5, 0.55, 0.7])
def test_blend_is_the_ratio_mix_of_pure_scores(items, a, b, share):
    p = mixed(a, b, share)
    base = {t: {m["item_id"]: m["body_fit"] for m in matching.recommend(pure(t), items, RULES)["items"]} for t in (a, b)}
    for m in matching.recommend(p, items, RULES)["items"]:
        want = p["ratios"][a] * base[a][m["item_id"]] + p["ratios"][b] * base[b][m["item_id"]]
        assert m["body_fit"] == pytest.approx(want, abs=1e-6)


@pytest.mark.parametrize("t", "SWN")
def test_pure_type_top_ranks_have_no_negative_tag_for_that_type(items, t):
    out = matching.recommend(pure(t), items, RULES, list_size=10)
    by = {m["item_id"]: m for m in out["items"]}
    for group, ranked in out["ranking"].items():
        for x in ranked:
            assert not [g for g in by[x["item_id"]]["tags"] if g in RULES.weights and RULES.weights[g][t] < 0], (t, group, x)


@pytest.mark.parametrize("a,b", PAIRS)
def test_mixed_rankings_differ_from_each_pure_type(items, a, b):
    """혼합형 1위가 한쪽 단일형 1위를 그대로 따라가면 안 된다 (섞은 의미가 없다). 최소한 단일형 둘의 1위가 서로 달라야 한다."""
    tops = {t: matching.recommend(pure(t), items, RULES)["ranking"]["top"][0]["item_id"] for t in (a, b)}
    assert tops[a] != tops[b]


@pytest.mark.parametrize("strategy", ["blend", "quota", "quota_safe"])
@pytest.mark.parametrize("a,b", PAIRS)
def test_strategies_return_full_lists_without_duplicates(items, strategy, a, b):
    out = matching.recommend(mixed(a, b, 0.52), items, RULES, list_size=10, mixed_strategy=strategy)
    for group, ranked in out["ranking"].items():
        ids = [x["item_id"] for x in ranked]
        assert len(ids) == 10 and len(set(ids)) == 10, (group, ids)
    assert len(out["outfits"]) == 3
