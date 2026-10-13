"""평균 방식과 합산 방식의 점수를 비교한다 (samples/demo_tags.json 기준).  python tools/score_compare.py
- 평균(현재): matching.body_fit (태그 평균 + 개수 적은 상품은 중립 쪽으로 당김)
- 합산: 태그별 값을 더해 -2~+2로 제한한 뒤 0~1로 환산
- 체형은 S/W/N 단일형 3가지로 잰다.
- '떨어진 조합': 그 체형에서 가중치가 양수인 태그를 하나 더 붙였더니 평균 점수가 내려간 (상품, 체형, 추가 태그) 조합.
  분모는 상품이 아니라 이 조합의 수다.
"""
import json
import sys
from itertools import combinations
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402

rules = matching.load_rules()
items = [x for x in json.load(open(ROOT / "samples" / "demo_tags.json", encoding="utf-8")) if x["cat"] != "dress"]


def profile(t):
    return {"ratios": {k: 1.0 if k == t else 0.0 for k in "SWN"}, "primary": t, "is_mixed": False}


def total_score(tags, t):
    known = matching._known(tags, rules)
    s = max(-2.0, min(2.0, sum(rules.weights[g][t] for g in known)))
    return (s + 2) / 4


def ranks(v):
    order = sorted(range(len(v)), key=lambda i: v[i])
    r, i = [0.0] * len(v), 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and v[order[j + 1]] == v[order[i]]:
            j += 1
        for k in range(i, j + 1):
            r[order[k]] = (i + j) / 2 + 1
        i = j + 1
    return r


def spearman(a, b):
    ra, rb = ranks(a), ranks(b)
    ma, mb = sum(ra) / len(ra), sum(rb) / len(rb)
    num = sum((x - ma) * (y - mb) for x, y in zip(ra, rb))
    den = (sum((x - ma) ** 2 for x in ra) * sum((y - mb) ** 2 for y in rb)) ** 0.5
    return num / den if den else float("nan")


print(f"상품 {len(items)}개(원피스 제외), 태그 {len(rules.weights)}종")
drops = combos = 0
for t in "SWN":
    p = profile(t)
    avg = [matching.body_fit(x["tags"], p, rules) for x in items]
    tot = [total_score(x["tags"], t) for x in items]
    top = lambda sc: {items[i]["item_id"] for i in sorted(range(len(items)), key=lambda i: -sc[i])[:8]}
    print(f"{t}: 순위 상관 {spearman(avg, tot):.2f}, 상위 8개 겹침 {len(top(avg) & top(tot))}/8, "
          f"서로 다른 점수 평균 {len(set(round(a, 4) for a in avg))}종 / 합산 {len(set(round(a, 4) for a in tot))}종")
    for x in items:
        base = matching.body_fit(x["tags"], p, rules)
        for g, row in rules.weights.items():
            if row[t] > 0 and g not in x["tags"]:
                combos += 1
                drops += matching.body_fit(x["tags"] + [g], p, rules) < base
print(f"태그를 하나 더 붙여 평균 점수가 내려간 조합: {drops} / {combos} (= 상품 x 체형 3종 x 추가 태그)")
