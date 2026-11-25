"""ETRI 패션 데이터셋 속성으로 상품 설명문("~해요" 체)을 만든다.

입력은 tools/etri_spacing.py로 띄어쓰기를 정리한 TSV. 속성에 적힌 말만 골라 정해진 문장에 넣으므로
속성에 없는 내용은 설명에 들어가지 않는다. 문장으로 만들기 어색한 줄("~있음", "~보인다" 등)은 쓰지 않는다.

    python tools/etri_describe.py "<데이터 폴더>/mdata_정리.tsv" "<저장할 파일>.tsv"

결과 파일(id, 종류 코드, clothing_type, 설명)은 라이선스(ETRI) 때문에 저장소에 넣지 않는다.
"""
import re
import sys
from collections import OrderedDict

# 종류 코드 → (설명에 쓸 이름, 앱의 clothing_type). 신발(SE)은 만들지 않는다
KINDS = {
    "BL": ("블라우스", "top"), "SH": ("셔츠", "top"), "KN": ("니트", "top"), "SW": ("맨투맨", "top"),
    "CD": ("가디건", "outer"), "CT": ("코트", "outer"), "JK": ("재킷", "outer"), "JP": ("점퍼", "outer"),
    "VT": ("조끼", "outer"), "PT": ("바지", "pants"), "SK": ("스커트", "skirt"), "OP": ("원피스", "dress"),
}

# ---------- 형태(F): 종류별로 하나씩 고른다. 마지막 낱말이 아래 명사인 줄만 문장에 넣는다 ----------
FEATURE_GROUPS = (
    ("fit", ("실루엣", "핏", "루즈핏")),
    ("neck", ("칼라", "카라", "네크라인", "넥라인", "넥", "후드", "라펠")),
    ("sleeve", ("소매", "슬리브", "숄더", "슬리브리스", "암홀")),
    ("length", ("길이", "기장")),
    ("detail", ("여밈", "오픈형", "포켓", "주머니", "디테일", "장식", "트임", "절개선", "밑단", "처리",
                "배색", "포인트", "라인", "스커트", "팬츠", "웨이스트", "밴딩", "형태", "스타일")),
)
MAX_PHRASE = 24     # 이보다 긴 줄은 문장이 어색해져 쓰지 않는다
MAX_FEATURES = 3

# 문장에 넣으면 뜻이 뒤집히거나 어색해지는 말
BAD_IN_FEATURE = re.compile(r"없|않|사이즈|모델|착용")

# 앱에서 부를 이름으로 쓸 수 있는 세부 종류 낱말 (F 줄이 이 낱말로 끝나고 짧으면 이름에 쓴다)
SUBTYPE_ENDS = ("코트", "자켓", "재킷", "점퍼", "가디건", "조끼", "블라우스", "셔츠", "니트", "티셔츠",
                "맨투맨", "후디", "원피스", "팬츠", "스커트", "베스트",
                "슬랙스", "수트", "슈트", "레깅스")

# ---------- 데님 ----------
BLUE_DENIM = ("블루", "하늘색", "네이비")          # 이 색의 데님은 색 이름 대신 "청바지·데님"으로 부른다
# 옷 자체가 데님이라고 볼 수 없는 말: 장식·배색, 다른 옷과의 코디, 데님처럼 보이는 가죽
_NOT_DENIM_BODY = re.compile(r"패치|배색|배합|매치|코디|어울|함께|레더")
# 형태(F) 줄이 자기 옷 이름으로 끝날 때: "데님 팬츠", "데님 롱 조끼", "스키니 진", "청 자켓"
_DENIM_NAME_LINE = re.compile(r"(?:데님(?:\s\S+){0,2}\s?(?:팬츠|바지|진|자켓|재킷|점퍼|스커트|치마|조끼|셔츠|원피스|코트)|"
                              r"(?:스키니|와이드|부츠컷|일자|배기)\s진|청\s?(?:바지|자켓|재킷|치마|스커트))$")


