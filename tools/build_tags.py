"""데모 상품 36개의 태그·혼용률을 다시 만들어 samples/demo_tags.json 에 저장한다.  python tools/build_tags.py
설명글: 글로 된 상품은 samples/musinsa_96.json 의 text, 이미지뿐인 상품은 samples/vision_result.json 의 text."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402

S = ROOT / "samples"
load = lambda n: json.load(open(S / n, encoding="utf-8"))
items = load("demo_items.json")
items = items["items"] if isinstance(items, dict) else items
text = {x["id"]: x.get("text", "") for x in load("musinsa_96.json")["items"]}
vision = {x["id"]: x.get("text", "") for x in load("vision_result.json")["items"]}
rules = matching.load_rules()
out = []
for it in items:
    desc = (text.get(it["id"]) or "") + "\n" + (vision.get(it["id"]) or "")
    r = matching.tag_item_full(it["name"], desc, rules)
    out.append({"product_id": it["id"], "name": it["name"], "cat": it["cat"], "tags": r["tags"],
                "materials": r["materials"], "material_basis": r["material_basis"], "source": it["source"]})
json.dump(out, open(S / "demo_tags.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
with_comp = [o for o in out if o["materials"]]
print(f"{len(out)}개 저장. 혼용률이 읽힌 상품 {len(with_comp)}개, 태그 없음 {sum(not o['tags'] for o in out)}개")
for o in with_comp:
    print(o["name"][:28], {m["fiber"]: m["share"] for m in o["materials"]}, o["tags"])
