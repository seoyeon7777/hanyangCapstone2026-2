"""A 파트: 카메라 보조 지표(가슴두께·어깨 경사각, 박선미·정은영 2024 논문 기준) 테스트.

기대값은 논문 Table 6 구간으로 손계산했다 (30대 기준).
  가슴두께(mm)  W 144~192, N 193~217, S 218~292
      중심: W = (144+192)/2 = 168, N = (193+217)/2 = 205, S = 218 + (217-193)/2 = 230
  어깨 경사각(도)  N: 왼 12~20 / 오 14~22 → 중심 (16+18)/2 = 17
                  S: 왼 21~25 / 오 24~26 → 중심 (23+25)/2 = 24
                  W: 왼 26~40 / 오 27~37 → 중심 (33+32)/2 = 32.5
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import body_profile as bp  # noqa: E402

MEASURES = {"height": 163.0, "shoulder_width": 31.8, "arm_length": 52.0,
            "upper_body_length": 45.0, "leg_length": 86.5}
ANSWERS = {f"q{i}": 2 for i in range(1, 8)}


def approx(d, **expected):
    for t in bp.TYPES:
        assert d[t] == pytest.approx(expected.get(t, 0.0), abs=1e-9), (t, d)


def test_chest_depth_membership_30s():
    approx(bp.chest_depth_membership(23.0), S=1.0)             # 230mm = S 중심
    approx(bp.chest_depth_membership(30.0), S=1.0)             # 중심보다 더 두꺼워도 S
    approx(bp.chest_depth_membership(20.5), N=1.0)             # 205mm = N 중심
    approx(bp.chest_depth_membership(18.65), W=0.5, N=0.5)     # 186.5mm: W 168 ~ N 205 의 한가운데
    approx(bp.chest_depth_membership(21.75), N=0.5, S=0.5)     # 217.5mm: N 205 ~ S 230 의 한가운데
    approx(bp.chest_depth_membership(12.0), W=1.0)             # 168mm 이하는 전부 W


def test_chest_depth_uses_age_band():
    # 20대: W 140~187 / N 188~211 / S 212~285 → N 중심 (188+211)/2 = 199.5mm
    approx(bp.chest_depth_membership(19.95, age=25), N=1.0)
    # 같은 값이라도 30대 기준이면 W 쪽 (199.5mm는 N 중심 205 보다 얇다)
    m30 = bp.chest_depth_membership(19.95)
    assert m30["W"] > 0 and m30["N"] < 1.0


def test_shoulder_slope_membership_30s():
    approx(bp.shoulder_slope_membership(24.0), S=1.0)
    approx(bp.shoulder_slope_membership(20.5), N=0.5, S=0.5)    # N 17 ~ S 24 의 한가운데
    approx(bp.shoulder_slope_membership(28.25), S=0.5, W=0.5)   # S 24 ~ W 32.5 의 한가운데
    approx(bp.shoulder_slope_membership(40.0), W=1.0)
    approx(bp.shoulder_slope_membership(5.0), N=1.0)


def test_age_band():
    assert bp.age_band(None) == "30대" and bp.age_band("x") == "30대" and bp.age_band(True) == "30대"
    assert bp.age_band(19) == "20대" and bp.age_band(25) == "20대"
    assert bp.age_band(30) == "30대" and bp.age_band(47) == "40대"
    assert bp.age_band(59) == "50대" and bp.age_band(71) == "60대"
    assert bp.age_band(300) == "30대"       # 말이 안 되는 나이는 기본값


def test_no_camera_shape_keys_changes_nothing():
    base = bp.score_avatar(MEASURES)
    assert bp.score_camera_shape(MEASURES) == {"S": 0.0, "W": 0.0, "N": 0.0}
    assert bp.classify(ANSWERS, MEASURES) == bp.classify(ANSWERS, dict(MEASURES, chest_depth=None))
    assert bp.score_avatar(dict(MEASURES, chest_depth=None, shoulder_slope=None)) == base


def test_out_of_range_camera_values_are_ignored_not_errors():
    base = bp.classify(ANSWERS, MEASURES)
    for bad in ({"chest_depth": 99}, {"chest_depth": -3}, {"shoulder_slope": 1}, {"shoulder_slope": 120},
                {"chest_depth": "abc"}, {"shoulder_slope": True}):
        assert bp.classify(ANSWERS, dict(MEASURES, **bad)) == base, bad


def test_camera_shape_adds_weighted_scores():
    # S 중심 가슴두께(23cm) + S 중심 어깨 경사각(24도) → S 점수에 0.5 + 0.5 가 더해진다
    plus = bp.score_avatar(dict(MEASURES, chest_depth=23.0, shoulder_slope=24.0))
    base = bp.score_avatar(MEASURES)
    assert plus["S"] - base["S"] == pytest.approx(bp.W_CHEST_DEPTH + bp.W_SHOULDER_SLOPE)
    assert plus["W"] == pytest.approx(base["W"]) and plus["N"] == pytest.approx(base["N"])


def test_camera_shape_moves_the_diagnosis_in_the_right_direction():
    thick_straight = bp.classify(ANSWERS, dict(MEASURES, chest_depth=24.0, shoulder_slope=24.0))
    thin_droopy = bp.classify(ANSWERS, dict(MEASURES, chest_depth=15.0, shoulder_slope=36.0))
    base = bp.classify(ANSWERS, MEASURES)
    assert thick_straight["ratios"]["S"] > base["ratios"]["S"]      # 두껍고 적당한 어깨 → S 상승
    assert thin_droopy["ratios"]["W"] > base["ratios"]["W"]         # 얇고 쳐진 어깨 → W 상승
    assert abs(sum(thick_straight["ratios"].values()) - 1) < 1e-9
