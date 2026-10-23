"""B. 옷 매칭 - 태그 붙이기, 체형 적합도, 추천 이유, 코디 점수.

입력  : A의 체형 결과(profile), 상품(item)
출력  : C가 받는 dict (match_item)
규칙  : 가중치·키워드·문장·설정은 data/*.json 에서 읽는다. 코드에 숫자를 두지 않는다.

profile = {"ratios": {"S": .54, "W": .17, "N": .29}, "primary": "S", "secondary": "N", "confidence": .25, "is_mixed": False}  # A의 classify() 출력
item    = {"product_id": 1, "product_name": "...", "clothing_type": "top", "season": "FW", "popularity": .6, "tags": [...]}
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path
import math

TYPES = ("S", "W", "N")
DATA_DIR = Path(__file__).parent / "data"


# ---------------------------------------------------------------- 체형 입력 (A의 classify 출력)

def normalize_profile(profile: dict) -> dict:
    """A(체형 분류)의 출력을 검증하고 다듬는다. 이 모듈의 공개 함수는 모두 이걸 거친다.

    입력: {"ratios": {"S":..,"W":..,"N":..}, "primary": "S", "secondary": "N", "confidence": .25, "is_mixed": False}
    - ratios 합이 1이 아니면(반올림 오차 등) 1로 맞춘다.
    - ratios·primary·is_mixed 가 없거나, 비율이 음수·NaN·무한대·합 0이거나, primary가 비율 최대 유형이 아니면 ValueError.
    - 예전 이름(p / main / mixed)도 받지만 내부에서는 새 이름만 쓴다. secondary·confidence·source 는 계산에 쓰지 않는다.
    """
    ratios = profile.get("ratios", profile.get("p"))
    primary = profile.get("primary", profile.get("main"))
    mixed = profile.get("is_mixed", profile.get("mixed"))
    if not isinstance(ratios, dict) or any(t not in ratios for t in TYPES):
        raise ValueError('profile: "ratios"에 S, W, N 세 값이 모두 필요하다')
    vals = {t: ratios[t] for t in TYPES}
    if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) or v < 0 for v in vals.values()):
        raise ValueError("profile: 비율은 0 이상의 유한한 숫자여야 한다 (NaN·무한대 불가)")
    total = sum(vals.values())
    if total <= 0:
        raise ValueError("profile: 비율의 합이 0이다")
    if primary not in TYPES:
        raise ValueError('profile: "primary"는 S, W, N 중 하나여야 한다')
    if not isinstance(mixed, bool):
        raise ValueError('profile: "is_mixed"는 true/false여야 한다')
    if vals[primary] < max(vals.values()) * (1 - 1e-6):
        raise ValueError(f'profile: primary={primary!r}가 비율이 가장 큰 유형이 아니다 (ratios={vals})')
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


# ---------------------------------------------------------------- 2) 태깅 -> tagging.py

from tagging import (
    MAX_DESC_CHARS,  # 태깅은 tagging.py로 분리했다. 기존 이름(matching.tag_item 등)은 그대로 쓸 수 있게 다시 내보낸다.
    _SKIP_SENT,
    _COTTON_KO,
    _NEGATED,
    _COLOR_DENIM,
    _WIDE_NECK,
    _SENT_SPLIT,
    _FIT_LEGEND,
    _FIT_SCALE,
    _STYLING_TAIL,
    _CARE_START,
    _CARE_MATERIAL,
    _BODY_SLIM,
    _TRIM_LEATHER,
    _GARMENT,
    _COORD_FROM,
    _COORD_ANY,
    _PART_LOOSE,
    _LOOKS_SLIM,
    _LABELED_COMP,
    _REGULAR_FIT,
    _FIT_FROM_OPTION,
    fit_stated_from_tags,
    _clean_desc,
    _fiber_regex,
    _PART_MAIN,
    _PART_SUB,
    parse_composition,
    _fiber_tags,
    percent_to_share,
    tag_from_attributes,
    tag_item_full,
    tag_item,
)  # noqa: F401


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
        if t["product_id"] in used_tops:
            continue
        used_tops.add(t["product_id"])
        result.append({"top": t["product_id"], "bottom": b["product_id"],
                       "outer": outer[0]["product_id"] if outer else None,
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
    """상품 하나의 체형 적합도. item = {product_id, product_name, description?, tags?, source?, attrs?}.
    review = {"n": 리뷰 수, "mismatch": 등록 정보와 다르다고 한 수}가 있으면 review_agreement를 계산해 출력에 싣는다. config.review_mode가 off(기본)이면 점수에는 반영하지 않는다.
    source가 direct·manual이면 attrs(등록 양식)가 우선하고, 양식에서 비운 선택 항목만 product_name/description으로 보충한다(text_added에 따로 적는다).
    item["text_fill"]=False면 글 보충을 끈다(상품명도 읽으므로 description을 비우는 것으로는 꺼지지 않는다).
    아니면 product_name/description에서만 태그를 만든다. tags를 직접 주면 그대로 쓴다."""
    source = item.get("source")
    fit_stated, text_added = item.get("fit_stated"), list(item.get("text_added") or [])     # 등록 때 저장된 값이 있으면 그대로 쓴다
    tags = item.get("tags")
    if tags is None and source in FORM_SOURCES:      # 양식 입력 상품: 검증을 통과한 입력만 쓴다(일부만 있으면 오류)
        r = tag_from_attributes(item.get("attrs") or {}, rules)
        tags, fit_stated = r["tags"], r["fit_stated"]
        text_added = [] if item.get("text_fill") is False else [g for g in _text_fill_tags(r["attributes"], item.get("product_name", ""), item.get("description", ""), rules) if g not in tags]
        tags = tags + text_added
    elif tags is None and item.get("attrs"):
        raise ValueError('attrs는 양식 입력 상품(source="direct" 또는 "manual")에만 쓴다. 가져온 상품은 product_name/description에서 태그를 만든다')
    if tags is None:
        r = tag_item_full(item["product_name"], item.get("description", ""), rules)
        tags, fit_stated = r["tags"], r["fit_stated"]
    if fit_stated is None:
        fit_stated = fit_stated_from_tags(tags)
    agreement = review_agreement(item.get("review"), rules)
    applied = agreement if rules.config.get("review_mode", "off") != "off" else 1.0      # 기본은 수집·표시만 하고 점수에는 반영하지 않는다
    score = body_fit(tags, profile, rules, applied)
    # 정보 없음: 점수에 쓸 태그가 하나도 없고 핏 표기도 못 읽은 상품. 중립 점수를 주는 대신 추천에서 뺀다(config.exclude_no_info).
    # 핏을 읽었는데(레귤러 등) 태그가 없는 상품은 '특징 없는 기본 상품'이라 정보가 있는 것으로 보고 중립 점수로 남긴다.
    no_info = not _known(tags, rules) and fit_stated == "unknown"
    if no_info and rules.config.get("exclude_no_info", True):
        score = None
    else:
        no_info = False
    return {
        "product_id": item["product_id"],
        "tags": tags,
        "text_added": text_added,
        "review_agreement": round(agreement, 4),
        "fit_stated": fit_stated,
        "body_fit": score,
        "excluded": score is None,
        "no_info": no_info,
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
    반환: [{product_id, score(섞은 점수), type_score(뽑힌 유형 기준 점수), picked_for(유형 또는 None)}]"""
    profile = normalize_profile(profile)
    strategy = strategy or rules.config.get("mixed_strategy", "blend")
    if strategy not in ("blend", "quota", "quota_safe"):
        raise ValueError('strategy는 "blend", "quota", "quota_safe" 중 하나여야 한다')
    usable = [m for m in matched if m["body_fit"] is not None]
    blend = sorted(usable, key=lambda m: -m["body_fit"])
    if strategy == "blend" or not profile["is_mixed"]:
        return [{"product_id": m["product_id"], "score": m["body_fit"], "type_score": m["body_fit"], "picked_for": None} for m in blend[:k]]
    shares = _type_shares(profile)
    apply_review = rules.config.get("review_mode", "off") != "off"
    pure = {t: {m["product_id"]: body_fit(m["tags"], _pure_profile(t), rules, m.get("review_agreement", 1.0) if apply_review else 1.0) for m in usable} for t in shares}
    order = {m["product_id"]: i for i, m in enumerate(usable)}
    neutral = (0.0 - rules.w_min) / (rules.w_max - rules.w_min)
    safe = {m["product_id"] for m in usable if all(pure[t][m["product_id"]] >= neutral - 1e-9 for t in shares)}
    def pool(t):
        ranked = sorted(usable, key=lambda m: (-pure[t][m["product_id"]], order[m["product_id"]]))
        if strategy != "quota_safe":
            return ranked
        return [m for m in ranked if m["product_id"] in safe] + [m for m in ranked if m["product_id"] not in safe]     # 안전한 후보를 먼저, 모자라면 나머지로
    lists = {t: pool(t) for t in shares}
    chosen, out, picked = set(), [], {t: 0 for t in shares}
    for n in range(min(k, len(usable))):
        t = max(shares, key=lambda x: (shares[x] * (n + 1) - picked[x], shares[x]))     # 지금까지 부족한 만큼 자리가 많이 남은 유형부터
        m = next((c for c in lists[t] if c["product_id"] not in chosen), None)
        if m is None:
            t = next(x for x in shares if x != t)
            m = next(c for c in lists[t] if c["product_id"] not in chosen)
        chosen.add(m["product_id"])
        picked[t] += 1
        out.append({"product_id": m["product_id"], "score": m["body_fit"], "type_score": pure[t][m["product_id"]], "picked_for": t})
    return out


