"""새 상품 검증 결과표.  python tools/validate_new.py
입력: samples/validation_items.json (글 설명·판매자 핏), samples/vision_new_result.json (이미지에서 읽은 글, 없으면 글 설명만 쓴다)
규칙은 고정된 버전 그대로 쓴다. 결과를 보고 규칙을 고쳤다면 고치기 전 출력을 먼저 저장해 둘 것.
핏 판정: 일치 / 오분류(다른 핏 태그가 붙음) / 미분류(핏 태그 없음). '동의어 누락'은 원문을 읽고 사람이 가려낸다.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402

rules = matching.load_rules()
items = json.load(open(ROOT / "samples" / "validation_items.json", encoding="utf-8"))["items"]
vp = ROOT / "samples" / "vision_new_result.json"
vision = {x["id"]: x for x in json.load(open(vp, encoding="utf-8"))["items"]} if vp.exists() else {}
FIT = {"슬림": "fit:slim", "레귤러": "fit:regular", "루즈": "fit:over", "오버사이즈": "fit:over"}
FIT_TAGS = {"fit:slim", "fit:regular", "fit:over"}
rows, stat, stat2 = [], {}, {}
FIT2 = {"슬림": "slim", "레귤러": "regular", "루즈": "over", "오버사이즈": "over"}
for it in items:
    v = vision.get(it["id"])
    desc = it["text"] + ("\n" + v["text"] if v else "")
    has_info = any(w in it["text"].lower() for w in ("소재", "fabric", "material", "혼용", "%", "핏", "fit"))   # 안내문만 있는 글은 글 설명으로 치지 않는다
    src = "글" if has_info else ("이미지(읽음)" if v else "이미지(미읽음)")
    r = matching.tag_item_full(it["name"], desc, rules)
    got = [t for t in r["tags"] if t in FIT_TAGS]
    want = FIT.get(it["fit"])
    if want is None:
        verdict = "정답 없음"
    elif want in got and len(got) == 1:
        verdict = "일치"
    elif not got:
        verdict = "미분류"
    else:
        verdict = "오분류"
    stat.setdefault((src, verdict), []).append(it["id"])
    fs, w2 = r["fit_stated"], FIT2.get(it["fit"])      # 레귤러를 읽은 것도 분류 성공으로 치는 판정(수정 2 이후). 위의 태그 기준 판정은 그대로 둔다.
    v2 = "정답 없음" if w2 is None else ("미분류" if fs == "unknown" else ("일치" if fs == w2 else "오분류"))
    stat2.setdefault(v2, []).append(it["id"])
    comp = ", ".join(f"{m['fiber']} {round(m['share']*100)}%({m['part']})" for m in r["materials"]) or "-"
    rows.append((it["id"], it["cat"], it["name"][:22], src, it["fit"], got or "-", verdict, r["tags"], comp, r["material_basis"]))
for x in rows:
    print(" | ".join(str(c) for c in x))
print()
for k in sorted(stat):
    print(k, len(stat[k]), stat[k])

print("\n[핏 표기 기준 판정 (fit_stated: 레귤러를 읽으면 분류 성공)]")
for k in ("일치", "오분류", "미분류", "정답 없음"):
    print(k, len(stat2.get(k, [])), stat2.get(k, []))
