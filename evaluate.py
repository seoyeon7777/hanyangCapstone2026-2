"""
[작업 4] 치수 구간 기준값 계산 + [작업 5] 정확도 검증 (evaluate.py)

사용법 (터미널에 입력)
  python evaluate.py sample.csv thresholds   [작업 4] 튜닝용 데이터로 기준값(33%·67%) 계산
  python evaluate.py sample.csv tune         [작업 5] 튜닝용 21명(훈련용)으로 정확도 확인 (여러 번 반복)
  python evaluate.py sample.csv test         [작업 5] 테스트용 9명으로(진짜 테스트) 최종 정확도

"""
import csv
import sys

import body_profile as P


# ─────────────────────────────────────────────
# 데이터 읽기
# ─────────────────────────────────────────────
def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def load(path, split):
    """split에 해당하는 행만 읽고, 입력 오류 행은 이유와 함께 제외한다."""
    rows, skipped = [], []
    with open(path, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            if r.get("split", "").strip() != split:
                continue
            answers = {q: r[q].strip() for q in P.SURVEY}
            meas = {k: _num(r[k]) for k in P.REQUIRED_MEASURES}
            try:
                P.validate_answers(answers)
                P.validate_measurements(meas)
            except P.InputError as e:
                skipped.append((r["id"], str(e)))
                continue
            rows.append({"id": r["id"], "answers": answers,
                         "meas": meas, "truth": r["answer"].strip().upper()})
    return rows, skipped


# ─────────────────────────────────────────────
# 예측 · 평가
# ─────────────────────────────────────────────
def predict(row, mode="combined", w_survey=P.W_SURVEY):
    s = P.survey_ratios(row["answers"])
    a = P.normalize(P.score_avatar(row["meas"]))
    if mode == "survey":
        ratios = s
    elif mode == "avatar":
        ratios = a
    else:
        ratios = P.combine(s, a, w_survey, 1 - w_survey)
    return P.decide_type(ratios)


def evaluate(rows, mode="combined", w_survey=P.W_SURVEY):
    top1 = top2 = 0
    confusion = {t: {u: 0 for u in P.TYPES} for t in P.TYPES}
    results = []
    for row in rows:
        out = predict(row, mode, w_survey)
        truth = row["truth"]
        confusion[truth][out["primary"]] += 1
        top1 += out["primary"] == truth
        top2 += truth in (out["primary"], out["secondary"])
        results.append((row, out))
    n = len(rows)
    return {"top1": top1 / n, "top2": top2 / n, "confusion": confusion,
            "results": results}


def fmt_ratios(r):
    return "  ".join(f"{t} {r[t]:.2f}" for t in P.TYPES)


# ─────────────────────────────────────────────
# 리포트
# ─────────────────────────────────────────────
def report(path, split):
    rows, skipped = load(path, split)
    title = "튜닝용(tune)" if split == "tune" else "테스트용(test) ★최종"
    print(f"===== [작업 5] {title} 정확도 검증 =====")
    for pid, why in skipped:
        print(f"  제외 {pid}: {why}")
    print(f"  검증 인원: {len(rows)}명\n")
    if not rows:
        return

    print("① 정확도")
    for mode, name in [("survey", "설문만                "),
                       ("avatar", "아바타만              "),
                       ("combined", f"합산 {P.W_SURVEY}/{P.W_AVATAR} (최종 결과)")]:
        ev = evaluate(rows, mode)
        print(f"  {name} Top-1 {ev['top1']:6.1%}   Top-2 {ev['top2']:6.1%}")

    print("\n② 설문·아바타 비중별 비교 (0.6/0.4가 적당한지 확인)")
    for w in (0.4, 0.5, 0.6, 0.7, 0.8):
        ev = evaluate(rows, "combined", w)
        mark = "  ← 현재" if abs(w - P.W_SURVEY) < 1e-9 else ""
        print(f"  설문 {w:.1f} / 아바타 {1 - w:.1f}   "
              f"Top-1 {ev['top1']:6.1%}   Top-2 {ev['top2']:6.1%}{mark}")

    e = evaluate(rows, "combined")
    print("\n③ 혼동 행렬 (합산, 행=정답 / 열=예측)")
    print("          " + "   ".join(P.TYPES))
    for t in P.TYPES:
        print(f"  정답 {t}   " + "   ".join(str(e["confusion"][t][u]) for u in P.TYPES))

    mixed = sum(out["is_mixed"] for _, out in e["results"])
    print(f"\n④ 혼합형 판정: {mixed}명 / {len(rows)}명 (MIX_GAP = {P.MIX_GAP})")

    print("\n⑤ 틀린 사례 (합산)")
    wrong = [(row, out) for row, out in e["results"] if out["primary"] != row["truth"]]
    if not wrong:
        print("  없음")
    for row, out in wrong:
        s = P.survey_ratios(row["answers"])
        a = P.normalize(P.score_avatar(row["meas"]))
        print(f"  {row['id']} 정답 {row['truth']} → 예측 {out['primary']}"
              f"{' (혼합형)' if out['is_mixed'] else ''}")
        print(f"      설문   {fmt_ratios(s)}")
        print(f"      아바타 {fmt_ratios(a)}")
        print(f"      답     " + " ".join(f"{q}={row['answers'][q]}" for q in P.SURVEY))


def suggest_thresholds(path):
    """튜닝용 데이터에서 지표별 33%, 67% 지점을 계산한다."""
    rows, _ = load(path, "tune")
    print(f"[작업 4] 튜닝용 데이터 {len(rows)}명 기준 THRESHOLDS 제안 (body_profile.py에 붙여 넣기)\n")
    print("THRESHOLDS = {")
    for name, (calc, *_) in P.INDICATORS.items():
        vals = sorted(calc(r["meas"]) for r in rows)
        q = lambda p: vals[round(p * (len(vals) - 1))]
        print(f'    "{name}": ({q(1/3):.4f}, {q(2/3):.4f}),')
    print("}")


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "sample.csv"
    cmd = sys.argv[2] if len(sys.argv) > 2 else "tune"
    if cmd == "thresholds":
        suggest_thresholds(path)
    elif cmd in ("tune", "test"):
        report(path, cmd)
    else:
        print("세 번째 인자는 tune / test / thresholds 중 하나입니다.")
