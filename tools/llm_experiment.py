"""키워드 태깅 vs LLM 태깅 비교 실험 (텍스트 전용).

  python tools/llm_experiment.py                       # API 없이 키워드 기준선만
  python tools/llm_experiment.py --provider gemini     # GEMINI_API_KEY 필요
  python tools/llm_experiment.py --provider groq --sleep 10

비교 입력: 이름만 / 이름+설명 텍스트.  지표:
  - 평균 태그 수, 태그 0개 비율
  - (LLM) 근거 인용이 입력에 실제 있는 비율(grounded), 목록 밖 태그를 만든 횟수(dropped)
  - 핏 정답 대조: 무신사 판매자 핏 속성이 있는 상품만. 슬림->fit:slim, 스키니->sil:skinny,
    루즈/오버사이즈->fit:over, 레귤러->핏 태그 없음.  (정답은 판매자 입력값이라 참고용이다)
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import tagger_llm  # noqa: E402

FIT_TAGS = {"fit:slim", "fit:over", "sil:skinny"}
FIT_EXPECTED = {"슬림": {"fit:slim"}, "스키니": {"sil:skinny"}, "루즈": {"fit:over"},
                "오버사이즈": {"fit:over"}, "레귤러": set()}


def keyword_tags(text: str, vocab: dict) -> list[str]:
    t = text.lower()
    return [tag for tag, kws in vocab.items() if any(k.lower() in t for k in kws)]


def summarize(tag_lists: list[list[str]]) -> dict:
    n = len(tag_lists)
    zero = sum(1 for t in tag_lists if not t)
    return {"avg_tags": round(sum(len(t) for t in tag_lists) / n, 2), "zero": zero, "zero_pct": round(100 * zero / n)}


def fit_agreement(items: list[dict], tag_lists: list[list[str]]) -> dict:
    exact = total = reg_fp = reg_n = nonreg_hit = nonreg_n = 0
    for it, tags in zip(items, tag_lists):
        exp = FIT_EXPECTED.get(it["fit"]) if it["fit"] else None
        if exp is None:
            continue
        pred = set(tags) & FIT_TAGS
        total += 1
        exact += pred == exp
        if it["fit"] == "레귤러":
            reg_n += 1
            reg_fp += bool(pred)
        else:
            nonreg_n += 1
            nonreg_hit += bool(pred & exp)
    return {"items_with_attr": total, "exact_match": f"{exact}/{total}",
            "non_regular_found": f"{nonreg_hit}/{nonreg_n}", "regular_false_positive": f"{reg_fp}/{reg_n}"}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(ROOT / "samples" / "musinsa_96.json"))
    ap.add_argument("--provider", choices=["none", "gemini", "groq"], default="none")
    ap.add_argument("--sleep", type=float, default=4.0, help="호출 사이 대기(초). 무료 한도 보호용")
    ap.add_argument("--limit", type=int, default=0, help="앞에서 N개만 (0이면 전부)")
    args = ap.parse_args()

    raw = json.loads(Path(args.data).read_text(encoding="utf-8"))
    items = raw["items"] if isinstance(raw, dict) else raw   # {"items": [...]} 또는 [...] 둘 다 허용
    for it in items:
        it.setdefault("text", "")
        it.setdefault("cat", "")
        it.setdefault("fit", None)
    if args.limit:
        items = items[: args.limit]
    vocab = tagger_llm.load_vocab()

    report = {"n": len(items), "keyword_name_only": None, "keyword_name_text": None}
    kw_name = [keyword_tags(it["name"], vocab) for it in items]
    kw_text = [keyword_tags(f'{it["name"]} {it["text"]}', vocab) for it in items]
    report["keyword_name_only"] = {**summarize(kw_name), **fit_agreement(items, kw_name)}
    report["keyword_name_text"] = {**summarize(kw_text), **fit_agreement(items, kw_text)}

    if args.provider != "none":
        call, model = tagger_llm.make_caller(args.provider)
        cache_path = ROOT / "samples" / f"llm_cache_{args.provider}.json"
        cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
        llm_tags, grounded, tagged, dropped, failures, rows = [], 0, 0, 0, 0, []
        for i, it in enumerate(items, 1):
            before = len(cache)
            try:
                r = tagger_llm.tag_item_llm(it["name"], it["text"], it["cat"], vocab, call, cache, args.provider + model)
            except Exception as e:  # 한 건 실패가 전체를 멈추지 않게
                failures += 1
                print(f"[{i}/{len(items)}] 실패: {e}", file=sys.stderr)
                llm_tags.append([])
                continue
            llm_tags.append(r["tags"])
            grounded += len(r["grounded"])
            tagged += len(r["tags"])
            dropped += len(r["dropped"])
            rows.append({"id": it["id"], "name": it["name"], "tags": r["tags"], "grounded": r["grounded"], "dropped": r["dropped"]})
            cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
            print(f"[{i}/{len(items)}] {it['name'][:30]:<30} {r['tags']}")
            if len(cache) > before:  # 실제 호출했을 때만 쉰다
                time.sleep(args.sleep)
        report["llm_name_text"] = {"provider": args.provider, "model": model, **summarize(llm_tags),
                                   **fit_agreement(items, llm_tags), "failures": failures,
                                   "grounded_rate": f"{grounded}/{tagged}", "dropped_tags": dropped}
        out = ROOT / "samples" / f"llm_result_{args.provider}.json"
        out.write_text(json.dumps({"report": report, "rows": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n상품별 결과 저장: {out}")

    print("\n=== 요약 ===")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
