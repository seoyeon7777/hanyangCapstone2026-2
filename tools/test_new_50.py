"""새 상품(samples/new_50.json) 시험.  python tools/test_new_50.py
규칙은 수정하지 않은 상태 그대로 돌린다. 정답은 무신사 핏 항목뿐이라 핏 외 태그는 눈으로 확인한다."""
import json, sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
from collections import Counter
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching

rules = matching.load_rules()
items = json.load(open(ROOT / "samples" / "new_50.json", encoding="utf-8"))["items"]
_v = ROOT / "samples" / "new_50_vision.json"      # 있으면 상세 이미지에서 읽은 글을 원문 뒤에 붙인다
if _v.exists():
    _vis = {x["id"]: x.get("text", "") for x in json.load(open(_v, encoding="utf-8"))["items"]}
    for _it in items:
        _it["text"] = ((_it.get("text") or "") + "\n" + (_vis.get(_it["id"]) or "")).strip()
FIT = {"슬림": "slim", "스키니": "slim", "레귤러": "regular", "루즈": "over", "오버|사이즈": "over", "오버사이즈": "over"}
PROF = {"S": {"S": .8, "W": .1, "N": .1}, "W": {"S": .1, "W": .8, "N": .1}, "N": {"S": .1, "W": .1, "N": .8}, "S55/N45": {"S": .55, "W": 0, "N": .45}}
res = []
for it in items:
    r = matching.tag_item_full(it["name"], it["text"], rules)
    res.append((it, r))
    print(f'{it["id"]:>9} {it["src"]} {it["cat"]:<7} 핏정답={it["fit"] or "-":<6} 읽음={r["fit_stated"]:<8} 태그={r["tags"]}  | {it["name"][:34]}')
print("\n[핏 판정: 정답 있는 것만]")
ok = bad = unk = 0
for it, r in res:
    w = FIT.get(it["fit"]) if it["fit"] else None
    if not w: continue
    if r["fit_stated"] == "unknown": unk += 1
    elif r["fit_stated"] == w: ok += 1
    else: bad += 1; print("  오분류", it["id"], it["fit"], "->", r["fit_stated"], it["name"][:30])
print(f"일치 {ok} / 오분류 {bad} / 미분류 {unk} (정답 {ok+bad+unk}건)")
print("\n[출처별 태그 수]")
for src in ("무신사", "에이블리"):
    ns = [len(r["tags"]) for it, r in res if it["src"] == src]
    z = sum(1 for n in ns if n == 0)
    print(src, "평균", round(sum(ns)/len(ns), 2), "태그 0개", z, "/", len(ns))
print("\n[태그 분포]")
c = Counter(t for _, r in res for t in r["tags"])
print(dict(c.most_common()))
allt = set(rules.weights) if hasattr(rules, "weights") else set()
print("한 번도 안 나온 가중치 태그:", sorted(t for t in allt if t not in c))
print("\n[유형별 추천: 상위3 / 하위2 (body_fit, 근거)]")
for name, p in PROF.items():
    prof = matching.normalize_profile({"ratios": p, "primary": max(p, key=p.get), "is_mixed": name == "S55/N45"})
    sc = []
    for it, r in res:
        m = matching.match_item({"item_id": it["id"], "name": it["name"], "desc": it["text"]}, prof, rules)
        sc.append((m["body_fit"], it, m))
    known = sorted([x for x in sc if x[0] is not None], key=lambda x: -x[0])
    print(f"-- {name}: 점수 계산 {len(known)}/{len(items)}, 제외·태그부족 {len(items)-len(known)}")
    for s, it, m in known[:3]: print("  상", round(s, 3), it["id"], it["name"][:26], m["reasons"])
    for s, it, m in known[-2:]: print("  하", round(s, 3), it["id"], it["name"][:26], m["tags"])
    print("  점수 범위", round(known[-1][0], 3), "~", round(known[0][0], 3))
