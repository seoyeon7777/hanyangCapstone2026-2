"""tools/etri_tagging.py: 부위 설명이 옷 전체의 태그가 되지 않고, 띄어 쓴 키워드를 놓치지 않는지"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

import matching  # noqa: E402
from etri_tagging import etri_tags  # noqa: E402

RULES = matching.load_rules()


def shape_tags(line, clothing_type="top"):
    return {t for t in etri_tags("", {"F": [line]}, clothing_type, RULES) if t.startswith(("sil:", "fit:", "waist:"))}


@pytest.mark.parametrize("line", [
    "손목까지 내려오는 일자형 소매", "와이드 커프스", "바깥 쪽 트임이 있는 와이드한 커프스", "와이드한 일자형 소매",
    "슬림한 소매 폭으로 손목 밑까지 오는 소매 길이", "넉넉한 와이드형 소매", "와이드 프린팅 티셔츠",
    "일자형 밑단", "일자형 암홀의 슬리브리스", "와이드한 일자형의 어깨 스트랩을 앞 뒤 몸판에 연결",
    "밴딩 처리로 소매 끝단이 플레어짐", "퍼프 소매와 소매 밑단의 플레어로 러블리한",
])
def test_part_shapes_are_not_garment_tags(line):
    assert shape_tags(line) == set()


@pytest.mark.parametrize("line, clothing_type, expected", [
    ("스트레이트 실루엣", "dress", {"sil:IH"}),
    ("몸판은 슬림한 일자형 실루엣", "top", {"fit:slim", "sil:IH"}),
    ("엉덩이를 덮는 길이의 일자형 몸통", "top", {"sil:IH"}),
    ("넉넉한 와이드 핏", "pants", {"fit:over", "sil:wide"}),
    ("박시한 핏의 후드 티", "top", {"fit:over"}),
    ("루즈핏 후드 집업", "outer", {"fit:over"}),
    ("스커트는 밑단이 플레어로 퍼짐", "skirt", {"sil:A"}),
    ("풍성한 A 라인 실루엣", "dress", {"sil:A"}),
    ("하이 웨이스트 팬츠", "pants", {"waist:high"}),
    ("오버 사이즈 핏", "top", {"fit:over"}),
    ("H 라인 스커트", "skirt", {"sil:IH"}),
    ("전체적으로 박시한 스타일의 후드 맨투맨", "top", {"fit:over"}),
    ("주름으로 스커트 전체 플레어짐 깊은 브이넥 라인", "dress", {"sil:A"}),
])
def test_garment_shapes_are_kept(line, clothing_type, expected):
    assert shape_tags(line, clothing_type) == expected


def test_other_tags_on_part_lines_remain():
    assert "det:ruffle" in etri_tags("", {"F": ["어깨에서 허리까지 풍성한 러플 장식"]}, "top", RULES)
