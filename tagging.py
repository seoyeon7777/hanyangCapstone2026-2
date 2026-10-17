"""태깅 - 상품 정보(양식 입력 또는 글)에서 태그를 붙인다. 판매자 등록 때 한 번 돌고, 결과(태그)는 상품과 함께 저장된다.

점수·추천(matching.py)은 이 파일을 쓰지 않는다. 저장된 태그만 읽는다.
규칙은 data/keywords.json, materials.json, attributes.json 에서 읽는다.
"""
from __future__ import annotations

import re
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from matching import Rules


_SKIP_SENT = re.compile(r"매치|코디|스타일링|어울|사이즈\s*[\d,\s]{4,}|세탁|건조|다림질|표백|울\s*코스|교환|반품|환불|제품의\s*경우(?=[^.\n]*(?:스크레치|스크래치|주름|반점|하자|불량))|(?:스크레치|스크래치|반점|하자)[^.\n]*제품의\s*경우|배송|(?:아우터|상의|하의|이너|액세서리|신발|가방)\s*\||손질|how to care|약품\s*냄새|털\s*날림|fit\s*guide|size\s*tip|데님류|데님의\s*경우|모티브|어우러|셋업|추천드리|빨래|울\s*샴푸|울샴프|세탁망|트림\s*(?:부분|원단)|(?:나일론|nylon)\s*(?:소재|원단)?\s*의?\s*(?:여밈|지퍼|스냅|단추)|\([A-Za-z]{2,}[A-Za-z0-9]{5,}\)")
_COTTON_KO = re.compile(r"(?<![가-힣])면(?=\s*\d+\s*%|\s*[/,·]\s*(?:폴리|레이온|나일론|스판|린넨|울|아크릴)|\s*(?:과|와)\s*(?:폴리|레이온|나일론|스판|린넨|울|아크릴)|\s*혼방)")  # 한 글자 '면'은 소재 표기 문맥에서만 코튼으로 본다
_NEGATED = re.compile(r"(?:슬림|오버핏|오버사이즈|오버|루즈|와이드|크롭|스키니)\S{0,3}\s*(?:하지\s*않|지\s*않|아닌|아니)")
_COLOR_DENIM = re.compile(r"\(\s*denim\s*\)|(?:색상|color)\s*[:：]?\s*denim|^\s*denim\s*$", re.M)  # 색상명 DENIM은 소재가 아님
_WIDE_NECK = re.compile(r"(?:와이드|넉넉한?)[^.\n]{0,8}(?:네크라인|넥라인|카라|칼라)")                             # '와이드한 폭의 보트 네크라인'은 바지 실루엣이 아님
_SENT_SPLIT = re.compile(r"[.\n■●▶]|(?<=다)\s|(?=composition|혼용률|material\s*[:：])")   # 마침표 없이 이어 붙은 설명에서 혼용률 문구가 앞 문장(코디 설명)과 같이 버려지지 않게 끊는다


_FIT_LEGEND = re.compile(r"(?:(?:슬림|기본|레귤러|세미\s*루즈|루즈|오버|와이드)\s*핏(?:\s*/\s*(?:오버)\s*핏)?[\s|/·,]*){3,}")   # 케어라벨 범례표 '슬림핏|기본핏|루즈핏/오버핏|와이드핏' (상품 설명이 아님)
_FIT_SCALE = re.compile(r"(?:슬림|타이트|레귤러|세미\s*루즈|루즈|오버)\s*(?:핏|fit)?\s*(?:적당함?|보통|정\s*사이즈|좋음|없음)\s*(?:슬림|타이트|레귤러|세미\s*루즈|루즈|오버)?\s*(?:핏|fit)?")   # 상세 이미지 속 착용감 척도 '슬림핏 적당함 루즈핏' (선택지일 뿐 선택값이 아님)
MAX_DESC_CHARS = 5000
_STYLING_TAIL = re.compile(r"(?:[가-힣a-z]+\s{0,3},\s{0,3}){0,3}[가-힣a-z]*\s{0,3}스타일링(?:에|으로)?\s{0,3}(?:활용|사용)?\s{0,3}(?:가능|하기\s{0,3}좋)")   # '...루즈핏 실루엣 드롭 숄더 데일리, 빈티지, 스트릿 스타일링에 활용 가능'처럼 마침표 없이 이어진 문장에서 뒤쪽 코디 문구 때문에 앞쪽 핏 설명까지 버려지는 것 방지
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
    desc = desc[:MAX_DESC_CHARS]      # 정규식 몇 개가 아주 긴 반복 문자열에서 제곱 시간이 걸리므로 길이를 막는다 (서버는 이보다 길면 400)
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
    if percent_list is not None and not isinstance(percent_list, list):
        raise ValueError('composition_percent는 [{"fiber": ..., "percent": ...}, ...] 목록이어야 한다')
    out = []
    for m in percent_list or []:
        if not isinstance(m, dict):
            raise ValueError('composition_percent의 각 항목은 {"fiber": ..., "percent": ...} 객체여야 한다')
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
        if not isinstance(comp, list) or any(not isinstance(m, dict) for m in comp):
            raise ValueError('attrs: composition은 [{"fiber": ..., "share": ...}, ...] 목록이어야 한다')
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
