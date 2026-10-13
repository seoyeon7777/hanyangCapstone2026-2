"""코디 밸런스 규칙 적용 범위(config.balance_scope) flag vs ratio 비교.  python tools/balance_compare.py
같은 상품·같은 체형 비율로 두 방식의 코디 순위가 어떻게 달라지는지 본다.
상품은 가상 판매자 샘플(samples/synthetic_sellers.json)이라 '어느 쪽이 더 좋은 추천인가'를 말해 주지는 않는다. 계산 방식의 차이만 보여 준다."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402
import server  # noqa: E402

PROFILES = {
    "S 단일 (.61/.17/.22)": {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False},
    "W 단일 (.15/.65/.20)": {"ratios": {"S": 0.15, "W": 0.65, "N": 0.20}, "primary": "W", "is_mixed": False},
    "N 단일 (.20/.15/.65)": {"ratios": {"S": 0.20, "W": 0.15, "N": 0.65}, "primary": "N", "is_mixed": False},
    "혼합 S.55/N.45": {"ratios": {"S": 0.55, "W": 0.0, "N": 0.45}, "primary": "S", "is_mixed": True, "secondary": "N"},
    "경계 직전 S.52/W.10/N.38 (is_mixed=True)": {"ratios": {"S": 0.52, "W": 0.10, "N": 0.38}, "primary": "S", "is_mixed": True, "secondary": "N"},
    "경계 직후 S.52/W.10/N.38 (is_mixed=False)": {"ratios": {"S": 0.52, "W": 0.10, "N": 0.38}, "primary": "S", "is_mixed": False, "secondary": "N"},
    "세 유형 근접 S.40/W.32/N.28": {"ratios": {"S": 0.40, "W": 0.32, "N": 0.28}, "primary": "S", "is_mixed": True, "secondary": "W"},
}


def run(scope, bodies, names):
    rules = matching.load_rules()
    rules.config["balance_scope"] = scope
    client = server.create_app(rules).test_client()
    for b in bodies:
        client.post("/api/items", json=b)
    out = {}
    for label, p in PROFILES.items():
        j = client.post("/api/recommend", json={"profile": p, "top_n": 10, "list_size": 10}).get_json()
        out[label] = [(f'{names[o["top"]]} + {names[o["bottom"]]}', round(o["score"], 3), [b["name"] for b in o["balance"]]) for o in j["outfits"]]
    return out


def main():
    data = json.load(open(ROOT / "samples" / "synthetic_sellers.json", encoding="utf-8"))["items"]
    bodies = [it["body"] for it in data]
    names = {b["item_id"]: b["name"] for b in bodies}
    flag, ratio = run("flag", bodies, names), run("ratio", bodies, names)
    lines = ["코디 밸런스 규칙 적용 범위 비교: flag(이전: 단일형=1위만, 혼합형=1·2위만) vs ratio(세 유형 비율대로)",
             "상품: 가상 판매자 샘플 중 등록된 상품. 코디 상위 10개 비교. 어느 쪽이 더 좋은 추천인지는 이 결과로 판단할 수 없다.", ""]
    for label in PROFILES:
        f, r = flag[label], ratio[label]
        fn, rn = [x[0] for x in f], [x[0] for x in r]
        same = len(set(fn) & set(rn))
        same_order = sum(1 for a, b in zip(fn, rn) if a == b)
        lines.append(f"=== {label} ===  상위10 겹침 {same}/10, 같은 순위 {same_order}/10")
        for title, rows in (("flag", f[:5]), ("ratio", r[:5])):
            lines.append(f"  [{title}]")
            for name, sc, rules_ in rows:
                lines.append(f"    {sc:.3f}  {name}  {rules_ if rules_ else ''}")
        lines.append("")
    # 경계 직전/직후 비교(같은 비율, is_mixed만 다름)
    a, b = "경계 직전 S.52/W.10/N.38 (is_mixed=True)", "경계 직후 S.52/W.10/N.38 (is_mixed=False)"
    for title, res in (("flag", flag), ("ratio", ratio)):
        ra, rb = [x[0] for x in res[a]], [x[0] for x in res[b]]
        lines.append(f"[{title}] 같은 비율에서 is_mixed만 바꿨을 때 코디 상위10 같은 순위 {sum(1 for x, y in zip(ra, rb) if x == y)}/10, 겹침 {len(set(ra) & set(rb))}/10")
    text = "\n".join(lines)
    (ROOT / "samples" / "balance_compare.txt").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
