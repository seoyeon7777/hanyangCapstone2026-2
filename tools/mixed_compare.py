"""혼합형 추천 방식 비교: blend(섞은 점수 하나) vs quota(비율대로 자리 나눔).  python tools/mixed_compare.py
가상 판매자 샘플(samples/synthetic_sellers.json)에서 등록된 상품으로, 카테고리 묶음별 상위 k개를 두 방식으로 뽑아 비교한다."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402
import server  # noqa: E402

K = 8
PROFILES = {
    "S .5 / N .5": {"ratios": {"S": 0.5, "W": 0.0, "N": 0.5}, "primary": "S", "is_mixed": True, "secondary": "N"},
    "S .6 / N .4": {"ratios": {"S": 0.6, "W": 0.0, "N": 0.4}, "primary": "S", "is_mixed": True, "secondary": "N"},
    "S .45 / W .30 / N .25": {"ratios": {"S": 0.45, "W": 0.30, "N": 0.25}, "primary": "S", "is_mixed": True, "secondary": "W"},
}


def main():
    rules = matching.load_rules()
    client = server.create_app(rules).test_client()
    data = json.load(open(ROOT / "samples" / "synthetic_sellers.json", encoding="utf-8"))["items"]
    stored = [r.get_json()["item"] for r in (client.post("/api/items", json=it["body"]) for it in data) if r.status_code == 201]
    names = {b["item_id"]: b["name"] for b in stored}
    for pname, prof in PROFILES.items():
        shares = matching._type_shares(matching.normalize_profile(prof))
        print(f"=== {pname}  (자리 나눔 대상 {({t: round(v, 2) for t, v in shares.items()})}), 상의 묶음 상위 {K}개 ===")
        matched = [{**matching.match_item(b, prof, rules), "category": b["category"]} for b in stored if b["category"] == "top"]
        res = {s: matching.rank_items(matched, prof, rules, K, s) for s in ("blend", "quota", "quota_safe")}
        for s, rows in res.items():
            print(f"[{s}]")
            for r in rows:
                tags = next(m["tags"] for m in matched if m["item_id"] == r["item_id"])
                ps = {t: matching.body_fit(tags, matching._pure_profile(t), rules) for t in shares}
                print(f"  {names[r['item_id']][:26]:<28} 섞은 점수 {r['score']:.3f}  " + "  ".join(f"{t}기준 {v:.3f}" for t, v in ps.items()) + (f"  <- {r['picked_for']} 몫" if r["picked_for"] else ""))
        ids = {s: {r["item_id"] for r in rows} for s, rows in res.items()}
        print(f"  겹친 상품: blend-quota {len(ids['blend'] & ids['quota'])}/{K}, blend-quota_safe {len(ids['blend'] & ids['quota_safe'])}/{K}, quota-quota_safe {len(ids['quota'] & ids['quota_safe'])}/{K}\n")


if __name__ == "__main__":
    main()
