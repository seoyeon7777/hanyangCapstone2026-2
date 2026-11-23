"""새 설문 15문항 채점 (body_profile.SURVEY15, 11/15)

- "version": 2 가 있으면 새 표, 없으면 옛 7문항 표 (예전 답은 그대로)
- 보기 A=스트레이트, B=웨이브, C=내추럴 2점. 가중치 Q3·Q14 1.5, Q15 0.5, 나머지 1.0
- 7문항 이상, 앞에서부터 이어서 답한 문항만 채점하고 답한 문항의 최고점으로 나눈다
"""
import itertools

import pytest

import body_profile as P


def v2(*values):
    """q1부터 차례로 답한 새 설문"""
    return {"version": 2, **{f"q{i + 1}": v for i, v in enumerate(values)}}


def test_all_a_is_straight():
    r = P.classify(v2(*[1] * 15))
    assert r["primary"] == "S"
    assert r["ratios"]["S"] == pytest.approx(1.0)


def test_weights():
    assert P.SURVEY15["q3"]["weight"] == 1.5
    assert P.SURVEY15["q14"]["weight"] == 1.5
    assert P.SURVEY15["q15"]["weight"] == 0.5
    assert all(P.SURVEY15[f"q{i}"]["weight"] == 1.0
               for i in range(1, 16) if i not in (3, 14, 15))


def test_q15_counts_half():
    # q1~q14: A 7개(가중치 합 7.5: q3 포함) / B 7개(q14 포함 7.5) → 동점, q15만 C
    answers = [1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3]
    r = P.classify(v2(*answers))
    # 최고점 = 2 × (12×1.0 + 2×1.5 + 0.5) = 31 → S 15/31, W 15/31, N 1/31
    assert r["ratios"]["S"] == pytest.approx(15 / 31)
    assert r["ratios"]["W"] == pytest.approx(15 / 31)
    assert r["ratios"]["N"] == pytest.approx(1 / 31)


def test_stop_early_uses_only_answered():
    # 8문항에서 멈춤: 답한 8문항(최고점 2×8.5=17)으로만 나눈다
    r = P.classify(v2(1, 1, 1, 2, 2, 3, 3, 3))
    assert r["ratios"]["S"] == pytest.approx(7 / 17)     # q1,q2 + q3(1.5) = 3.5 → 7점
    assert r["ratios"]["W"] == pytest.approx(4 / 17)
    assert r["ratios"]["N"] == pytest.approx(6 / 17)


def test_tie_uses_q14_then_q3():
    # q1~q14 중 S·W 동점, q14 = B → 웨이브가 1위
    answers = [1, 2, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 2]
    r = P.classify(v2(*answers))
    assert r["ratios"]["S"] == pytest.approx(r["ratios"]["W"])
    assert r["primary"] == "W"


def test_need_seven_answers():
    with pytest.raises(P.InputError):
        P.classify(v2(1, 1, 1, 1, 1, 1))


def test_no_gap_allowed():
    answers = v2(*[1] * 7)
    answers["q9"] = 1          # q8을 건너뜀
    with pytest.raises(P.InputError):
        P.classify(answers)


def test_bad_option():
    with pytest.raises(P.InputError):
        P.classify(v2(1, 1, 1, 1, 1, 1, 4))


def test_old_survey_unchanged():
    old = {f"q{i}": 1 for i in range(1, 8)}
    assert P.classify(old) == P.classify(dict(old))
    assert not P.is_survey15(old)
    assert P.survey_tie_order(old) == P.TYPES


def test_balanced_over_all_seven_answer_combos():
    """3개 체형이 골고루: 7문항 답 2,187가지 모두에서 1위 개수가 같다"""
    count = {t: 0 for t in P.TYPES}
    for combo in itertools.product([1, 2, 3], repeat=7):
        count[P.classify(v2(*combo))["primary"]] += 1
    assert count["S"] == count["W"] == count["N"]