def is_denim(attrs: dict[str, list[str]]) -> bool:
    """소재(M)에 데님이 적혔거나, 형태(F) 줄이 데님 옷 이름일 때. 코디 설명(E)은 보지 않는다"""
    if any("데님" in line and not _NOT_DENIM_BODY.search(line) for line in attrs.get("M", [])):
        return True
    return any(_DENIM_NAME_LINE.search(line) and not _NOT_DENIM_BODY.search(line) for line in attrs.get("F", []))


def _jeans_noun(subtype: str) -> str:
    """"바지"→"청바지", "와이드 데님 팬츠"→"와이드 청바지" """
    words = [w for w in subtype.split() if w != "데님"]
    if words and words[-1] in ("바지", "팬츠", "진", "청바지"):
        words.pop()
    return " ".join(words + ["청바지"])


def garment_noun(kind_code: str, attrs: dict[str, list[str]]) -> str:
    """설명 첫 문장과 이름에 쓸 옷 이름. 파란 데님 바지는 "청바지" """
    subtype = pick_subtype(attrs.get("F", []), KINDS[kind_code][0])
    # 데님이어도 점프 수트·레깅스·슬랙스는 청바지라고 부르지 않는다
    if kind_code == "PT" and is_denim(attrs) and subtype.split()[-1] in ("바지", "팬츠", "진", "청바지"):
        colors, _ = main_colors(attrs.get("C", []))
        if not colors or colors[0] in BLUE_DENIM:
            return _jeans_noun(subtype)
    return subtype


# 이름이 아니라 문장 조각인 줄: "전체적으로 박시한 맨투맨", "머메이드 라인의 스커트"
NOT_A_NAME = re.compile(r"전체|(?:라인|형태|스타일)의|느낌|으로")

# ---------- 색상(C) ----------
COLORS = (   # (찾을 말, 설명에 쓸 말). 긴 말이 먼저
    ("스카이 블루", "하늘색"), ("스카이블루", "하늘색"), ("하늘색", "하늘색"), ("소라색", "하늘색"),
    ("베이비 핑크", "베이비핑크"), ("연 베이지", "연베이지"), ("연베이지", "연베이지"),
    ("블랙", "블랙"), ("검정", "블랙"), ("화이트", "화이트"), ("흰색", "화이트"), ("아이보리", "아이보리"),
    ("크림", "크림"), ("베이지", "베이지"), ("브라운", "브라운"), ("갈색", "브라운"), ("카멜", "카멜"),
    ("카키", "카키"), ("올리브", "올리브"), ("민트", "민트"), ("그린", "그린"), ("네이비", "네이비"),
    ("남색", "네이비"), ("블루", "블루"), ("차콜", "차콜"), ("그레이", "그레이"), ("회색", "그레이"),
    ("핑크", "핑크"), ("버건디", "버건디"), ("와인", "와인"), ("레드", "레드"), ("빨강", "레드"),
    ("오렌지", "오렌지"), ("머스타드", "머스타드"), ("옐로우", "옐로우"), ("노란색", "옐로우"),
    ("라벤더", "라벤더"), ("퍼플", "퍼플"), ("보라", "퍼플"),
)
# 이런 말이 있는 색상 줄은 옷 본체가 아니라 장식·부자재 색일 수 있어 건너뛴다
ACCENT = re.compile(r"단추|버튼|지퍼|안감|로고|스티치|자수|테두리|배색|포인트|벨트|끈|프린트|라벨|장식")
PATTERNS = (("스트라이프", "스트라이프 무늬"), ("줄무늬", "줄무늬"), ("체크", "체크 무늬"), ("도트", "도트 무늬"),
            ("플로럴", "플로럴 무늬"), ("플라워", "플라워 무늬"))
MAX_COLORS = 3

