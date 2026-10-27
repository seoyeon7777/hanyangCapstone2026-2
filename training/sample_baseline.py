"""AI Hub '한국인 전신 형상 및 치수 측정' 샘플로 지금 측정 방식의 기준 오차를 잰다.
사용: python sample_baseline.py <New_Sample.zip> <출력 폴더>
정면 카메라 1~4번 × 옆면 9~12번, 자세 '차렷', 옷 6종을 짝지어 둘레를 계산하고 CSV의 실측과 비교한다.
사람 2명뿐이라 통계가 아니라 '코드가 맞게 도는지'와 방향을 보는 용도다.
"""
import csv
import os
import sys

import numpy as np

from features import CHEST_ELLIPSE_FACTOR, Measurer, ellipse, pair_thickness
from sample_baseline_data import CODES, FRONT_CAMS, SIDE_CAMS, SampleZip

ZIP, OUT = sys.argv[1], sys.argv[2]
GT_NAMES = {"chest": "젖가슴둘레", "waist": "허리둘레", "hip": "엉덩이둘레",
            "arm": "팔길이", "shoulder": "어깨사이너비", "leg": "샅높이"}

sz = SampleZip(ZIP)
m = Measurer()
rows = []
for subject in sz.subjects():
    gt = sz.gt(subject)
    height = float(gt["키"])
    for code in CODES:
        fronts = {c: m.measure_front(sz.image(subject, code, c), height) for c in FRONT_CAMS}
        sides = {c: m.measure_side(sz.image(subject, code, c), height) for c in SIDE_CAMS}
        fronts = {c: r for c, r in fronts.items() if r}
        sides = {c: r for c, r in sides.items() if r}
        for fc, f in fronts.items():
            for sc, s in sides.items():
                t = pair_thickness(s, f["ratios"])
                if not t:
                    continue
                est = {k: ellipse(f["widths"][k], t[k]) for k in ("chest", "waist", "hip")}
                est["chest"] *= CHEST_ELLIPSE_FACTOR
                est.update(arm=f["lengths"]["arm"], shoulder=f["lengths"]["shoulder"], leg=f["lengths"]["leg"])
                row = {"subject": subject, "code": code, "front": fc, "side": sc}
                for k in GT_NAMES:
                    row["est_" + k] = est[k]
                    row["gt_" + k] = gt[GT_NAMES[k]]
                for k in ("chest", "waist", "hip"):
                    row["w_" + k] = f["widths"][k]
                    row["d_" + k] = t[k]
                rows.append(row)
        print(subject, code, "정면 %d장, 옆면 %d장 성공" % (len(fronts), len(sides)), flush=True)

os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, "baseline_rows.csv"), "w", newline="", encoding="utf-8") as fh:
    w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)

lines = ["# 지금 방식의 오차 (est - gt, cm). 사진 쌍 %d개" % len(rows)]
for subject in sz.subjects() + ["전체"]:
    sel = [r for r in rows if subject == "전체" or r["subject"] == subject]
    if not sel:
        continue
    lines.append("## %s (쌍 %d)" % (subject, len(sel)))
    for k, nm in GT_NAMES.items():
        d = np.array([r["est_" + k] - r["gt_" + k] for r in sel])
        gt_txt = "%6.1f" % sel[0]["gt_" + k] if subject != "전체" else "   -  "
        lines.append("%-8s %-8s 실측=%s 추정평균=%6.1f 편향=%+6.1f 평균절대오차=%5.1f 표준편차=%4.1f" % (
            k, nm, gt_txt, np.mean([r["est_" + k] for r in sel]), d.mean(), np.abs(d).mean(), d.std()))
open(os.path.join(OUT, "baseline_summary.txt"), "w", encoding="utf-8").write("\n".join(lines))
print("\n".join(lines))
