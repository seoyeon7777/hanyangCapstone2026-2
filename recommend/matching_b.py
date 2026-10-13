"""B 파트(matching.py)를 C 파트 약속 모양으로 바꿔 주는 연결 파일.

C가 쓰는 함수 두 개를 B의 계산으로 채운다.
    match_score(body, item)                    → 체형 적합도·제외 여부·어울림/피해야 할 태그·추천 이유
    outfit_bonus(body, top_tags, bottom_tags)  → 코디 밸런스 보정 (B의 data/balance.json)

숫자·태그 가중치·문장은 모두 B의 data/*.json에서 온다. 여기서는 이름만 맞춘다.
    C의 body  = {"ratio": {...}, "kind": "single"|"mixed", "primary": "S"}
    B의 profile = {"ratios": {...}, "is_mixed": bool, "primary": "S"}  (A의 classify() 출력과 같은 모양)
"""
import os
import sys

# B의 matching.py는 저장소 맨 위 폴더에 있다
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

import matching  # noqa: E402  (B 파트)

RULES = matching.load_rules()


def to_profile(body):
    """C의 body → B(그리고 A)의 profile."""
    return {"ratios": body["ratio"], "primary": body["primary"], "is_mixed": body["kind"] == "mixed"}


def from_profile(profile):
    """A의 classify() 결과(=B의 profile) → C의 body."""
    return {"ratio": dict(profile["ratios"]), "primary": profile["primary"],
            "kind": "mixed" if profile["is_mixed"] else "single"}


def match_score(body, item):
    """C 약속 모양: {"score", "excluded", "good_tags", "avoid_tags"} + B가 만든 "reasons"."""
    m = matching.match_item({"item_id": item["id"], "name": item["name"], "tags": item["tags"]},
                            to_profile(body), RULES)
    weights, primary = RULES.weights, body["primary"]
    known = [t for t in m["tags"] if t in weights]
    # 1위 타입 가중치가 양수면 어울림(큰 순서), 음수면 피해야 함
    good = sorted((t for t in known if weights[t][primary] > 0), key=lambda t: -weights[t][primary])
    avoid = [t for t in known if weights[t][primary] < 0]
    return {
        "score": 0.0 if m["excluded"] else round(m["body_fit"], 4),
        "excluded": m["excluded"],
        "good_tags": good,
        "avoid_tags": avoid,
        "reasons": m["reasons"],
    }


def outfit_bonus(body, top_tags, bottom_tags):
    """B의 골격별 코디 밸런스 규칙(보너스 +0.10 / 감점 -0.15, 혼합형은 비율만큼)."""
    delta, _ = matching.balance_adjustment(top_tags, bottom_tags, to_profile(body), RULES)
    return delta
