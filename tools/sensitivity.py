"""가중치·보정값 민감도 분석.  python tools/sensitivity.py [--trials 1000] [--seed 0]
시연 상품(samples/demo_tags.json)의 태그에 현재 규칙을 적용해 상품 순위를 만들고, 가정을 흔들었을 때 순위가 얼마나 바뀌는지 본다.
- 크기 흔들기: 가중치의 방향(부호)은 문헌에서 온 것이라 유지하고, 크기(1~3)만 -1/0/+1 무작위로 바꾼다.
- 태그 하나씩 빼기: 그 태그가 없는 규칙으로 순위를 다시 만든다.
- 설정값: shrink_k를 0·1·2·3으로 바꾼다.
지표: 기준 순위와의 켄달 tau-b(1이면 같은 순위), 상위 5개 겹침(5가 같음), 1위가 같은 비율. 같은 점수는 입력 순서로 자른다(경계 동점은 상위 5개 지표를 흔든다).
"""
import argparse
import copy
import json
import random
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402

PROFILES = {
    "S단일": {"ratios": {"S": 1.0, "W": 0.0, "N": 0.0}, "primary": "S", "is_mixed": False},
    "W단일": {"ratios": {"S": 0.0, "W": 1.0, "N": 0.0}, "primary": "W", "is_mixed": False},
    "N단일": {"ratios": {"S": 0.0, "W": 0.0, "N": 1.0}, "primary": "N", "is_mixed": False},
    "혼합(S.5 W.3 N.2)": {"ratios": {"S": 0.5, "W": 0.3, "N": 0.2}, "primary": "S", "is_mixed": True, "secondary": "W"},
}


def with_weights(base, weights, **config):
    cells = [v for row in weights.values() for v in row.values()]
    return matching.Rules(**{**base.__dict__, "weights": weights, "w_min": min(cells), "w_max": max(cells),
                             "config": {**base.config, **config}})


def scores(rules, items, profile):
    out = []
    for it in items:
        s = matching.body_fit(it["tags"], profile, rules)
        out.append(-1.0 if s is None else s)
    return out


def tau_b(a, b):
    n, c, d, ta, tb = len(a), 0, 0, 0, 0
    for i in range(n):
        for j in range(i + 1, n):
            x, y = a[i] - a[j], b[i] - b[j]
            if abs(x) < 1e-12 and abs(y) < 1e-12:
                continue
            if abs(x) < 1e-12:
                ta += 1
            elif abs(y) < 1e-12:
                tb += 1
            elif x * y > 0:
                c += 1
            else:
                d += 1
    den = ((c + d + ta) * (c + d + tb)) ** 0.5
    return (c - d) / den if den else 1.0


def top(sc, k=5):
    return set(sorted(range(len(sc)), key=lambda i: (-sc[i], i))[:k])


def compare(base_sc, sc):
    return tau_b(base_sc, sc), len(top(base_sc) & top(sc)), top(base_sc, 1) == top(sc, 1)


def summarize(rows):
    taus, ov, t1 = zip(*rows)
    q = sorted(taus)
    return (statistics.mean(taus), q[max(0, int(len(q) * 0.05) - 1)], statistics.mean(ov), 100 * sum(t1) / len(t1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--trials", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()
    rnd = random.Random(args.seed)
    base = matching.load_rules()
    items = json.load(open(ROOT / "samples" / "demo_tags.json", encoding="utf-8"))
    print(f"상품 {len(items)}개, 태그 {len(base.weights)}종, 시행 {args.trials}회, seed {args.seed}\n")
    base_sc = {p: scores(base, items, prof) for p, prof in PROFILES.items()}
    for p_, sc_ in base_sc.items():
        order = sorted(sc_, reverse=True)
        print(f"기준 순위 {p_}: 5위 점수와 같은 점수의 상품 {sum(abs(x - order[4]) < 1e-9 for x in sc_)}개 (동점 경계가 크면 상위 5개 지표는 흔들린다)")
    print()
    hdr = f'{"":<22}{"평균 tau":>9}{"하위5% tau":>11}{"상위5 평균겹침":>14}{"1위 같음(%)":>12}'

    print("[1] 가중치 크기를 -1/0/+1 무작위로 흔듦 (방향 유지, 크기 1~3)")
    print(hdr)
    for p, prof in PROFILES.items():
        rows = []
        for _ in range(args.trials):
            w = {g: {t: (0 if v == 0 else (1 if v > 0 else -1) * min(3, max(1, abs(v) + rnd.choice((-1, 0, 1))))) for t, v in row.items()}
                 for g, row in base.weights.items()}
            rows.append(compare(base_sc[p], scores(with_weights(base, w), items, prof)))
        m, lo, o, t1 = summarize(rows)
        print(f"{p:<22}{m:>9.3f}{lo:>11.3f}{o:>14.2f}{t1:>12.1f}")

    print("\n[2] 설정값 shrink_k (기준 1)")
    print(hdr)
    for k in (0, 2, 3):
        for p, prof in PROFILES.items():
            m, lo, o, t1 = summarize([compare(base_sc[p], scores(with_weights(base, copy.deepcopy(base.weights), shrink_k=k), items, prof))])
            print(f"k={k} {p:<17}{m:>9.3f}{'-':>11}{o:>14.0f}{t1:>12.0f}")

    print("\n[3] 태그 하나씩 빼기: 순위를 가장 많이 바꾸는 태그 (S단일 / W단일 / N단일 / 혼합 중 최저 tau)")
    worst = []
    for g in base.weights:
        w = {k: v for k, v in base.weights.items() if k != g}
        r = with_weights(base, w)
        res = {p: compare(base_sc[p], scores(r, items, prof)) for p, prof in PROFILES.items()}
        used = sum(g in it["tags"] for it in items)
        worst.append((min(v[0] for v in res.values()), g, used, res))
    for tau, g, used, res in sorted(worst)[:8]:
        print(f"{g:<22} 시연 상품 중 {used:>2}개에 붙음  최저 tau {tau:.3f}  1위 유지: " + ", ".join(f"{p}={'O' if v[2] else 'X'}" for p, v in res.items()))
    print(f"(시연 상품에 한 번도 안 붙은 태그: {sum(1 for _, _, u, _ in worst if u == 0)}종 - 이 분석으로는 영향을 알 수 없다)")


if __name__ == "__main__":
    main()
