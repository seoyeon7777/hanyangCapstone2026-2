"""ETRI 속성 문장에 태그를 붙이기 전의 전처리.

tagging.py의 키워드는 쇼핑몰 상품명·설명에서 "옷 전체"를 말하는 표현을 읽도록 만들어졌다.
ETRI 속성은 부위를 한 줄씩 설명하고 낱말을 띄어 써서, 그대로 넣으면 두 가지가 틀린다.
  1. "일자형 소매", "와이드 커프스"처럼 부위의 모양이 옷 전체의 실루엣·핏 태그가 된다 → 그 키워드를 지운다
  2. "A 라인", "하이 웨이스트"처럼 띄어 써서 키워드를 놓친다 → 붙여 쓴다
다른 상품 데이터에 맞춰 시험해 온 tagging.py는 바꾸지 않는다.
"""
import re

from tagging import tag_item_full

# 띄어 써서 놓치는 말 → 키워드 모양
_JOIN = (
    (re.compile(r"(?<![A-Za-z])[Aa]\s*라인"), "A라인"),
    (re.compile(r"에이\s+라인"), "에이라인"),
    (re.compile(r"(?<![A-Za-z])[Hh]\s*라인"), "일자 라인"),
    (re.compile(r"하이\s+(웨이스트|웨스트|라이즈)"), r"하이\1"),
    (re.compile(r"오버\s+(핏|사이즈)"), r"오버\1"),
    (re.compile(r"테일러드\s+롱\s*(?=코트)"), "롱 "),
)

# 옷 전체의 모양이 아니라 이 부위의 모양을 말하는 낱말.  "후드 티/집업"은 옷 이름이라 뺀다
_PART = (r"(?:소매|커프스|슬리브(?!리스)|암홀|칼라|카라|네크|넥|라펠|포켓|주머니|스트랩|벨트|밸트|리본|"
         r"프린팅|프린트|트임|단추|버튼\s*라인|지퍼|스티치|프릴|러플|밴드|끈|후드(?!\s*(?:티|집업|점퍼|셔츠|원피스|코트|맨투맨|니트|가디건|조끼|자켓|재킷|패딩)))")
# 모양을 나타내는 키워드 (tagging 규칙의 실루엣·핏 키워드)
_SHAPE = r"(?:일자|스트레이트|와이드|슬림|넉넉|루즈|박시|오버사이즈|오버핏|타이트|스키니|A라인|에이라인|플레어)"
# "플레어짐·플레어지고"처럼 서술어로 끝난 말은 뒤의 부위를 꾸미지 않으므로 넣지 않는다
_SUFFIX = r"(?:형|한|핏|의|로|하게|하고|스러운|된|인)*(?!짐|지|져|진)"
# 꾸미는 말과 부위 사이에 낄 수 있는 낱말 (두 개까지). "핏·실루엣·몸"이 끼면 옷 전체를 말한 것으로 본다
_BETWEEN = r"(?:\s+(?!\S*(?:핏|실루엣|몸))\S{1,5}){0,2}?"


def _part_patterns(hem_is_part: bool):
    part = _PART[:-1] + (r"|밑단)" if hem_is_part else ")")
    return (
        # "일자형 소매", "와이드한 일자형의 어깨 스트랩"
        re.compile(rf"{_SHAPE}{_SUFFIX}(?={_BETWEEN}\s*{part})"),
        # "소매는 일자", "소매 끝단이 플레어짐"
        re.compile(rf"({part}(?:은|는|이|가|도|의)?(?:\s*(?:폭|통|끝단|끝|부분|모양)(?:은|는|이|가)?)?\s*)"
                   rf"(?:약간\s*|살짝\s*)?{_SHAPE}"),
    )


# 스커트·원피스에서 "밑단이 플레어"는 옷의 A라인 실루엣이다. 다른 옷에서는 밑단도 부위로 본다
_PATTERNS = {True: _part_patterns(True), False: _part_patterns(False)}
_SLEEVE_HEM = re.compile(r"소매\s*(?:밑단|끝단)[^,]{0,6}?플레어")


def _strip_part_shapes(line: str, hem_is_part: bool) -> str:
    before, after = _PATTERNS[hem_is_part]
    line = _SLEEVE_HEM.sub("소매", line)
    # 뒤에서부터 지워야 "와이드한 일자형 소매"의 두 낱말이 모두 지워진다
    previous = None
    while previous != line:
        previous = line
        line = before.sub(" ", line)
        line = after.sub(r"\1", line)
    return line


def prepare_line(line: str, clothing_type: str) -> str:
    """띄어 쓴 키워드를 붙이고, 부위를 꾸미는 모양 낱말을 지운다."""
    for pattern, new in _JOIN:
        line = pattern.sub(new, line)
    skirt_like = clothing_type in ("skirt", "dress")
    flare_on_hem = skirt_like and re.search(r"플레어", line) and not re.search(r"일자|스트레이트|와이드", line)
    return _strip_part_shapes(line, hem_is_part=not flare_on_hem)


def etri_tags(name: str, attrs: dict, clothing_type: str, rules) -> list:
    """{'F': [...], 'M': [...], 'E': [...]} 속성 → 태그 목록 (색상 C는 쓰지 않는다)"""
    text = "\n".join(prepare_line(line, clothing_type) for ftype in ("F", "M", "E") for line in attrs.get(ftype, []))
    return tag_item_full(prepare_line(name, clothing_type), text, rules)["tags"]