# ---------- 분위기(E) ----------
MOODS = (
    "캐주얼한", "편안한", "여성스러운", "깔끔한", "귀여운", "차분한", "모던한", "심플한", "세련된", "발랄한",
    "단정한", "활동적인", "베이직한", "여유로운", "우아한", "유니크한", "포근한", "내추럴한", "스포티한",
    "부드러운", "빈티지한", "시크한", "고급스러운", "화려한", "사랑스러운", "트렌디한", "화사한", "클래식한",
    "페미닌한", "단아한", "로맨틱한", "러블리한", "여리여리한", "실용적인", "은은한",
)
OCCASIONS = (("데일리", "데일리 룩"), ("데이트", "데이트 룩"), ("캠퍼스", "캠퍼스 룩"), ("오피스", "오피스 룩"),
             ("하객", "하객 룩"), ("바캉스", "바캉스 룩"), ("피크닉", "피크닉 룩"))


# 화면에 보이는 글(설명·상품명)에서 우리말로 바꿔 쓸 일본식 표현. 태그는 원문으로 붙이므로 영향이 없다
PLAIN_WORDS = (("소라색", "하늘색"), ("시보리", "고무단"), ("세라 칼라", "세일러 칼라"), ("카라", "칼라"))
# "고무단"은 받침이 있어 "시보리를·시보리로"의 조사도 바꿔야 한다
_SIBORI_JOSA = {"를": "을", "가": "이", "로": "으로", "는": "은", "와": "과"}


def plain_korean(text: str) -> str:
    text = re.sub(r"시보리([를가로는와])(?![가-힣])", lambda m: "고무단" + _SIBORI_JOSA[m.group(1)], text)
    for old, new in PLAIN_WORDS:
        text = text.replace(old, new)
    return text


def _has_batchim(word: str) -> bool:
    ch = word[-1:] if word else ""
    if "가" <= ch <= "힣":
        return (ord(ch) - 0xAC00) % 28 != 0
    return False


def ieyo(word: str) -> str:
    """'블라우스예요' / '원피스예요' / '혼방이에요'"""
    return word + ("이에요" if _has_batchim(word) else "예요")


def i_ga(word: str) -> str:
    return word + ("이" if _has_batchim(word) else "가")


def pick_features(lines: list[str]) -> list[str]:
    chosen: list[str] = []
    for _, heads in FEATURE_GROUPS:
        for line in lines:
            words = line.split()
            if not words or len(line) > MAX_PHRASE or BAD_IN_FEATURE.search(line):
                continue
            if words[-1] not in heads:
                continue
            if line in chosen or len(words) < 2 and line in ("핏", "실루엣", "길이", "기장", "소매"):
                continue
            chosen.append(line)
            break
        if len(chosen) >= MAX_FEATURES:
            break
    return chosen


def pick_subtype(lines: list[str], kind_name: str) -> str:
    """'트렌치 코트'처럼 세부 종류가 적힌 줄이 있으면 그 이름, 없으면 종류 이름"""
    for line in lines:
        words = line.split()
        if 2 <= len(words) <= 3 and len(line) <= 14 and words[-1] in SUBTYPE_ENDS \
                and not BAD_IN_FEATURE.search(line) and not NOT_A_NAME.search(line):
            return line
    return kind_name


_COMPOSITION = re.compile(r"(?:[가-힣A-Za-z]+(?: [가-힣A-Za-z]+)?\s?\d{1,3}\s?%)")


# 섬유 이름 앞에 붙은 말("혼용율", "겉감")과 한글 이름 뒤에 겹쳐 적은 영문 이름("면 COTTON")
_MATERIAL_NOISE = re.compile(r"혼용[률율]?\s*:?|겉감\s*:?|(?<=[가-힣]) [A-Z]{2,}(?=\s?\d)")


def material_sentence(lines: list[str]) -> str:
    for line in lines:
        # 안감 혼용률은 겉감과 섞이지 않게 뺀다
        line_clean = " ".join(_MATERIAL_NOISE.sub("", line.split("안감")[0]).split())
        parts = _COMPOSITION.findall(line_clean)
        if parts:
            parts = [re.sub(r"\s?(\d)", r" \1", p, count=1).replace(" %", "%").strip() for p in parts]
            return f"소재는 {', '.join(parts)}예요."
    for line in lines:
        if line.endswith("혼방") and len(line) <= 20:
            return f"소재는 {ieyo(line)}."
    return ""


