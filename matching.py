"""B. 옷 매칭 - 태그 붙이기, 체형 적합도, 추천 이유, 코디 점수.

입력  : A의 체형 결과(profile), 상품(item)
출력  : C가 받는 dict (match_item)
규칙  : 가중치·키워드·문장·설정은 data/*.json 에서 읽는다. 코드에 숫자를 두지 않는다.

profile = {"ratios": {"S": .54, "W": .17, "N": .29}, "primary": "S", "secondary": "N", "confidence": .25, "is_mixed": False}  # A의 classify() 출력
item    = {"item_id": 1, "name": "...", "category": "top", "season": "FW", "popularity": .6, "tags": [...]}
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

TYPES = ("S", "W", "N")
DATA_DIR = Path(__file__).parent / "data"


# ---------------------------------------------------------------- 체형 입력 (A의 classify 출력)

def normalize_profile(profile: dict) -> dict:
    """A(체형 분류)의 출력을 검증하고 다듬는다. 이 모듈의 공개 함수는 모두 이걸 거친다.

    입력: {"ratios": {"S":..,"W":..,"N":..}, "primary": "S", "secondary": "N", "confidence": .25, "is_mixed": False}
    - ratios 합이 1이 아니면(반올림 오차 등) 1로 맞춘다.
    - ratios·primary·is_mixed 가 없거나, 비율이 음수·합 0이면 ValueError.
    - 예전 이름(p / main / mixed)도 받지만 내부에서는 새 이름만 쓴다. secondary·confidence·source 는 계산에 쓰지 않는다.
    """
    ratios = profile.get("ratios", profile.get("p"))
    primary = profile.get("primary", profile.get("main"))
    mixed = profile.get("is_mixed", profile.get("mixed"))
    if not isinstance(ratios, dict) or any(t not in ratios for t in TYPES):
        raise ValueError('profile: "ratios"에 S, W, N 세 값이 모두 필요하다')
    vals = {t: ratios[t] for t in TYPES}
    if any(isinstance(v, bool) or not isinstance(v, (int, float)) or v < 0 for v in vals.values()):
        raise ValueError("profile: 비율은 0 이상의 숫자여야 한다")
    total = sum(vals.values())
    if total <= 0:
        raise ValueError("profile: 비율의 합이 0이다")
    if primary not in TYPES:
        raise ValueError('profile: "primary"는 S, W, N 중 하나여야 한다')
    if not isinstance(mixed, bool):
        raise ValueError('profile: "is_mixed"는 true/false여야 한다')
    return {"ratios": {t: v / total for t, v in vals.items()}, "primary": primary,
            "secondary": profile.get("secondary"), "is_mixed": mixed}


# ---------------------------------------------------------------- 규칙 로드

@dataclass(frozen=True)
class Rules:
    weights: dict   # tag -> {"S": int, "W": int, "N": int}
    keywords: dict  # keyword -> [tag, ...]
    reasons: dict   # tag -> {type: 문장}
    config: dict
    w_min: float    # 가중치 행렬의 최솟값 (BodyFit 정규화에 사용)
    w_max: float    # 가중치 행렬의 최댓값
    balance: dict = field(default_factory=dict)  # 코디 밸런스 규칙 (balance.json)
    materials: dict = field(default_factory=dict)  # 혼용률 읽기 규칙 (materials.json) - 섬유 -> {words, tag}
    attributes: dict = field(default_factory=dict)  # 판매자 등록 양식 정의 (attributes.json)


def _read(path: Path) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_rules(data_dir: Path | str = DATA_DIR, weights_path: Path | str | None = None) -> Rules:
    """data_dir의 규칙 파일을 읽는다. 테스트에서는 weights_path로 가중치만 바꿔 끼운다."""
    data_dir = Path(data_dir)
    raw = _read(Path(weights_path) if weights_path else data_dir / "tag_weight.json")["tags"]
    weights = {tag: {t: row[t] for t in TYPES} for tag, row in raw.items()}
    cells = [v for row in weights.values() for v in row.values()]
    return Rules(
        weights=weights,
        keywords=_read(data_dir / "keywords.json")["keywords"],
        reasons=_read(data_dir / "reasons.json")["reasons"],
        config=_read(data_dir / "config.json"),
        w_min=min(cells),
        w_max=max(cells),
        balance=_read(data_dir / "balance.json") if (data_dir / "balance.json").exists() else {},
        materials=_read(data_dir / "materials.json")["fibers"] if (data_dir / "materials.json").exists() else {},
        attributes=_read(data_dir / "attributes.json") if (data_dir / "attributes.json").exists() else {},
    )


def validate_rules(rules: Rules) -> list[str]:
    """규칙 파일을 바꾼 뒤 돌려 보는 점검. 문제 목록을 돌려주고, 없으면 빈 리스트."""
    problems = []
    thr = rules.config["avoid_threshold"]
    if rules.config["avoid_mode"] == "exclude" and rules.w_min > thr:
        problems.append(f"avoid_threshold({thr}) 이하인 칸이 없어 AVOID 제외가 동작하지 않음")
    balance_tags = {
        g
        for rule_list in rules.balance.get("rules", {}).values()
        for rule in rule_list
        for key in ("top_any", "bottom_any", "either_any")
        for g in rule.get(key, [])
    }
    emitted = {g for tags in rules.keywords.values() for g in tags}
    for kw, tags in rules.keywords.items():
        for g in tags:
            if g not in rules.weights and g not in balance_tags:
                problems.append(f"keywords: '{kw}' -> 가중치표·밸런스 규칙에 없는 태그 {g}")
    for g in sorted(balance_tags - emitted):
        problems.append(f"balance: 태그 {g}를 붙이는 키워드가 없어 이 규칙은 동작하지 않음")
    for fiber, spec in rules.materials.items():
        if spec.get("tag") and spec["tag"] not in rules.weights:
            problems.append(f"materials: {fiber}의 태그 {spec['tag']}가 가중치표에 없음")
    cats = rules.attributes.get("categories", [])
    for f in rules.attributes.get("fields", []):
        for c in f.get("applies", []):
            if c not in cats:
                problems.append(f"attributes: {f['key']}의 카테고리 {c}가 categories에 없음")
        for label, g in f.get("options", {}).items():
            if g and g not in rules.weights and g not in balance_tags:
                problems.append(f"attributes: {f['key']}/{label} -> 가중치표·밸런스 규칙에 없는 태그 {g}")
        for label, allowed in f.get("option_applies", {}).items():
            if label not in f.get("options", {}):
                problems.append(f"attributes: {f['key']}의 option_applies에 없는 선택지 {label}")
            for c in allowed:
                if c not in f.get("applies", []):
                    problems.append(f"attributes: {f['key']}/{label}의 카테고리 {c}가 항목 applies에 없음")
        for fiber in f.get("fibers", []):
            if fiber not in rules.materials:
                problems.append(f"attributes: 혼용률 섬유 {fiber}가 materials.json에 없음")
    for t in rules.balance.get("rules", {}):
        if t not in TYPES:
            problems.append(f"balance: 유형 키 '{t}'가 S/W/N이 아님")
    for g, by_type in rules.reasons.items():
        if g not in rules.weights:
            problems.append(f"reasons: 가중치표에 없는 태그 {g}")
        for t in by_type:
            if t not in TYPES:
                problems.append(f"reasons: {g}의 유형 키 '{t}'가 S/W/N이 아님")
    return problems


# ---------------------------------------------------------------- 2) 태깅

_SKIP_SENT = re.compile(r"매치|코디|스타일링|어울|사이즈\s*[\d,\s]{4,}|세탁|건조|다림질|표백|울\s*코스|교환|반품|환불|제품의\s*경우(?=[^.\n]*(?:스크레치|스크래치|주름|반점|하자|불량))|(?:스크레치|스크래치|반점|하자)[^.\n]*제품의\s*경우|배송|(?:아우터|상의|하의|이너|액세서리|신발|가방)\s*\||손질|how to care|약품\s*냄새|털\s*날림|fit\s*guide|size\s*tip|데님류|데님의\s*경우|모티브|어우러|셋업|추천드리|빨래|울\s*샴푸|울샴프|세탁망|트림\s*(?:부분|원단)|(?:나일론|nylon)\s*(?:소재|원단)?\s*의?\s*(?:여밈|지퍼|스냅|단추)|\([A-Za-z]{2,}[A-Za-z0-9]{5,}\)")
_COTTON_KO = re.compile(r"(?<![가-힣])면(?=\s*\d+\s*%|\s*[/,·]\s*(?:폴리|레이온|나일론|스판|린넨|울|아크릴)|\s*(?:과|와)\s*(?:폴리|레이온|나일론|스판|린넨|울|아크릴)|\s*혼방)")  # 한 글자 '면'은 소재 표기 문맥에서만 코튼으로 본다
_NEGATED = re.compile(r"(?:슬림|오버핏|오버사이즈|오버|루즈|와이드|크롭|스키니)\S{0,3}\s*(?:하지\s*않|지\s*않|아닌|아니)")
_COLOR_DENIM = re.compile(r"\(\s*denim\s*\)|(?:색상|color)\s*[:：]?\s*denim|^\s*denim\s*$", re.M)  # 색상명 DENIM은 소재가 아님
_WIDE_NECK = re.compile(r"(?:와이드|넉넉한?)[^.\n]{0,8}(?:네크라인|넥라인|카라|칼라)")                             # '와이드한 폭의 보트 네크라인'은 바지 실루엣이 아님
_SENT_SPLIT = re.compile(r"[.\n■●▶]|(?<=다)\s|(?=composition|혼용률|material\s*[:：])")   # 마침표 없이 이어 붙은 설명에서 혼용률 문구가 앞 문장(코디 설명)과 같이 버려지지 않게 끊는다


_FIT_LEGEND = re.compile(r"(?:(?:슬림|기본|레귤러|세미\s*루즈|루즈|오버|와이드)\s*핏(?:\s*/\s*(?:오버)\s*핏)?[\s|/·,]*){3,}")   # 케어라벨 범례표 '슬림핏|기본핏|루즈핏/오버핏|와이드핏' (상품 설명이 아님)
_FIT_SCALE = re.compile(r"(?:슬림|타이트|레귤러|세미\s*루즈|루즈|오버)\s*(?:핏|fit)?\s*(?:적당함?|보통|정\s*사이즈|좋음|없음)\s*(?:슬림|타이트|레귤러|세미\s*루즈|루즈|오버)?\s*(?:핏|fit)?")   # 상세 이미지 속 착용감 척도 '슬림핏 적당함 루즈핏' (선택지일 뿐 선택값이 아님)
_STYLING_TAIL = re.compile(r"(?:[가-힣a-z]+\s*,\s*){0,3}[가-힣a-z]*\s*스타일링(?:에|으로)?\s*(?:활용|사용)?\s*(?:가능|하기\s*좋)")   # '...루즈핏 실루엣 드롭 숄더 데일리, 빈티지, 스트릿 스타일링에 활용 가능'처럼 마침표 없이 이어진 문장에서 뒤쪽 코디 문구 때문에 앞쪽 핏 설명까지 버려지는 것 방지
_CARE_START = re.compile(r"washing\s*(?:tips?|info(?:rmation)?|guide)|care\s*guide|세탁\s*방법\s*및\s*주의|세탁\s*및\s*취급|취급\s*시?\s*주의|how\s*to\s*care|care\s*instruction", re.I)   # 여기서부터는 소재별 세탁 안내표 ('LEATHER 가죽 ...'가 소재로 잡힘)
_CARE_MATERIAL = re.compile(r"^\s*(?:denim|leather|suede|fur|wool|knit|cotton|silk|linen)\s*\n\s*(?:데님|가죽|스웨이드|퍼|울|니트|면|실크|린넨)[^\n]*", re.M)   # 케어 안내표의 소재 제목 ('LEATHER / 가죽 ...')
_BODY_SLIM = re.compile(r"(?:허리|골반|밑단|허벅지|종아리|팔뚝|다리)\s*(?:은|는|이|가)\s*슬림")   # '허리는 슬림하게' 신체 효과 설명, 핏 표기가 아님
_TRIM_LEATHER = re.compile(r"[^.\n]{0,30}?(?:가죽|레더|leather)\s*(?:패치|라벨|탭|태그)")                                   # '소가죽을 사용한 가죽패치' 같은 장식 부위 설명


_GARMENT = r"(?:스커트|슬랙스|팬츠|바지|데님|청바지|치마|코트|자켓|재킷|니트|티셔츠|셔츠|가디건|원피스)"
_COORD_FROM = re.compile(rf"[가-힣a-z]*{_GARMENT}\s*부터[^.\n]{{0,40}}")   # '스커트부터 슬랙스, 데님'처럼 함께 입을 다른 옷을 나열한 문구 (이 상품의 소재가 아님)
_COORD_ANY = re.compile(rf"(?:[가-힣a-z]*{_GARMENT}\s*[,·/]?\s*)+(?:어디에나|어디든|(?:와|과|랑)\s*함께|에\s*매치)")   # '와이드팬츠,스커트 어디에나' (코디 문구가 실루엣으로 잡힘)
_PART_LOOSE = re.compile(r"(?:팔뚝|소매|소맷단|암홀|허벅지|종아리|밑단|엉덩이|골반|힙|뱃살)\s*(?:은|는|이|가|도|에|까지)?\s*(?:넉넉|널널|여유|루즈|오버|헐렁)[가-힣]*(?:\s*핏)?"
                         r"|(?:후드|하이넥|넥라인|목|카라|칼라|모자)\s*(?:은|는|이|가|도)?\s*(?:넉넉|널널|여유)[가-힣]*"
                         r"|(?:넉넉|널널|여유|헐렁)[가-힣]{0,3}\s*(?:후드(?!\s*(?:집업|티|티셔츠|맨투맨|자켓|재킷|점퍼|가디건|코트|원피스|니트|스웨터|조끼|베스트|셔츠|패딩|요크))|하이넥|넥라인|카라|칼라|소매|소맷단|팔뚝|암홀|허벅지|종아리|밑단)")   # '넉넉한 후드, 하이넥과 밑단'(앞에서 꾸미는 형태). '넉넉한 후드집업'은 옷 전체라 남긴다. # '팔뚝널널핏', '넉넉한 후드'처럼 일부 부위만 여유 있다는 설명 (옷 전체의 오버핏이 아님)
_LOOKS_SLIM = re.compile(r"슬림\s*(?:해|하게|한)?\s*(?:보이|보여|보임|연출|효과)")   # '슬림해 보이는 핏'은 보이는 효과이지 슬림핏 표기가 아니다
_LABELED_COMP = "(?:소재|재질|혼용률|혼용율|material|fabric)"   # '소재 면 100'처럼 % 없이 라벨 뒤에 적힌 혼용률


_REGULAR_FIT = re.compile(r"(?:레귤러|regular|스탠다드|standard)\s*-?\s*(?:핏|fit)(?!\s*(?:이|은|가)?\s*(?:아닌|아니|않))|기본\s*핏|정\s*핏")   # 판매자가 '레귤러'라고 밝힌 경우 (점수 태그는 만들지 않는다)
_FIT_FROM_OPTION = {"슬림": "slim", "레귤러": "regular", "오버(루즈)": "over"}


def fit_stated_from_tags(tags: list[str], regular: bool = False) -> str:
    """핏 표기 상태: slim / regular / over / unknown. 점수 태그와 별개로 저장만 한다.
    하나만 읽히면 그 값, 서로 다른 둘 이상이 읽히면(모순) 또는 아무것도 없으면 unknown."""
    found = [v for v, on in (("slim", "fit:slim" in tags), ("over", "fit:over" in tags), ("regular", regular or "fit:regular" in tags)) if on]
    return found[0] if len(found) == 1 else "unknown"


def _clean_desc(desc: str) -> str:
    """설명글에서 상품 자체가 아닌 문장(함께 입는 옷 추천, 사이즈 숫자 나열)을 뺀다.
    예: '니트나 후드와 매치해...'(→ 니트 오탐), '사이즈 064,067 WIDE'(→ 와이드 오탐)."""
    if not desc:
        return ""
    desc = _STYLING_TAIL.sub(" . ", desc)
    desc = _COORD_ANY.sub(" ", _COORD_FROM.sub(" . ", desc))
    m = _CARE_START.search(desc)
    if m:
        desc = desc[:m.start()]
    desc = _CARE_MATERIAL.sub(" ", desc)
    desc = _BODY_SLIM.sub(" ", _FIT_SCALE.sub(" ", desc))
    desc = _TRIM_LEATHER.sub(" ", _FIT_LEGEND.sub(" ", desc))   # 줄바꿈으로 끊기 전에 처리한다 (범례가 줄마다 나뉘어 나오는 경우가 있다)
    return " ".join(p for p in _SENT_SPLIT.split(desc) if p and not _SKIP_SENT.search(p))


def _fiber_regex(rules: Rules):
    words = sorted({(w.lower(), f) for f, spec in rules.materials.items() for w in spec["words"]}, key=lambda x: -len(x[0]))
    to_fiber = dict(words)
    alt = "|".join(("(?<![가-힣])" if len(w) == 1 else "") + re.escape(w) for w, _ in words)
    # 한 글자 섬유명(면·모·마)이 '표면 100%'처럼 다른 단어 안에서 잡히지 않도록 앞이 한글이면 건너뛴다
    a = re.compile(rf"({alt})\s*[:：]?\s*(\d{{1,3}})(?:\.\d+)?\s*%")   # '면 60%'
    b = re.compile(rf"(\d{{1,3}})(?:\.\d+)?\s*%\s*({alt})")                       # '60% 면'
    return a, b, to_fiber


_PART_MAIN = re.compile(r"겉감|몸판|본판|주원단|본체|shell|outer\s*fabric\s*\d*|main\s*fabric")
_PART_SUB = re.compile(r"안감|lining|배색\s*\d*|contrast\s*\d*|충전재|filling|심지|트림|trim")


def parse_composition(text: str, rules: Rules) -> list[dict]:
    """'면 60% 폴리에스터 40%' 같은 혼용률 문구에서 [{"fiber": "cotton", "share": 0.6, "part": "main"}, ...]을 읽는다.
    part: 겉감·몸판 같은 주원단 표기 뒤에 나오면 "main", 안감·배색·충전재 뒤면 "sub", 표기가 없으면 "unlabeled".
    같은 섬유가 같은 part에서 여러 번 나오면 가장 큰 값을 쓴다. 혼용률이 없으면 빈 리스트."""
    if not rules.materials or not text:
        return []
    a, b, to_fiber = _fiber_regex(rules)
    text = text.lower()
    fa = [(m.group(1), m.group(2), m.start()) for m in a.finditer(text)]    # '면 60% 폴리 40%' 형식
    fb = [(m.group(2), m.group(1), m.start()) for m in b.finditer(text)]    # '60% 면 40% 폴리' 형식 (두 형식 중 더 많이 잡히는 쪽을 쓴다)
    if len(fa) != len(fb):
        found = fa if len(fa) > len(fb) else fb          # 형식이 맞는 쪽이 더 많이 잡힌다 (안감·배색까지 있으면 합이 100%를 넘으므로 합으로는 가리지 못한다)
    else:
        found = fa if abs(sum(int(p) for _, p, _ in fa) - 100) <= abs(sum(int(p) for _, p, _ in fb) - 100) else fb
    if not found:       # '소재 면 100 색상'처럼 % 없이 라벨 뒤에 적힌 경우: 합이 100일 때만 받는다
        alt = "|".join(("(?<![가-힣])" if len(w) == 1 else "") + re.escape(w) for w in sorted(to_fiber, key=lambda w: -len(w)))
        lab = re.search(rf"{_LABELED_COMP}\s*[:：]?\s*((?:(?:{alt})\s*\d{{1,3}}\s*[/,·+]?\s*){{1,4}})(?=\s*(?:색상|사이즈|컬러|color|size|세탁|제조|원산지|$))", text)
        if lab:
            pairs = [(m.group(1), m.group(2), lab.start()) for m in re.finditer(rf"({alt})\s*(\d{{1,3}})", lab.group(1))]
            if pairs and abs(sum(int(p) for _, p, _ in pairs) - 100) <= 1:
                found = pairs
    labels = [(m.end(), "main") for m in _PART_MAIN.finditer(text)] + [(m.end(), "sub") for m in _PART_SUB.finditer(text)]
    best: dict[tuple[str, str], float] = {}
    for word, pct, pos in found:
        share = int(pct) / 100
        if share > 1:
            continue
        near = [(end, kind) for end, kind in labels if end <= pos and pos - end <= 40]
        part = max(near)[1] if near else "unlabeled"      # 가장 가까운 앞쪽 표기를 따른다
        key = (to_fiber[word], part)
        best[key] = max(best.get(key, 0.0), share)
    return [{"fiber": f, "share": s, "part": p} for (f, p), s in best.items()]


def _fiber_tags(materials: list[dict], rules: Rules) -> tuple[list[str], str]:
    """읽은 혼용률에서 섬유 태그를 고른다. 점수에는 겉감(주원단)만 쓴다. 겉감 표기가 있으면 그것만, 없으면 안감·배색 표기가 아닌 값만 쓴다.
    안감·배색 값만 있어 겉감을 알 수 없으면 태그를 붙이지 않고 basis="unclear"로 기록한다. material_min_share 미만인 섬유는 태그를 붙이지 않는다."""
    min_share = rules.config.get("material_min_share", 0.0)
    main = [m for m in materials if m["part"] == "main"] or [m for m in materials if m["part"] == "unlabeled"]
    basis = "main" if any(m["part"] == "main" for m in materials) else ("unlabeled" if main else "unclear")
    out = []
    for m in main:
        g = rules.materials[m["fiber"]].get("tag")
        if g and m["share"] >= min_share and g not in out:
            out.append(g)
    return out, basis


def percent_to_share(percent_list: list[dict]) -> list[dict]:
    """판매자 화면의 퍼센트 입력([{"fiber": "cotton", "percent": 60}, ...], 0 초과 100 이하)을 내부 비율(0~1)로 바꾼다.
    범위·합계 검증은 tag_from_attributes가 한다(서버에서 다시 확인)."""
    out = []
    for m in percent_list or []:
        p = m.get("percent")
        if not isinstance(p, (int, float)) or isinstance(p, bool) or not 0 < p <= 100:
            raise ValueError(f"attrs: {m.get('fiber')}의 퍼센트는 0 초과 100 이하여야 한다 (받은 값: {p!r})")
        out.append({"fiber": m.get("fiber"), "share": p / 100})
    return out


def tag_from_attributes(attrs: dict, rules: Rules) -> dict:
    """판매자 등록 양식(attributes.json)의 입력값 검증 + 태그 생성. 직접 등록 상품 전용이다.
    attrs 예: {"category": "top", "fit": "슬림", "length_top": "크롭", "composition": [{"fiber": "cotton", "share": 0.6}, {"fiber": "polyester", "share": 0.4}]}
    - 필수 항목이 비어 있거나, 그 카테고리에 없는 항목·선택지를 쓰거나, 혼용률이 잘못되면 ValueError.
    - 혼용률은 섬유별 0 초과 1 이하, 같은 섬유 중복 불가, 합계 1 +- composition_tolerance. 수치를 모르면 composition_status("확인 불가"|"해당 없음").
    - 반환: {"tags", "materials", "material_basis", "attributes"}. attributes는 검증을 통과한 입력값 전체로, 레귤러처럼 태그가 없는 값도 그대로 저장한다."""
    form = rules.attributes
    if not form:
        raise ValueError("attributes.json이 없다")
    cat = attrs.get("category")
    if cat not in form["categories"]:
        raise ValueError(f'attrs: category는 {form["categories"]} 중 하나여야 한다 (받은 값: {cat!r})')
    tags: list[str] = []
    stored: dict = {"category": cat}
    missing: list[str] = []
    for f in form["fields"]:
        val = attrs.get(f["key"])
        has = val not in (None, "", [])
        if f.get("type") == "composition":
            continue
        if cat not in f["applies"]:
            if has:
                raise ValueError(f'attrs: "{f["label"]}"은 {cat} 카테고리에 없는 항목이다')
            continue
        if not has:
            if f.get("required"):
                missing.append(f["label"])
            continue
        if isinstance(val, list) and "해당 없음" in val and len(val) > 1:
            raise ValueError(f'attrs: "{f["label"]}"에서 "해당 없음"은 다른 선택지와 함께 고를 수 없다 (모순)')
        for choice in (val if isinstance(val, list) else [val]):
            if choice not in f["options"]:
                raise ValueError(f'attrs: "{f["label"]}"에 없는 선택지 {choice!r} (가능: {list(f["options"])})')
            allowed = f.get("option_applies", {}).get(choice)
            if allowed and cat not in allowed:
                raise ValueError(f'attrs: "{choice}"는 {cat} 카테고리에서 고를 수 없다 (가능: {allowed})')
            for other, bad in f.get("option_conflicts", {}).get(choice, {}).items():
                if attrs.get(other) in bad:
                    raise ValueError(f'attrs: "{choice}"와 {other}={attrs.get(other)!r}는 함께 고를 수 없다 (모순)')
            g = f["options"][choice]
            if g and g not in tags:
                tags.append(g)
        stored[f["key"]] = val
    # 혼용률: 수치 또는 상태(확인 불가·해당 없음) 중 하나는 반드시 있어야 한다
    comp, status = attrs.get("composition") or [], attrs.get("composition_status")
    if comp and status:
        raise ValueError("attrs: composition과 composition_status를 함께 쓸 수 없다")
    if status and status not in form["composition_statuses"]:
        raise ValueError(f'attrs: composition_status는 {form["composition_statuses"]} 중 하나여야 한다')
    if not comp and not status:
        missing.append("혼용률(수치 또는 확인 불가/해당 없음)")
    if missing:
        raise ValueError("attrs: 필수 항목이 비어 있다: " + ", ".join(missing))
    materials: list[dict] = []
    if comp:
        seen = set()
        for m in comp:
            fiber, share = m.get("fiber"), m.get("share")
            if fiber not in rules.materials:
                raise ValueError(f"attrs: 알 수 없는 섬유 {fiber!r}")
            if fiber in seen:
                raise ValueError(f"attrs: 섬유 {fiber!r}가 중복됐다")
            if not isinstance(share, (int, float)) or isinstance(share, bool) or not 0 < share <= 1:
                raise ValueError(f"attrs: {fiber}의 비율은 0 초과 1 이하여야 한다 (받은 값: {share!r})")
            seen.add(fiber)
            materials.append({"fiber": fiber, "share": float(share), "part": "main"})
        total = sum(m["share"] for m in materials)
        if abs(total - 1) > form["composition_tolerance"]:
            raise ValueError(f"attrs: 혼용률 합계가 {total:.2f}이다 (1 +- {form['composition_tolerance']} 이어야 한다)")
        stored["composition"] = comp
    else:
        stored["composition_status"] = status
    fiber_added, basis = _fiber_tags(materials, rules) if materials else ([], None)
    tags.extend(g for g in fiber_added if g not in tags)
    return {"tags": tags, "materials": materials, "material_basis": basis, "attributes": stored,
            "fit_stated": _FIT_FROM_OPTION.get(stored.get("fit"), "unknown")}


def tag_item_full(name: str, desc: str, rules: Rules) -> dict:
    """태그와 읽은 혼용률을 함께 돌려준다: {"tags": [...], "materials": [{"fiber","share","part"}], "material_basis": "main|unlabeled|unclear|None", "fit_stated": "slim|regular|over|unknown"}.
    혼용률이 읽힌 상품은 섬유 태그(면·울·나일론·실크·린넨·캐시미어)를 키워드가 아니라 혼용률로 정한다:
    material_min_share 이상인 섬유만 붙이고, 그보다 적게 든 섬유는 이름에 나와도 붙이지 않는다.
    혼용률이 없으면 예전처럼 키워드로 붙인다. 니트·데님·가죽·퍼·시폰·벨벳 등 섬유가 아닌 태그는 항상 키워드로 붙인다.
    fit_conflict: 슬림과 오버(루즈)가 함께 읽히면 True. 이때 두 핏 태그는 점수에서 빼고 fit_stated는 unknown으로 둔다(판단 보류).
    제외하는 문장: 함께 입는 다른 옷 나열('스커트부터 슬랙스, 데님'), 일부 부위만 여유 있다는 설명('팔뚝널널핏', '넉넉한 후드'),
    '슬림해 보이는' 같은 보이는 효과, 트림·여밈의 섬유, 세탁 안내 문장. '소재 면 100'처럼 %가 없어도 라벨 뒤에서 합이 100이면 혼용률로 읽는다."""
    desc = _COLOR_DENIM.sub(" ", (desc or "").lower())     # 줄 단위 패턴이라 줄바꿈이 사라지기 전에 처리한다
    text = f"{_COLOR_DENIM.sub(' ', name.lower())} {_clean_desc(desc)}"
    materials = parse_composition(text, rules)
    text = _COTTON_KO.sub(" 코튼 ", text)
    text = _NEGATED.sub(" ", text)   # "슬림하지 않은", "오버핏이 아닌"처럼 부정된 핏·실루엣 표현
    text = re.sub(r"세미\s*-?\s*오버", " ", text)   # 세미 오버핏은 오버핏보다 덜 헐렁하다 (판매자 핏 레귤러 상품에 '세미 오버핏' 표기가 있었다)
    for noise in ("겨울", "서울", "울산", "소울", "니트지", "제조일자"):   # '서울' 주소·'겨울'의 '울', 목 안쪽 니트지(트림)가 소재로 잡히는 것 방지
        text = text.replace(noise, " ")
    text = _WIDE_NECK.sub(" ", text)
    text = _LOOKS_SLIM.sub(" ", _PART_LOOSE.sub(" ", text))
    for deco in ("레더패치", "레더 패치", "가죽패치", "가죽 패치", "벨벳 테이프", "벨벳테이프"):   # 소재가 아니라 장식(패치·테이프)
        text = text.replace(deco, " ")
    tags: list[str] = []
    basis = None
    for kw, kw_tags in rules.keywords.items():
        if kw.lower() in text:
            tags.extend(g for g in kw_tags if g not in tags)
    if materials:
        fiber_tags = {spec["tag"] for spec in rules.materials.values() if spec.get("tag")}
        tags = [g for g in tags if g not in fiber_tags]
        fiber_added, basis = _fiber_tags(materials, rules)
        tags.extend(g for g in fiber_added if g not in tags)
    conflict = "fit:slim" in tags and "fit:over" in tags      # '슬림 루즈핏'처럼 서로 반대인 핏이 함께 읽히면 어느 쪽도 점수에 쓰지 않고 판단을 보류한다
    if conflict:
        tags = [g for g in tags if g not in ("fit:slim", "fit:over")]
    return {"tags": tags, "materials": materials, "material_basis": basis, "fit_conflict": conflict,
            "fit_stated": "unknown" if conflict else fit_stated_from_tags(tags, bool(_REGULAR_FIT.search(text)))}


def tag_item(name: str, desc: str, rules: Rules) -> list[str]:
    """상품명·설명에서 태그를 붙인다 (키워드 부분 문자열 일치 + 혼용률). 읽은 혼용률도 필요하면 tag_item_full."""
    return tag_item_full(name, desc, rules)["tags"]


# ---------------------------------------------------------------- 3) 체형 적합도

def _known(tags: list[str], rules: Rules) -> list[str]:
    """가중치표에 있는 태그만, 중복 없이. 표에 없는 태그는 점수·개수 계산에서 제외한다."""
    return [g for g in dict.fromkeys(tags) if g in rules.weights]


def is_excluded(tags: list[str], profile: dict, rules: Rules) -> bool:
    """AVOID 하드 필터: 단일형이고 1위 타입에서 임계값 이하인 태그가 있으면 제외."""
    profile = normalize_profile(profile)
    if rules.config["avoid_mode"] != "exclude" or profile["is_mixed"]:
        return False
    thr = rules.config["avoid_threshold"]
    return any(rules.weights[g][profile["primary"]] <= thr for g in _known(tags, rules))


def body_fit(tags: list[str], profile: dict, rules: Rules, reliability: float = 1.0) -> float | None:
    """0~1 적합도. 제외 대상이면 None.

    raw = Σ_태그 Σ_타입 P_t * W[태그][t] / 태그 수
    BodyFit = (raw - MIN) / (MAX - MIN)   # MIN/MAX는 가중치 행렬에서 계산
    태그가 적으면 한두 개 값이 점수를 좌우하므로, config의 shrink_k > 0이면 중립 쪽으로 당긴다:
    BodyFit = 중립 + (BodyFit - 중립) * n / (n + k)   (n = 가중치표에 있는 태그 수)
    reliability(0~1, review_agreement 참고)가 1보다 작으면 같은 방식으로 중립 쪽으로 더 당긴다: 중립 + (BodyFit - 중립) * reliability.
    config.review_mode가 positive_only면 중립보다 높은 점수만 당긴다(낮은 점수가 올라가 순위가 오르는 것을 막는다). 보정 여부는 match_item이 정한다.
    """
    profile = normalize_profile(profile)
    known = _known(tags, rules)
    if is_excluded(known, profile, rules):
        return None
    span = rules.w_max - rules.w_min
    neutral = (0.0 - rules.w_min) / span  # 모든 칸이 0일 때의 점수
    if not known:
        return min(1.0, max(0.0, neutral))  # 태그가 없으면 중립
    raw = sum(profile["ratios"][t] * rules.weights[g][t] for g in known for t in TYPES) / len(known)
    score = (raw - rules.w_min) / span
    k = rules.config.get("shrink_k", 0)
    if k > 0:
        n = len(known)
        score = neutral + (score - neutral) * n / (n + k)
    if reliability < 1.0 and not (rules.config.get("review_mode", "off") == "positive_only" and score <= neutral):
        score = neutral + (score - neutral) * reliability
    return min(1.0, max(0.0, score))


def review_agreement(review: dict | None, rules: Rules) -> float:
    """리뷰 응답 중 '등록 정보와 같다'고 답한 정도(0~1)를 낸다. 등록 오류를 확정하는 값이 아니다(구매 사이즈·체형에 따라 같은 상품도 다르게 느낄 수 있다).
    review = {"n": 답한 리뷰 수, "mismatch": 그중 다르다고 한 수}. 리뷰가 없으면 1.0(영향 없음).
    agreement = (n - mismatch + k0) / (n + k0), k0 = config.review_prior_k (리뷰가 적을 때 일치 표 k0개를 먼저 둔다. 리뷰가 없으면 1.0, 즉 등록 정보를 그대로 믿는 설계다).
    k0는 설계 가정이고 근거가 없다. 방향(작게 나온다/크게 나온다)은 쓰지 않고, 태그가 점수에 주는 영향만 줄인다."""
    if not review:
        return 1.0
    n, m = review.get("n"), review.get("mismatch")
    if not all(isinstance(x, int) and not isinstance(x, bool) for x in (n, m)) or n < 0 or not 0 <= m <= n:
        raise ValueError(f"review: n과 mismatch는 0 <= mismatch <= n인 정수여야 한다 (받은 값: {review!r})")
    k0 = rules.config.get("review_prior_k", 3)
    return (n - m + k0) / (n + k0)


# ---------------------------------------------------------------- 6) 추천 이유

def reasons(tags: list[str], profile: dict, rules: Rules) -> list[str]:
    """혼합 점수에 긍정적으로 기여한 태그의 문장을 reason_max개까지 (큰 순서).

    태그 기여 = Σ_유형 비율 x 가중치. 기여가 0 이하인 태그(한 유형에서만 좋고 합쳐서는 이득이 없는 태그 포함)는 설명하지 않는다.
    문장은 그 태그에서 비율 x 가중치가 가장 큰 유형의 문장을 쓴다(그 유형에 문장이 없으면 다음 유형).
    단일형·혼합형을 가리지 않고 같은 방식이라 is_mixed를 보지 않는다. 기여가 같으면 입력 순서를 유지한다.
    문장은 유형별 서술이라 '두 유형 모두 잘 맞는다' 같은 단정은 하지 않는다."""
    profile = normalize_profile(profile)
    known = _known(tags, rules)
    ratio = profile["ratios"]
    contrib = {g: sum(ratio[t] * rules.weights[g][t] for t in TYPES) for g in known}
    out: list[str] = []
    for g in sorted((g for g in known if contrib[g] > 0), key=lambda g: -contrib[g]):
        types = [t for t in TYPES if ratio[t] * rules.weights[g][t] > 0 and t in rules.reasons.get(g, {})]
        if not types:
            continue
        types.sort(key=lambda t: -ratio[t] * rules.weights[g][t])
        sentence = rules.reasons[g][types[0]]
        if sentence not in out:
            out.append(sentence)
        if len(out) >= rules.config["reason_max"]:
            break
    return out


# ---------------------------------------------------------------- 5) 코디

def _item_score(item: dict) -> float | None:
    """코디 계산에 쓸 상품 점수. C가 Final을 넘기면 "score", 아니면 body_fit을 쓴다."""
    s = item.get("score")
    return s if s is not None else item.get("body_fit")


def _rule_hits(rule: dict, top_tags: list[str], bottom_tags: list[str]) -> bool:
    keys = [k for k in ("top_any", "bottom_any", "either_any") if k in rule]
    if not keys:
        return False  # 조건이 없는 규칙은 적용하지 않는다
    top, bottom = set(top_tags), set(bottom_tags)
    if "top_any" in rule and not top & set(rule["top_any"]):
        return False
    if "bottom_any" in rule and not bottom & set(rule["bottom_any"]):
        return False
    if "either_any" in rule and not (top | bottom) & set(rule["either_any"]):
        return False
    return True


def _type_shares(profile: dict) -> dict[str, float]:
    """밸런스 규칙을 적용할 유형과 비중. 단일형은 1위만, 혼합형은 1·2위를 P 비율로(합 1)."""
    if not profile["is_mixed"]:
        return {profile["primary"]: 1.0}
    first, second = sorted(TYPES, key=lambda t: -profile["ratios"][t])[:2]
    total = profile["ratios"][first] + profile["ratios"][second]
    if total <= 0:
        return {first: 1.0}
    return {first: profile["ratios"][first] / total, second: profile["ratios"][second] / total}


def _balance_shares(profile: dict, rules: Rules) -> dict[str, float]:
    """코디 밸런스 규칙을 적용할 유형과 비중. config.balance_scope로 고른다.

    - "flag"(기본): _type_shares와 같다. 단일형은 1위만 100%, 혼합형은 1·2위만 비율대로(3위는 무시).
      is_mixed 경계(예: 10%p)를 넘는 순간 적용되는 규칙이 갑자기 바뀐다.
    - "ratio": is_mixed와 상관없이 세 유형의 비율 그대로 가중한다(비율 0인 유형은 뺀다). 상품 점수(body_fit)가
      이미 세 유형 비율로 계산되는 것과 같은 방식이고, 경계에서 값이 끊기지 않는다. 팀 결정이 필요해서 기본은 flag다."""
    scope = rules.config.get("balance_scope", "flag")
    if scope not in ("flag", "ratio"):
        raise ValueError('config.balance_scope는 "flag" 또는 "ratio"여야 한다')
    if scope == "flag":
        return _type_shares(profile)
    return {t: profile["ratios"][t] for t in TYPES if profile["ratios"][t] > 0}


def balance_adjustment(top_tags: list[str], bottom_tags: list[str], profile: dict, rules: Rules) -> tuple[float, list[dict]]:
    """상의·하의 태그에 밸런스 규칙을 적용한 보정값과, 적용된 규칙 목록."""
    profile = normalize_profile(profile)
    delta, applied = 0.0, []
    for t, share in _balance_shares(profile, rules).items():
        for rule in rules.balance.get("rules", {}).get(t, []):
            if _rule_hits(rule, top_tags, bottom_tags):
                delta += share * rule["value"]
                applied.append({"type": t, "name": rule["name"], "value": rule["value"], "share": round(share, 3)})
    return delta, applied


def outfit_score(top: dict, bottom: dict, profile: dict, rules: Rules) -> float:
    """(상의 점수 + 하의 점수) / 2 + 골격별 밸런스 보정 (보너스 +0.10 / 감점 -0.15), 0~1로 자른다.

    top/bottom = {"tags": [...], "score": Final 또는 "body_fit": ...}  (match_item 결과를 그대로 써도 된다)
    밸런스 규칙은 PDF 8쪽 표(data/balance.json). 혼합형은 1위·2위 규칙을 비율(P)만큼 가중 적용.
    """
    sa, sb = _item_score(top), _item_score(bottom)
    if sa is None or sb is None:
        raise ValueError("점수가 없는 상품(제외 대상)은 코디에 쓸 수 없다")
    delta, _ = balance_adjustment(top.get("tags", []), bottom.get("tags", []), profile, rules)
    return min(1.0, max(0.0, (sa + sb) / 2 + delta))


def recommend_outfits(tops: list[dict], bottoms: list[dict], profile: dict, rules: Rules,
                      outers: list[dict] | None = None, top_n: int = 3) -> list[dict]:
    """상위 K개 상의 x 상위 K개 하의를 조합해 코디 top_n개를 돌려준다 (PDF 8쪽).

    - K = config.outfit_top_k. 점수가 없는(제외된) 상품은 뺀다.
    - 같은 상의는 최대 1회만 노출한다.
    - 아우터는 점수가 가장 높은 1개를 각 코디에 붙인다 (코디 점수에는 반영하지 않는다).
    """
    k = rules.config["outfit_top_k"]

    def best(items: list[dict]) -> list[dict]:
        usable = [i for i in items if _item_score(i) is not None]
        return sorted(usable, key=lambda i: -_item_score(i))[:k]

    top_c, bottom_c = best(tops), best(bottoms)
    pairs = []
    for t in top_c:
        for b in bottom_c:
            delta, applied = balance_adjustment(t.get("tags", []), b.get("tags", []), profile, rules)
            score = min(1.0, max(0.0, (_item_score(t) + _item_score(b)) / 2 + delta))
            pairs.append((score, t, b, applied))
    pairs.sort(key=lambda x: -x[0])  # 점수가 같으면 입력 순서 유지

    outer = best(outers or [])[:1]
    result, used_tops = [], set()
    for score, t, b, applied in pairs:
        if t["item_id"] in used_tops:
            continue
        used_tops.add(t["item_id"])
        result.append({"top": t["item_id"], "bottom": b["item_id"],
                       "outer": outer[0]["item_id"] if outer else None,
                       "score": round(score, 4), "balance": applied})
        if len(result) == top_n:
            break
    return result


# ---------------------------------------------------------------- C에 넘기는 형태

FORM_SOURCES = ("direct", "manual")     # direct = 판매자 직접 등록, manual = 팀이 외부 상품을 같은 양식으로 입력. 둘 다 검증된 양식 입력이 우선한다.
def _tag_owners(rules: Rules, category: str) -> dict[str, set[str]]:
    """양식 정의에서 태그 -> 그 태그를 만드는 항목 키 집합. 이 카테고리에 없는 항목·선택지는 뺀다(상의에는 실루엣이 없다).
    혼용률은 섬유 태그의 소유 항목이다."""
    owners: dict[str, set[str]] = {}
    for f in rules.attributes.get("fields", []):
        if category not in f.get("applies", []):
            continue
        for choice, g in f.get("options", {}).items():
            allowed = f.get("option_applies", {}).get(choice)
            if g and not (allowed and category not in allowed):
                owners.setdefault(g, set()).add(f["key"])
        if f.get("type") == "composition":
            for fiber in f.get("fibers", []):
                g = rules.materials.get(fiber, {}).get("tag")
                if g:
                    owners.setdefault(g, set()).add(f["key"])
    return owners


def _text_fill_tags(attrs_stored: dict, name: str, desc: str, rules: Rules) -> list[str]:
    """양식 값이 우선이다. 글에서 읽은 태그 중, 그 태그를 만드는 양식 항목이 모두 '미입력'인 선택 항목뿐일 때만 보충한다.
    - 항목에 '해당 없음'·'보통' 같은 값을 골랐다면 입력된 것이므로 보충하지 않는다(미입력 != 없다고 선택함).
    - 필수 항목(핏·기장·혼용률)이 만드는 태그는 보충하지 않는다. 롱 카디건의 len:long, 섬유 태그 등 필수 입력을 우회하는 태그가 여기 해당한다.
    - 양식에 없는 태그(목선 등)와, 이 카테고리에 없는 항목의 태그(상의의 실루엣 등)는 보충하지 않는다."""
    owners = _tag_owners(rules, attrs_stored.get("category"))
    required = {f["key"] for f in rules.attributes.get("fields", []) if f.get("required")}
    filled = {k for k, v in attrs_stored.items() if v not in (None, "", [])}
    out = []
    for g in tag_item(name, desc, rules):
        own = owners.get(g)
        if own and not (own & required) and not (own & filled):
            out.append(g)
    return out


def match_item(item: dict, profile: dict, rules: Rules) -> dict:
    """상품 하나의 체형 적합도. item = {item_id, name, desc?, tags?, source?, attrs?}.
    review = {"n": 리뷰 수, "mismatch": 등록 정보와 다르다고 한 수}가 있으면 review_agreement를 계산해 출력에 싣는다. config.review_mode가 off(기본)이면 점수에는 반영하지 않는다.
    source가 direct·manual이면 attrs(등록 양식)가 우선하고, 양식에서 비운 선택 항목만 name/desc로 보충한다(text_added에 따로 적는다).
    item["text_fill"]=False면 글 보충을 끈다(상품명도 읽으므로 desc를 비우는 것으로는 꺼지지 않는다).
    아니면 name/desc에서만 태그를 만든다. tags를 직접 주면 그대로 쓴다."""
    source = item.get("source")
    fit_stated, text_added = None, []
    tags = item.get("tags")
    if tags is None and source in FORM_SOURCES:      # 양식 입력 상품: 검증을 통과한 입력만 쓴다(일부만 있으면 오류)
        r = tag_from_attributes(item.get("attrs") or {}, rules)
        tags, fit_stated = r["tags"], r["fit_stated"]
        text_added = [] if item.get("text_fill") is False else [g for g in _text_fill_tags(r["attributes"], item.get("name", ""), item.get("desc", ""), rules) if g not in tags]
        tags = tags + text_added
    elif tags is None and item.get("attrs"):
        raise ValueError('attrs는 양식 입력 상품(source="direct" 또는 "manual")에만 쓴다. 가져온 상품은 name/desc에서 태그를 만든다')
    if tags is None:
        r = tag_item_full(item["name"], item.get("desc", ""), rules)
        tags, fit_stated = r["tags"], r["fit_stated"]
    if fit_stated is None:
        fit_stated = fit_stated_from_tags(tags)
    agreement = review_agreement(item.get("review"), rules)
    applied = agreement if rules.config.get("review_mode", "off") != "off" else 1.0      # 기본은 수집·표시만 하고 점수에는 반영하지 않는다
    score = body_fit(tags, profile, rules, applied)
    return {
        "item_id": item["item_id"],
        "tags": tags,
        "text_added": text_added,
        "review_agreement": round(agreement, 4),
        "fit_stated": fit_stated,
        "body_fit": score,
        "excluded": score is None,
        "reasons": [] if score is None else reasons(tags, profile, rules),
    }


def match_items(items: list[dict], profile: dict, rules: Rules) -> list[dict]:
    return [match_item(it, profile, rules) for it in items]


def _pure_profile(t: str) -> dict:
    return {"ratios": {k: 1.0 if k == t else 0.0 for k in TYPES}, "primary": t, "is_mixed": False}


def rank_items(matched: list[dict], profile: dict, rules: Rules, k: int = 10, strategy: str | None = None) -> list[dict]:
    """match_item 결과(같은 카테고리 묶음)에서 추천 상품 k개를 고른다. 단일형은 strategy와 관계없이 점수순이다.
    strategy="blend": 혼합형도 비율을 섞은 점수(body_fit) 하나로 줄 세운다(기본).
    strategy="quota": 혼합형이면 1·2위 유형의 비율대로 자리를 나눠, 각 유형 기준 점수가 높은 상품을 번갈아 고른다
        (예: S 0.6 / N 0.4, k=10이면 대략 6개는 S 기준 상위, 4개는 N 기준 상위). 한 상품이 두 유형에 다 뽑히면 한 번만 쓴다.
    strategy="quota_safe": quota와 같되, 비율을 나누는 어느 유형에서도 중립보다 낮은 점수를 받는 상품은 후보에서 뺀다(한쪽 몫으로 뽑았는데 다른 쪽에 안 맞는 상품이 섞이는 것을 막는다). 후보가 모자라면 그 자리는 빈 채로 두지 않고 quota 방식으로 채운다.
    반환: [{item_id, score(섞은 점수), type_score(뽑힌 유형 기준 점수), picked_for(유형 또는 None)}]"""
    profile = normalize_profile(profile)
    strategy = strategy or rules.config.get("mixed_strategy", "blend")
    if strategy not in ("blend", "quota", "quota_safe"):
        raise ValueError('strategy는 "blend", "quota", "quota_safe" 중 하나여야 한다')
    usable = [m for m in matched if m["body_fit"] is not None]
    blend = sorted(usable, key=lambda m: -m["body_fit"])
    if strategy == "blend" or not profile["is_mixed"]:
        return [{"item_id": m["item_id"], "score": m["body_fit"], "type_score": m["body_fit"], "picked_for": None} for m in blend[:k]]
    shares = _type_shares(profile)
    apply_review = rules.config.get("review_mode", "off") != "off"
    pure = {t: {m["item_id"]: body_fit(m["tags"], _pure_profile(t), rules, m.get("review_agreement", 1.0) if apply_review else 1.0) for m in usable} for t in shares}
    order = {m["item_id"]: i for i, m in enumerate(usable)}
    neutral = (0.0 - rules.w_min) / (rules.w_max - rules.w_min)
    safe = {m["item_id"] for m in usable if all(pure[t][m["item_id"]] >= neutral - 1e-9 for t in shares)}
    def pool(t):
        ranked = sorted(usable, key=lambda m: (-pure[t][m["item_id"]], order[m["item_id"]]))
        if strategy != "quota_safe":
            return ranked
        return [m for m in ranked if m["item_id"] in safe] + [m for m in ranked if m["item_id"] not in safe]     # 안전한 후보를 먼저, 모자라면 나머지로
    lists = {t: pool(t) for t in shares}
    chosen, out, picked = set(), [], {t: 0 for t in shares}
    for n in range(min(k, len(usable))):
        t = max(shares, key=lambda x: (shares[x] * (n + 1) - picked[x], shares[x]))     # 지금까지 부족한 만큼 자리가 많이 남은 유형부터
        m = next((c for c in lists[t] if c["item_id"] not in chosen), None)
        if m is None:
            t = next(x for x in shares if x != t)
            m = next(c for c in lists[t] if c["item_id"] not in chosen)
        chosen.add(m["item_id"])
        picked[t] += 1
        out.append({"item_id": m["item_id"], "score": m["body_fit"], "type_score": pure[t][m["item_id"]], "picked_for": t})
    return out


_OUTFIT_GROUP = {"top": "top", "outer": "outer", "bottom": "bottom", "pants": "bottom", "skirt": "bottom"}   # 원피스(dress)는 코디 짝이 없어 상품 점수만 낸다


def recommend(profile: dict, items: list[dict], rules: Rules | None = None, top_n: int = 3, list_size: int = 10, mixed_strategy: str | None = None) -> dict:
    """C에 넘기는 한 번에 호출 함수. items = [{item_id, category, name, desc?, tags?, source?, attrs?}, ...]
    category: top | bottom | outer | pants | skirt | dress. 양식 입력 상품(source direct·manual)은 attrs의 category를 쓰므로 둘이 같아야 한다.
    반환: {"items": [match_item 결과 + category], "ranking": {top|bottom|outer: rank_items 결과}, "outfits": [코디 top_n개], "fit_unknown": 핏 표기를 못 읽은 상품 item_id 목록}
    ranking의 k는 list_size(기본 10), 혼합형 방식은 config.mixed_strategy(blend|quota) 또는 mixed_strategy 인자."""
    rules = rules or load_rules()
    normalize_profile(profile)      # 상품이 없어도 잘못된 체형 입력은 오류로 알린다
    matched = []
    for it in items:
        cat = it.get("category")
        if it.get("source") in FORM_SOURCES and it.get("attrs", {}).get("category", cat) != cat:
            raise ValueError(f'item {it.get("item_id")}: category={cat!r}와 attrs.category가 다르다')
        matched.append({**match_item(it, profile, rules), "category": cat})
    group = lambda g: [m for m in matched if _OUTFIT_GROUP.get(m["category"]) == g]
    outfits = recommend_outfits(group("top"), group("bottom"), profile, rules, outers=group("outer"), top_n=top_n)
    ranking = {g: rank_items(group(g), profile, rules, list_size, mixed_strategy) for g in ("top", "bottom", "outer")}
    return {"items": matched, "ranking": ranking, "outfits": outfits,
            "fit_unknown": [m["item_id"] for m in matched if m["fit_stated"] == "unknown"]}
