"""run_compare.py가 만든 runs 폴더의 results.json을 모아 입력 오차(noise_sd)별 평균 표를 만든다.

  python summarize_runs.py <runs 폴더>

각 칸은 시드를 평균한 점검용 사람 평균 오차(cm)이고, 괄호는 시드 사이 최대-최소(학습 때마다 생기는 흔들림)다.
"""
import collections
import glob
import json
import os
import sys

runs_dir = sys.argv[1]
by = collections.defaultdict(list)
base = {}
for p in sorted(glob.glob(os.path.join(runs_dir, "*", "results.json"))):
    r = json.load(open(p, encoding="utf-8"))
    by[(len(r["targets"]), r["noise_sd"])].append(r)
    for name, v in r["results"].items():
        base[(len(r["targets"]), name)] = v["baseline"]

names = ["정확", "가정A", "가정B"]
lines = []
for nout in sorted({k[0] for k in by}):
    targets = next(iter(by[k] for k in by if k[0] == nout))[0]["targets"]
    lines.append(f"\n## 출력 {nout}개 ({', '.join(targets)}), 점검 사람 {by[next(k for k in by if k[0] == nout)][0]['n_val_people']}명\n")
    for name in names:
        lines.append(f"### 입력 {name}\n")
        lines.append("| 학습 입력 오차 | 시드 수 | " + " | ".join(targets) + " |")
        lines.append("|---|---|" + "---|" * len(targets))
        for sdv in sorted(k[1] for k in by if k[0] == nout):
            rs = by[(nout, sdv)]
            cells = []
            for k in range(nout):
                vals = [r["results"][name]["model"][k] for r in rs]
                cells.append(f"{sum(vals) / len(vals):.2f} (±{(max(vals) - min(vals)) / 2:.2f})")
            lines.append(f"| {sdv:g} | {len(rs)} | " + " | ".join(cells) + " |")
        bl = base[(nout, name)]
        lines.append("| 기준선(+편향) | - | " + " | ".join(f"{x:.2f}" for x in bl) + " |\n")
text = "\n".join(lines)
print(text)
open(os.path.join(runs_dir, "summary.md"), "w", encoding="utf-8").write(text)
