"""후보 태그가 상품명에 얼마나 붙는지 점검한다.

python tools/coverage.py [상품명.csv] [candidates.json]
CSV는 category,name 두 열. 실제 상품명으로 바꿔서 다시 돌리면 된다.
"""
import csv
import json
import sys
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parent.parent
csv_path = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "samples" / "product_names.csv"
cand_path = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "data" / "candidates.json"

cands = json.loads(cand_path.read_text(encoding="utf-8"))["candidates"]
with open(csv_path, encoding="utf-8", newline="") as f:
    items = [(r["category"], r["name"]) for r in csv.DictReader(f)]


def tag(name):
    hits = {}
    for c in cands:
        for kw in c["keywords"]:
            if kw.lower() in name.lower():
                hits.setdefault(c["tag"], kw)
                break
    return hits


tagged = [(cat, name, tag(name)) for cat, name in items]
n = len(tagged)
counts = Counter(len(h) for _, _, h in tagged)
print(f"상품 {n}개, 평균 태그 {sum(len(h) for *_, h in tagged) / n:.2f}개")
print("태그 수 분포:", {k: counts[k] for k in sorted(counts)})
print()

by_tag = {c["tag"]: [] for c in cands}
for cat, name, h in tagged:
    for t, kw in h.items():
        by_tag[t].append((name, kw))

status = {c["tag"]: c["status"] for c in cands}
print(f"{'태그':<14}{'구분':<12}{'히트':>4}  예시(키워드)")
for t, hits in sorted(by_tag.items(), key=lambda x: -len(x[1])):
    ex = ", ".join(f"{nm.strip()}({kw})" for nm, kw in hits[:2])
    print(f"{t:<14}{status[t]:<12}{len(hits):>4}  {ex}")

never = [t for t, h in by_tag.items() if not h]
print("\n한 번도 안 붙은 태그:", never or "없음")
zero = [name.strip() for _, name, h in tagged if not h]
print(f"태그 0개 상품 {len(zero)}개:", zero)
