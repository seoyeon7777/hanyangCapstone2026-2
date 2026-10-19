"""A(체형 분류)의 출력 형식을 받는 부분 검증."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import matching  # noqa: E402

A_OUTPUT = {"ratios": {"S": 0.54, "W": 0.17, "N": 0.29}, "primary": "S", "secondary": "N",
            "confidence": 0.25, "is_mixed": False}


def test_a_example_is_accepted_as_is():
    out = matching.normalize_profile(A_OUTPUT)
    assert out["primary"] == "S" and out["is_mixed"] is False and out["secondary"] == "N"
    assert out["ratios"] == pytest.approx({"S": 0.54, "W": 0.17, "N": 0.29})


def test_ratios_are_renormalized_when_rounding_breaks_sum():
    out = matching.normalize_profile({**A_OUTPUT, "ratios": {"S": 0.54, "W": 0.17, "N": 0.30}})  # 합 1.01
    assert sum(out["ratios"].values()) == pytest.approx(1.0)


def test_legacy_names_still_accepted():
    out = matching.normalize_profile({"p": {"S": 0.6, "W": 0.2, "N": 0.2}, "main": "S", "mixed": True})
    assert out["primary"] == "S" and out["is_mixed"] is True


def test_extra_fields_do_not_change_result():
    rules = matching.load_rules()
    base = {"ratios": A_OUTPUT["ratios"], "primary": "S", "is_mixed": False}
    assert matching.body_fit(["fit:slim"], A_OUTPUT, rules) == matching.body_fit(["fit:slim"], base, rules)


@pytest.mark.parametrize("bad", [
    {"primary": "S", "is_mixed": False},                                                    # ratios 없음
    {"ratios": {"S": 0.5, "W": 0.5}, "primary": "S", "is_mixed": False},                    # N 누락
    {"ratios": {"S": -0.1, "W": 0.6, "N": 0.5}, "primary": "S", "is_mixed": False},         # 음수
    {"ratios": {"S": 0, "W": 0, "N": 0}, "primary": "S", "is_mixed": False},                # 합 0
    {"ratios": {"S": 0.5, "W": 0.3, "N": 0.2}, "primary": "X", "is_mixed": False},          # 잘못된 유형
    {"ratios": {"S": 0.5, "W": 0.3, "N": 0.2}, "primary": "S"},                             # is_mixed 없음
    {"ratios": {"S": 0.5, "W": 0.3, "N": 0.2}, "primary": "S", "is_mixed": "false"},        # bool 아님
    {"ratios": {"S": "0.5", "W": 0.3, "N": 0.2}, "primary": "S", "is_mixed": False},        # 숫자 아님
])
def test_bad_profile_raises(bad):
    with pytest.raises(ValueError):
        matching.normalize_profile(bad)


def test_bad_profile_raises_from_public_functions():
    rules = matching.load_rules()
    with pytest.raises(ValueError):
        matching.match_item({"product_id": 1, "product_name": "슬림 셔츠"}, {"primary": "S"}, rules)
