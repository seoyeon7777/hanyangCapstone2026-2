"""tools/etri_spacing.py: 형태소 단위로 띄어 쓴 문장의 띄어쓰기 정리 (데이터셋 파일 없이 도는 테스트)"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from etri_spacing import fix_spacing  # noqa: E402


@pytest.mark.parametrize("raw, expected", [
    ("신축성 없 음", "신축성 없음"),
    ("두께감 있 는", "두께감 있는"),
    ("손목 까지 내려오 는 소매", "손목까지 내려오는 소매"),
    ("깔끔_한", "깔끔한"),
    ("여성 스럽 고 섹시 한 느낌", "여성스럽고 섹시한 느낌"),
    ("활동 적 인", "활동적인"),
    ("뒤 로 묶_어 허리 라인 잡_아", "뒤로 묶어 허리 라인 잡아"),
    ("구김 이 가 기 쉬운", "구김이 가기 쉬운"),
    ("허리 가 더 가 늘_어 보인_다", "허리가 더 가늘어 보인다"),
    # 따로 쓰는 말은 그대로 둔다
    ("가을 느낌 을 줌", "가을 느낌을 줌"),
    ("앞 단 처리", "앞 단 처리"),
    ("면 100%", "면 100%"),
    ("RIB 로 마감", "RIB로 마감"),
    ("A 라 인형 실루엣", "A라인형 실루엣"),
    ("가로 이랑 짜임", "가로 이랑 짜임"),
])
def test_fix_spacing(raw, expected):
    assert fix_spacing(raw) == expected