def _segment(lines: list[str], key: str) -> str:
    """'신축성 없음 두께감 보통'처럼 한 줄에 여러 속성이 있으면 key 뒤, 다음 속성 앞까지만 본다"""
    for line in lines:
        if line.startswith(key) or f" {key}" in line:
            tail = line[line.index(key) + len(key):]
            return re.split(r"신축성|비침|두께", tail)[0]
    return ""


def _attributes(lines: list[str]) -> list[tuple[str, str]]:
    """(이어 쓰는 말, 끝맺는 말) 쌍의 목록"""
    out: list[tuple[str, str]] = []
    s = _segment(lines, "신축성")
    if s.strip():
        if "없" in s:
            out.append(("신축성은 없고", "신축성은 없어요"))
        elif "약간" in s or "조금" in s:
            out.append(("신축성은 약간 있고", "신축성은 약간 있어요"))
        elif "좋" in s:
            out.append(("신축성이 좋고", "신축성이 좋아요"))
        elif "있" in s:
            out.append(("신축성이 있고", "신축성이 있어요"))
    s = _segment(lines, "비침")
    if s.strip():
        if "없" in s:
            out.append(("비침은 없고", "비침은 없어요"))
        elif any(w in s for w in ("약간", "조금", "살짝")):
            out.append(("비침은 약간 있고", "비침은 약간 있어요"))
        elif "있" in s or "많" in s:
            out.append(("비침이 있고", "비침이 있어요"))
    s = _segment(lines, "두께")
    if s.strip():
        if "얇" in s:
            out.append(("두께는 얇은 편이고", "두께는 얇은 편이에요"))
        elif "두꺼" in s or "도톰" in s:
            out.append(("도톰한 편이고", "도톰한 편이에요"))
        elif any(w in s for w in ("보통", "중간", "적당")):
            out.append(("두께는 보통이고", "두께는 보통이에요"))
    return out


def attribute_sentence(lines: list[str]) -> str:
    pairs = _attributes(lines)
    if not pairs:
        return ""
    return ", ".join([a for a, _ in pairs[:-1]] + [pairs[-1][1]]) + "."


def main_colors(lines: list[str]) -> tuple[list[str], str]:
    """옷 본체의 색(적힌 순서대로 최대 MAX_COLORS개)과 무늬. 단추·안감 같은 장식 색 줄은 뺀다"""
    colors: list[str] = []
    pattern = ""
    for line in lines:
        for key, name in PATTERNS:
            if key in line and not pattern and not re.search(r"단추|안감|로고", line):
                pattern = name
        if ACCENT.search(line):
            continue
        rest = line
        found: list[tuple[int, str]] = []
        for key, name in COLORS:   # 긴 말을 먼저 찾고 찾은 자리는 지워서 '스카이 블루'가 '블루'로 또 잡히지 않게 한다
            at = rest.find(key)
            if at >= 0:
                found.append((at, name))
                rest = rest[:at] + " " * len(key) + rest[at + len(key):]
        for _, name in sorted(found):
            if name not in colors:
                colors.append(name)
    return colors[:MAX_COLORS], pattern


def product_name(kind_code: str, attrs: dict[str, list[str]]) -> str:
    """"하늘색 블라우스", "아이보리 플로럴 원피스" — 첫 번째 색 + 무늬 + 세부 종류. 없는 부분은 뺀다"""
    subtype = garment_noun(kind_code, attrs)
    colors, pattern = main_colors(attrs.get("C", []))
    if "청바지" in subtype:
        return plain_korean(subtype)
    if is_denim(attrs):
        # 파란 데님은 색 대신 "데님"("데님 셔츠"), 다른 색은 "그레이 데님 바지"
        if colors and colors[0] in BLUE_DENIM:
            colors = []
        if "데님" not in subtype and not subtype.startswith("청"):
            words_ = subtype.split()
            subtype = " ".join(words_[:-1] + ["데님", words_[-1]])
    words = []
    for word in (colors[:1] + [pattern.removesuffix("무늬").strip()]):
        if word and word not in subtype.replace(" ", "") and word not in subtype:
            words.append(word)
    return plain_korean(" ".join(words + [subtype]))


