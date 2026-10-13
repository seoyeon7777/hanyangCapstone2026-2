"""샘플 상품에 recommend()를 돌려 결과를 출력한다.  python demo.py"""
import json
from pathlib import Path

import matching

profile = {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False, "secondary": "N"}

rules = matching.load_rules()
for problem in matching.validate_rules(rules):
    print("규칙 경고:", problem)

items = json.loads((Path(__file__).parent / "samples" / "sample_items.json").read_text(encoding="utf-8"))
names = {it["item_id"]: it["name"] for it in items}
out = matching.recommend(profile, items, rules)

print("[상품별]")
for r in sorted(out["items"], key=lambda r: (r["excluded"], -(r["body_fit"] or 0))):
    fit = "제외" if r["excluded"] else f'{r["body_fit"]:.2f}'
    print(f'{names[r["item_id"]]:<16} {fit:>5}  핏표기={r["fit_stated"]:<8} {r["tags"]}  {r["reasons"]}')
print("\n[코디]")
for o in out["outfits"]:
    print(f'{names[o["top"]]} + {names[o["bottom"]]}  {o["score"]:.2f}  {[b["name"] for b in o["balance"]]}')
print("\n핏 표기를 못 읽은 상품:", [names[i] for i in out["fit_unknown"]])
