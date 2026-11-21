"""앱의 새 15문항 설문(survey_version=2) 채점 테스트. 옛 7문항 채점(기본)은 그대로여야 한다."""
import pytest

import body_profile as bp


def answers(values):
    return {f"q{i + 1}": v for i, v in enumerate(values)}


def test_all_a_is_straight():
    r = bp.classify(answers([1] * 15), survey_version=2)
    assert r["primary"] == "S"
    assert r["ratios"]["S"] == pytest.approx(1.0)
    assert not r["is_mixed"]


def test_all_b_is_wave_and_all_c_is_natural():
    assert bp.classify(answers([2] * 15), survey_version=2)["primary"] == "W"
    assert bp.classify(answers([3] * 15), survey_version=2)["primary"] == "N"


def test_early_result_with_seven_answers_uses_only_answered_questions():
    r = bp.classify(answers([2] * 7), survey_version=2)       # 조기 결과: 7개만 답함
    assert r["primary"] == "W"
    assert r["ratios"]["W"] == pytest.approx(1.0)


def test_close_scores_are_mixed():
    r = bp.classify(answers([1] * 8 + [2] * 7), survey_version=2)   # 8 대 7 → 격차 0.067 < 0.10
    assert r["primary"] == "S" and r["secondary"] == "W"
    assert r["is_mixed"]


def test_clear_lead_is_single():
    r = bp.classify(answers([1] * 9 + [2] * 3 + [3] * 3), survey_version=2)   # 격차 0.4
    assert r["primary"] == "S" and not r["is_mixed"]


def test_too_few_or_invalid_answers_are_rejected():
    with pytest.raises(bp.InputError):
        bp.classify(answers([1] * 6), survey_version=2)         # 7개 미만
    with pytest.raises(bp.InputError):
        bp.classify(answers([1] * 14 + [4]), survey_version=2)   # 보기 밖의 답
    with pytest.raises(bp.InputError):
        bp.classify({}, survey_version=2)


def test_default_is_still_the_old_seven_question_scoring():
    old = {"q1": 1, "q2": 1, "q3": 1, "q4": 1, "q5": 1, "q6": 1, "q7": 1}
    assert bp.classify(old)["primary"] == "S"
    with pytest.raises(bp.InputError):
        bp.classify(answers([1] * 6))      # 옛 방식은 q1~q7을 모두 요구한다