_OUTFIT_GROUP = {"top": "top", "outer": "outer", "bottom": "bottom", "pants": "bottom", "skirt": "bottom"}   # 원피스(dress)는 코디 짝이 없어 상품 점수만 낸다


def validate_item(it: dict) -> None:
    """추천에 넣는 상품 한 개의 형식을 검사한다. 잘못되면 ValueError (서버에서는 400)."""
    if not isinstance(it, dict):
        raise ValueError("상품은 객체여야 한다")
    iid = it.get("product_id")
    if iid is None or isinstance(iid, bool) or not isinstance(iid, (str, int)) or iid == "":
        raise ValueError("product_id는 비어 있지 않은 문자열 또는 정수여야 한다")
    if it.get("clothing_type") not in _OUTFIT_GROUP and it.get("clothing_type") != "dress":
        raise ValueError(f'item {iid}: clothing_type은 {sorted(set(_OUTFIT_GROUP) | {"dress"})} 중 하나여야 한다 (받은 값: {it.get("clothing_type")!r})')
    for key in ("product_name", "description"):
        if it.get(key) is not None and not isinstance(it[key], str):
            raise ValueError(f"item {iid}: {key}는 문자열이어야 한다")
    if len(it.get("description") or "") > MAX_DESC_CHARS:
        raise ValueError(f"item {iid}: description은 {MAX_DESC_CHARS}자 이하여야 한다")
    tags = it.get("tags")
    if tags is not None and (not isinstance(tags, list) or any(not isinstance(g, str) for g in tags)):
        raise ValueError(f"item {iid}: tags는 문자열 목록이어야 한다")


