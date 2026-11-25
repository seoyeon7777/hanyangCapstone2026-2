"""tools/etri_describe.py: 속성 → "~해요" 설명문 (데이터셋 파일 없이 도는 테스트)"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

from etri_describe import describe, option_colors, plain_korean, product_name  # noqa: E402
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
        "색상은 하늘색 단색이에요. 여성스러운, 세련된 분위기라 오피스 룩으로 입기 좋아요."
    )


def test_accent_color_and_awkward_lines_are_not_used():
    text = describe("BL", ATTRS)
    assert "블랙" not in text          # 단추 색은 옷 색으로 쓰지 않는다
    assert "없음" not in text and "보인다" not in text


def test_subtype_name_and_empty_attributes():
    assert describe("CT", {"F": ["트렌치 코트"]}) == "트렌치 코트예요."
    assert describe("OP", {}) == "원피스예요."
    assert describe("SW", {"F": ["전체적으로 박시한 맨투맨"]}) == "맨투맨이에요."


def test_product_name_uses_body_color_and_pattern():
    assert product_name("BL", ATTRS) == "하늘색 블라우스"          # 단추 색(블랙)은 쓰지 않는다
    assert product_name("OP", {"C": ["아이보리 바탕", "잔잔한 플로럴 패턴"]}) == "아이보리 플로럴 원피스"
    assert product_name("CT", {"F": ["트렌치 코트"]}) == "트렌치 코트"


def test_japanese_style_words_are_replaced_in_shown_text():
    assert plain_korean("시보리로 마감한 오픈카라와 세라 칼라") == "고무단으로 마감한 오픈칼라와 세일러 칼라"
    assert describe("SH", {"F": ["카라 티셔츠"], "C": ["소라색", "스카이 블루"]}) == "칼라 티셔츠예요. 색상은 하늘색이에요."


def test_denim_names():
    jeans = {"M": ["워싱된 데님 소재"], "C": ["워싱되지 않은 블루 데님 칼라"]}
    assert product_name("PT", jeans) == "청바지"
    assert describe("PT", jeans).startswith("청바지예요.")
    assert product_name("PT", {"F": ["와이드 데님 팬츠"], "C": ["하늘색"]}) == "와이드 청바지"
    assert product_name("PT", {"F": ["부츠컷 슬랙스"], "C": ["비비드한 블루 컬러"]}) == "블루 부츠컷 슬랙스"
    assert product_name("PT", {"M": ["데님"], "C": ["워싱된 그레이 컬러"]}) == "그레이 데님 바지"
    assert product_name("SH", {"M": ["얇은 데님"], "C": ["블루"]}) == "데님 셔츠"
    assert product_name("JK", {"F": ["데님 자켓"], "C": ["와인"]}) == "와인 데님 자켓"
    # 코디 설명이나 장식의 데님은 옷의 소재가 아니다
    assert product_name("KN", {"E": ["데님과 잘 어울리는"], "C": ["블루"]}) == "블루 니트"
    assert product_name("PT", {"M": ["데님 패치 장식"], "C": ["블루"]}) == "블루 바지"


def test_option_color_is_the_one_color_in_the_photo():
    assert option_colors(ATTRS) == ["하늘색"]
    assert option_colors({"C": ["아이보리", "그린"]}) == ["아이보리"]
    assert option_colors({"C": ["블랙 단추 포인트"]}) == []


def test_spacing_keeps_separate_words():
    assert fix_spacing("손 을 다 덮 는") == "손을 다 덮는"
    assert fix_spacing("제원단 으로 된 칼라") == "제원단으로 된 칼라"
