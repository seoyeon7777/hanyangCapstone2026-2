"""ETRI 패션 코디 데이터셋 속성 문장의 띄어쓰기를 정리한다.

원문은 형태소 단위로 띄어 쓴 글이다: "신축성 없 음", "손목 까지 내려오 는 일자형 소매", "깔끔_한".
앞말에 붙는 조사·어미만 골라 붙이고, "_"는 원래 한 낱말이던 자리라 이어 붙인다.

    python tools/etri_spacing.py "<데이터 폴더>/mdata.wst.txt.2020.6.23" "<저장할 파일>.tsv"

원문과 결과 파일은 라이선스(ETRI) 때문에 저장소에 넣지 않는다.
"""
import re
import sys

# 앞말에 붙는 조사
JOSA = {
    "의", "이", "가", "은", "는", "을", "를", "에", "에서", "에게", "로", "으로", "과", "와",
    # "이랑"은 이 데이터에서 조사가 아니라 골 이랑(짜임)이라 넣지 않는다
    "도", "만", "까지", "부터", "처럼", "보다", "이나", "나", "랑", "마다", "밖에", "조차",
    "에는", "에도", "에만", "로는", "로도", "으로는", "으로도", "으로만", "로만", "에서는", "에서도",
    "까지는", "까지도", "와는", "과는", "들", "씩", "인데", "이며", "이고", "이라", "라", "라서",
}

# 앞말(어간·어근)에 붙는 어미·접미사
EOMI = {
    # "줌"은 "느낌을 줌"처럼 따로 쓰는 말이라 붙이지 않는다
    "음", "함", "임", "짐", "됨", "다", "고", "게", "기", "며", "면서", "지만", "거나", "도록",
    "한", "된", "인", "운", "던", "을수록", "수록", "지", "질", "질수록", "아", "어", "여", "아서",
    "어서", "여서", "해", "해서", "하", "히", "되", "돼", "적", "적인", "적이", "형", "스러운",
    "스럽", "스러움", "스런", "스러워", "롭", "로운", "답", "다운", "으며", "으면", "려는", "려고",
    "ㄴ", "ㄹ", "았", "었", "였", "겠", "습니다", "ㅂ니다", "은데", "는데", "더라도", "러", "으러",
    "해도", "하게", "하고", "하여", "하며", "하기", "한다", "합니다", "해요", "해져", "해지",
}

# 이 조각들로 시작하면 앞말에 붙인다 ("하_고", "스럽_게", "되_어" 같이 "_"로 이어진 어미)
PREFIX_ATTACH = ("하_", "스럽_", "스러_", "되_", "적_", "지_", "로_운", "해_", "해지", "해져")

# "가 기"(가다), "가 늘_어"(가늘다)처럼 조사처럼 보이지만 다음 낱말의 시작인 경우
VERB_START_NEXT = {
    "가": ("기", "늘", "벼", "볍", "깝", "까", "깔"),
    "나": ("서", "가", "타", "눠", "누"),
}

# 조각이 이상하게 나뉜 말은 붙인 뒤에 한 번 더 고친다
PHRASE_FIX = (("라 인형", "라인형"),)

_SPACE = re.compile(r"\s+")
_HANGUL = re.compile(r"[가-힣]")


def _starts_next_word(token: str, nxt: str) -> bool:
    """token이 앞말의 조사가 아니라 다음 조각과 합쳐지는 낱말의 앞부분이면 True"""
    heads = VERB_START_NEXT.get(token)
    return bool(heads) and nxt.startswith(heads)


def fix_spacing(text: str) -> str:
    """띄어 쓴 형태소 문장 한 줄을 자연스러운 띄어쓰기로 바꾼다."""
    tokens = text.split()
    out: list[str] = []
    i = 0
    while i < len(tokens):
        tok = tokens[i]
        nxt = tokens[i + 1] if i + 1 < len(tokens) else ""
        prev = out[-1] if out else ""

        if prev and _starts_next_word(tok, nxt):
            # "구김 이 가 기" → "구김이 가기", "더 가 늘_어" → "더 가늘어"
            out.append(tok + nxt)
            i += 2
            continue

        attach = False
        if prev and _HANGUL.search(prev[-1:] or ""):
            attach = tok in JOSA or tok in EOMI or tok.startswith(PREFIX_ATTACH)
        elif prev and tok in JOSA:
            # 영문·숫자 뒤의 조사: "A 라인 의", "3CM 의", "RIB 로"
            attach = True

        # "손을 다 덮는"(모두), "원단으로 된"은 따로 쓴다
        if (tok == "다" and nxt.startswith(("덮", "가리", "가려", "커버"))) or (tok == "된" and prev.endswith("로")):
            attach = False

        # "여리 여리" 같은 같은 말 반복은 한 낱말로
        if prev and len(tok) == 2 and tok == prev:
            attach = True

        if attach:
            out[-1] = prev + tok
        else:
            out.append(tok)
        i += 1

    line = " ".join(out).replace("_", "")
    for wrong, right in PHRASE_FIX:
        line = line.replace(wrong, right)
    return _SPACE.sub(" ", line).strip()


def read_rows(path: str):
    """원본(CP949, 탭 5칸)을 읽어 (id, 대분류, 종류, 항목 유형, 문장)을 돌려준다."""
    with open(path, encoding="cp949") as f:
        for line in f:
            cols = [c.strip() for c in line.rstrip("\r\n").split("\t")]
            if len(cols) == 5:
                yield tuple(cols)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("사용법: python tools/etri_spacing.py <원본 파일> <저장할 파일>", file=sys.stderr)
        return 2
    count = 0
    with open(argv[2], "w", encoding="utf-8", newline="") as out:
        for item_id, group, kind, ftype, text in read_rows(argv[1]):
            out.write("\t".join((item_id, group, kind, ftype, fix_spacing(text))) + "\n")
            count += 1
    print(f"{count}줄을 정리해 저장했습니다: {argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