def recommend(profile: dict, items: list[dict], rules: Rules | None = None, top_n: int = 3, list_size: int = 10, mixed_strategy: str | None = None) -> dict:
    """C에 넘기는 한 번에 호출 함수. items = [{product_id, clothing_type, product_name, description?, tags?, source?, attrs?}, ...]
    clothing_type: top | bottom | outer | pants | skirt | dress. 양식 입력 상품(source direct·manual)은 attrs의 category를 쓰므로 둘이 같아야 한다.
    반환: {"items": [match_item 결과 + clothing_type], "ranking": {top|bottom|outer: rank_items 결과}, "outfits": [코디 top_n개], "fit_unknown": 핏 표기를 못 읽은 상품 product_id 목록, "no_info": 태그도 핏도 못 읽어 추천에서 뺀 상품 product_id 목록}
    ranking의 k는 list_size(기본 10), 혼합형 방식은 config.mixed_strategy(blend|quota) 또는 mixed_strategy 인자."""
    rules = rules or load_rules()
    normalize_profile(profile)      # 상품이 없어도 잘못된 체형 입력은 오류로 알린다
    matched, seen = [], set()
    for it in items:
        validate_item(it)
        if it["product_id"] in seen:
            raise ValueError(f'product_id {it["product_id"]!r}가 중복되었다')
        seen.add(it["product_id"])
        cat = it.get("clothing_type")
        if it.get("source") in FORM_SOURCES and it.get("attrs", {}).get("category", cat) != cat:
            raise ValueError(f'item {it.get("product_id")}: clothing_type={cat!r}와 attrs.category가 다르다')
        matched.append({**match_item(it, profile, rules), "clothing_type": cat})
    group = lambda g: [m for m in matched if _OUTFIT_GROUP.get(m["clothing_type"]) == g]
    outfits = recommend_outfits(group("top"), group("bottom"), profile, rules, outers=group("outer"), top_n=top_n)
    ranking = {g: rank_items(group(g), profile, rules, list_size, mixed_strategy) for g in ("top", "bottom", "outer")}
    return {"items": matched, "ranking": ranking, "outfits": outfits,
            "fit_unknown": [m["product_id"] for m in matched if m["fit_stated"] == "unknown"],
            "no_info": [m["product_id"] for m in matched if m["no_info"]]}
