"""규칙 태깅 + LLM 보강 이중 태깅 시험.   python tools\\dual_tagging_test.py --provider gemini

흐름: 1) 규칙(matching.tag_item_full)으로 먼저 태깅 (원문 + 이미지에서 읽은 글)
      2) 점수에 쓰이는 태그가 --threshold 개 미만인 상품만 LLM에 보낸다
      3) LLM 태그는 '근거 인용이 입력 글에 실제로 있는 것'(grounded)만 받는다
         + 점수 가중치표에 있는 태그만 + 규칙이 이미 핏 태그를 붙였으면 LLM 핏 태그는 버린다(모순 방지)
         + 혼용률이 읽힌 상품의 섬유 태그(면·울·린넨 등)는 LLM이 못 붙인다 (혼용률 규칙을 우회하지 않게)
LLM에는 matching._clean_desc를 통과한 글만 보낸다 (세탁 안내표·코디 문구를 다시 살리지 않기 위해).

준비:   set GEMINI_API_KEY=...     (키는 채팅에 적지 않는다)   set PYTHONUTF8=1
입력:   samples/real_50.json, samples/real_50_vision.json (없으면 원문만 사용)
출력:   samples/dual_result.json, 터미널 요약.  --provider none 이면 LLM 대상 상품만 보여 준다.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import matching  # noqa: E402
import tagger_llm  # noqa: E402

FIT = {"슬림": "slim", "스키니": "slim", "레귤러": "regular", "루즈": "over", "오버|사이즈": "over", "오버사이즈": "over"}
MAX_CHARS = 3500


def load_items() -> list[dict]:
    items = json.loads((ROOT / "samples" / "real_50.json").read_text(encoding="utf-8"))["items"]
    vpath = ROOT / "samples" / "real_50_vision.json"
    vis = {}
    if vpath.exists():
        vis = {x["id"]: x.get("text", "") for x in json.loads(vpath.read_text(encoding="utf-8"))["items"]}
    else:
        print("(real_50_vision.json 없음: 이미지에서 읽은 글 없이 원문만 사용)")
    for it in items:
        it["full_text"] = ((it.get("text") or "") + "\n" + (vis.get(it["id"]) or "")).strip()
    return items


def known(tags: list[str], rules) -> list[str]:
    return [t for t in tags if t in rules.weights]


def fit_result(it: dict, tags: list[str], regular: bool) -> str | None:
    """핏 정답이 있는 상품만: ok / 모순 / 오답 / 못읽음."""
    if not it.get("fit"):
        return None
    want = FIT[it["fit"]]
    ft = [t for t in tags if t.startswith("fit:")]
    got = matching.fit_stated_from_tags(tags, regular)
    if got == want:
        return "ok"
    if got == "unknown":
        return "모순" if ft else "못읽음"
    return "오답"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--provider", choices=["none", "gemini", "groq"], default="none")
    ap.add_argument("--threshold", type=int, default=2, help="점수 태그가 이 개수 미만이면 LLM에 보낸다")
    ap.add_argument("--sleep", type=float, default=4.0)
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    rules = matching.load_rules()
    items = load_items()
    if args.limit:
        items = items[: args.limit]
    vocab = tagger_llm.load_vocab()
    fiber_tags = {spec["tag"] for spec in rules.materials.values() if spec.get("tag")}
    targets = []
    base = {}
    for it in items:
        r = matching.tag_item_full(it["name"], it["full_text"], rules)
        base[it["id"]] = r
        if len(known(r["tags"], rules)) < args.threshold:
            targets.append(it)
    print(f"규칙 태깅 완료. LLM 대상 {len(targets)}/{len(items)}개 (점수 태그 {args.threshold}개 미만)")
    for it in targets:
        print(f'  {it["id"]:>9} {base[it["id"]]["tags"]}  | {it["name"][:34]}')

    llm: dict[str, dict] = {}
    if args.provider != "none":
        call, model = tagger_llm.make_caller(args.provider)
        cpath = ROOT / "samples" / f"llm_cache_{args.provider}.json"
        cache = json.loads(cpath.read_text(encoding="utf-8")) if cpath.exists() else {}
        for i, it in enumerate(targets, 1):
            cleaned = matching._clean_desc(it["full_text"].lower())[:MAX_CHARS]
            before = len(cache)
            try:
                r = tagger_llm.tag_item_llm(it["name"], cleaned, it.get("cat", ""), vocab, call, cache, args.provider + model)
            except Exception as e:
                print(f"[{i}/{len(targets)}] 실패 {it['id']}: {e}", file=sys.stderr)
                continue
            llm[it["id"]] = r
            cpath.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
            print(f"[{i}/{len(targets)}] {it['id']} LLM={r['tags']} 근거있음={r['grounded']} 버림={r['dropped']}")
            if len(cache) > before:
                time.sleep(args.sleep)

    rows, added_total, rescued = [], 0, 0
    before_fit, after_fit = {}, {}
    cov = {"규칙만": [0, 0], "이중": [0, 0]}   # [1개 이상, 2개 이상]
    for it in items:
        r = base[it["id"]]
        rule_tags = list(r["tags"])
        final = list(rule_tags)
        added, skipped = [], []
        if it["id"] in llm:
            has_fit = any(t.startswith("fit:") for t in rule_tags)
            for t in llm[it["id"]]["grounded"]:
                if t in final:
                    continue
                if t not in rules.weights:
                    skipped.append((t, "가중치표에 없음"))
                elif t in fiber_tags and r["materials"]:
                    skipped.append((t, "혼용률이 읽힌 상품: 섬유 태그는 혼용률로만 정한다"))
                elif t.startswith("fit:") and has_fit:
                    skipped.append((t, "규칙이 이미 핏 태그를 붙임"))
                else:
                    final.append(t)
                    added.append(t)
            for t in llm[it["id"]]["tags"]:
                if t not in llm[it["id"]]["grounded"]:
                    skipped.append((t, "근거 인용이 원문에 없음"))
        added_total += len(added)
        k0, k1 = len(known(rule_tags, rules)), len(known(final, rules))
        cov["규칙만"][0] += k0 >= 1; cov["규칙만"][1] += k0 >= 2
        cov["이중"][0] += k1 >= 1; cov["이중"][1] += k1 >= 2
        if k0 == 0 and k1 >= 1:
            rescued += 1
        regular = r["fit_stated"] == "regular"
        b, a = fit_result(it, rule_tags, regular), fit_result(it, final, regular)
        if b:
            before_fit[it["id"]], after_fit[it["id"]] = b, a
        rows.append({"id": it["id"], "name": it["name"], "rule_tags": rule_tags, "llm_added": added,
                     "llm_skipped": skipped, "final_tags": final, "fit_truth": it.get("fit")})

    n = len(items)
    print("\n=== 요약 ===")
    for k, (a, b) in cov.items():
        print(f"{k}: 점수 태그 1개 이상 {a}/{n}, 2개 이상 {b}/{n}")
    print(f"LLM 대상 {len(targets)}개 / 호출 성공 {len(llm)}개 / 추가된 태그 {added_total}개 / 태그 0개에서 살아난 상품 {rescued}개")
    from collections import Counter
    if before_fit:
        print("핏 정답 대조 (규칙만):", dict(Counter(before_fit.values())))
        print("핏 정답 대조 (이중)  :", dict(Counter(after_fit.values())))
    print("\n[LLM이 추가한 태그: 눈으로 확인하세요]")
    for r in rows:
        if r["llm_added"]:
            print(f'  {r["id"]:>9} +{r["llm_added"]}  | {r["name"][:34]}')
    out = ROOT / "samples" / "dual_result.json"
    out.write_text(json.dumps({"coverage": cov, "rows": rows}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n저장: {out}")


if __name__ == "__main__":
    main()
