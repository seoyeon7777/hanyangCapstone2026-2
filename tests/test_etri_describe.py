"""tools/etri_describe.py: 속성 → "~해요" 설명문 (데이터셋 파일 없이 도는 테스트)"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from etri_describe import describe  # noqa: E402
from etri_spacing import fix_spacing  # noqa: E402

ATTRS = {
    "F": ["단추 여밈의 전체 오픈형", "스탠드 칼라", "여유로운 핏", "허리가 더 가늘어 보인다", "칼라 없음"],
    "M": ["혼용율 면 COTTON 100%", "신축성 없음", "비침 약간 있음", "두께감 얇음"],
    "C": ["블랙 단추 포인트", "시원해 보이는 소라색 SKY BLUE", "단색의 깔끔한 느낌"],
    "E": ["여성스러운", "세련된", "깔끔한", "오피스 룩"],
}


def test_description_sentences():
    assert describe("BL", ATTRS) == (
        "블라우스예요. 여유로운 핏, 스탠드 칼라, 단추 여밈의 전체 오픈형이 특징이에요. "
        "소재는 면 100%예요. 신축성은 없고, 비침은 약간 있고, 두께는 얇은 편이에요. "
        "색상은 소라색 단색이에요. 여성스러운, 세련된 분위기라 오피스 룩으로 입기 좋아요."
    )


def test_accent_color_and_awkward_lines_are_not_used():
    text = describe("BL", ATTRS)
    assert "블랙" not in text          # 단추 색은 옷 색으로 쓰지 않는다
    assert "없음" not in text and "보인다" not in text


def test_subtype_name_and_empty_attributes():
    assert describe("CT", {"F": ["트렌치 코트"]}) == "트렌치 코트예요."
    assert describe("OP", {}) == "원피스예요."


def test_spacing_keeps_separate_words():
    assert fix_spacing("손 을 다 덮 는") == "손을 다 덮는"
    assert fix_spacing("제원단 으로 된 칼라") == "제원단으로 된 칼라"
