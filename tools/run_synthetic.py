"""가상 판매자 샘플 100개를 서버(API)로 등록하고 기대 결과와 대조한다.  python tools/run_synthetic.py
1) 각 상품을 POST /api/items 로 등록 (잘못된 등록은 거절돼야 한다)  2) expect와 대조  3) 체형 4종으로 /api/recommend 호출
기대 결과는 설계 문서에서 정한 것이라, 이 결과는 '규칙대로 동작하는가'를 보여 줄 뿐 실제 상품에서의 정확도가 아니다."""
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402
import server  # noqa: E402

PROFILES = {
    "S 단일": {"ratios": {"S": 1.0, "W": 0.0, "N": 0.0}, "primary": "S", "is_mixed": False},
    "W 단일": {"ratios": {"S": 0.0, "W": 1.0, "N": 0.0}, "primary": "W", "is_mixed": False},
    "N 단일": {"ratios": {"S": 0.0, "W": 0.0, "N": 1.0}, "primary": "N", "is_mixed": False},
    "혼합 S.5 W.3 N.2": {"ratios": {"S": 0.5, "W": 0.3, "N": 0.2}, "primary": "S", "is_mixed": True, "secondary": "W"},
}


def check(expect, resp, data):
    """대조 결과: 어긋난 항목 설명 리스트 (비어 있으면 통과)."""
    errs = []
    if "error" in expect:
        if resp.status_code == 201:
            return [f'거절돼야 하는데 등록됨 (기대 오류: {expect["error"]})']
        msg = data.get("error", "")
        return [] if expect["error"] in msg else [f'오류 문구에 "{expect["error"]}" 없음: {msg}']
    if resp.status_code != 201:
        return [f'등록이 거절됨: {data.get("error")}']
    return errs


def main():
    rules = matching.load_rules()
    client = server.create_app(rules).test_client()
    data = json.load(open(ROOT / "samples" / "synthetic_sellers.json", encoding="utf-8"))["items"]
    fails, by_group, stored = [], defaultdict(lambda: [0, 0]), []
    for it in data:
        resp = client.post("/api/items", json=it["body"])
        js = resp.get_json()
        errs = check(it["expect"], resp, js)
        shown = None
        if resp.status_code == 201:
            body = js["item"]
            stored.append(body)
            # 서버 응답에는 태그가 없으므로 match_item으로 계산해 대조한다
            m = matching.match_item(body, PROFILES["S 단일"], rules)
            r = matching.tag_from_attributes(body["attrs"], rules)
            e = it["expect"]
            tags, added = set(m["tags"]), set(m["text_added"])
            if "has" in e and not set(e["has"]) <= tags:
                errs.append(f'tags에 없음: {sorted(set(e["has"]) - tags)} (실제 {sorted(tags)})')
            if "lacks" in e and set(e["lacks"]) & tags:
                errs.append(f'tags에 있으면 안 됨: {sorted(set(e["lacks"]) & tags)}')
            if "added" in e and set(e["added"]) != added:
                errs.append(f'text_added 기대 {sorted(e["added"])} / 실제 {sorted(added)}')
            if "fit_stated" in e and e["fit_stated"] != m["fit_stated"]:
                errs.append(f'fit_stated 기대 {e["fit_stated"]} / 실제 {m["fit_stated"]}')
            if "fibers" in e and set(e["fibers"]) != {x["fiber"] for x in r["materials"]}:
                errs.append(f'섬유 기대 {sorted(e["fibers"])} / 실제 {sorted(x["fiber"] for x in r["materials"])}')
            if "agreement" in e and abs(e["agreement"] - m["review_agreement"]) > 0.01:
                errs.append(f'일치도 기대 {e["agreement"]} / 실제 {m["review_agreement"]}')
        g = by_group[it["group"]]
        g[0] += not errs
        g[1] += 1
        if errs:
            fails.append((it["item_id"], it["scenario"], errs))
    print(f"등록 시도 {len(data)}개 / 등록됨 {len(stored)}개 / 거절됨 {len(data) - len(stored)}개\n")
    print("[시나리오 묶음별 기대 결과 일치]")
    for k, (ok, n) in by_group.items():
        print(f"  {k:<22} {ok}/{n}")
    total_ok = sum(v[0] for v in by_group.values())
    print(f"  {'합계':<22} {total_ok}/{len(data)}\n")
    if fails:
        print("[어긋난 항목]")
        for i, sc, errs in fails:
            print(f"  #{i} {sc}")
            for e in errs:
                print(f"     - {e}")
        print()
    print("[저장된 상품의 태그 분포]")
    cnt = Counter(g for b in stored for g in matching.match_item(b, PROFILES["S 단일"], rules)["tags"])
    print("  ", ", ".join(f"{g} {c}" for g, c in cnt.most_common()))
    added = sum(bool(matching.match_item(b, PROFILES['S 단일'], rules)['text_added']) for b in stored)
    print(f"   글 보충이 일어난 상품 {added}/{len(stored)}개\n")
    for name, prof in PROFILES.items():
        r = client.post("/api/recommend", json={"profile": prof, "top_n": 3, "items": stored}).get_json()
        names = {b["item_id"]: b["name"] for b in stored}
        top_items = sorted((m for m in r["items"] if m["body_fit"] is not None), key=lambda m: -m["body_fit"])[:5]
        print(f"[{name}] 상위 상품")
        for m in top_items:
            print(f"   {m['body_fit']:.3f}  #{m['item_id']} {names[m['item_id']][:30]}  {m['tags']}")
        print("   코디:", [(names[o['top']][:14], names[o['bottom']][:14], o['score']) for o in r["outfits"]])
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