def option_colors(attrs: dict[str, list[str]]) -> list[str]:
    """옵션에서 고를 색: 사진 속 옷 한 벌의 대표 색(이름에 쓰는 첫 번째 색) 하나. 색 정보가 없으면 빈 목록"""
    colors, _ = main_colors(attrs.get("C", []))
    return colors[:1]


def color_sentence(lines: list[str]) -> str:
    colors, pattern = main_colors(lines)
    solid = any("단색" in line and not ACCENT.search(line) for line in lines)
    if colors and pattern:
        return f"색은 {'·'.join(colors)}에 {ieyo(pattern)}."
    if colors:
        tail = " 단색" if solid and len(colors) == 1 else ""
        return f"색상은 {ieyo('·'.join(colors) + tail)}."
    return ""


def mood_sentence(lines: list[str]) -> str:
    moods: list[str] = []
    occasion = ""
    for line in lines:
        for mood in MOODS:
            if mood in line.split() and mood not in moods:
                moods.append(mood)
        if not occasion:
            for key, name in OCCASIONS:
                if key in line and "룩" in line:
                    occasion = name
                    break
    moods = moods[:2]
    if moods and occasion:
        return f"{', '.join(moods)} 분위기라 {occasion}으로 입기 좋아요."
    if moods:
        return f"{', '.join(moods)} 분위기예요."
    if occasion:
        return f"{occasion}으로 입기 좋아요."
    return ""


def _dedupe_words(line: str) -> str:
    """"덮는 덮는 길이"처럼 바로 이어 반복된 낱말은 하나만"""
    out: list[str] = []
    for word in line.split():
        if not out or out[-1] != word:
            out.append(word)
    return " ".join(out)


def describe(kind_code: str, attrs: dict[str, list[str]]) -> str:
    """kind_code(BL 등)와 {'F': [...], 'M': [...], 'C': [...], 'E': [...]} → 설명문"""
    name = KINDS[kind_code][0]
    f_lines = attrs.get("F", [])
    subtype = pick_subtype(f_lines, name)
    sentences = [ieyo(garment_noun(kind_code, attrs)) + "."]
    f_lines = [_dedupe_words(x) for x in f_lines]
    features = [x for x in pick_features(f_lines) if x != subtype]
    if features:
        sentences.append(f"{i_ga(', '.join(features))} 특징이에요.")
    for part in (material_sentence(attrs.get("M", [])), attribute_sentence(attrs.get("M", [])),
                 color_sentence(attrs.get("C", [])), mood_sentence(attrs.get("E", []))):
        if part:
            sentences.append(part)
    return plain_korean(" ".join(sentences))


def load_items(path: str) -> "OrderedDict[str, dict]":
    items: "OrderedDict[str, dict]" = OrderedDict()
    with open(path, encoding="utf-8") as f:
        for line in f:
            cols = line.rstrip("\n").split("\t")
            if len(cols) != 5:
                continue
            item_id, _, kind, ftype, text = cols
            if kind not in KINDS:
                continue
            entry = items.setdefault(item_id, {"kind": kind, "attrs": {}})
            entry["attrs"].setdefault(ftype, []).append(text)
    return items


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("사용법: python tools/etri_describe.py <정리한 TSV> <저장할 TSV>", file=sys.stderr)
        return 2
    items = load_items(argv[1])
    with open(argv[2], "w", encoding="utf-8", newline="") as out:
        out.write("product_id\tkind\tclothing_type\tdescription\n")
        for item_id, item in items.items():
            kind = item["kind"]
            out.write("\t".join((item_id, kind, KINDS[kind][1], describe(kind, item["attrs"]))) + "\n")
    print(f"설명문 {len(items)}개를 저장했습니다: {argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
