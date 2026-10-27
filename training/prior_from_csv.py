"""AI Hub 「전신 형상 및 치수 측정」의 사람별 실측 CSV(1,000명)로, 사진 없이 키·몸무게(·성별·나이)만으로
치수를 얼마나 맞힐 수 있는지 잰다. 사진 모델이 넘어야 할 기준선이자, 사진 결과가 이상할 때 쓸 안전장치용 기준이다.
사용: python prior_from_csv.py <CSV들이 든 zip(예: download (3).tar에서 꺼낸 것)> <출력 폴더>
항목 이름은 CSV 헤더의 한글 이름을 그대로 쓴다. 입력 오류(예: 몸무게 6kg, 가슴둘레 8cm)는 사람이 볼 만한 범위 밖이면 빼고 센다.
"""
import csv
import io
import os
import sys
import zipfile

import numpy as np
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import Ridge
from sklearn.model_selection import KFold
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline

ZIP, OUT = sys.argv[1], sys.argv[2]
TARGETS = ["젖가슴둘레", "허리둘레", "배꼽수준허리둘레", "엉덩이둘레", "어깨사이너비", "팔길이", "샅높이"]
# 사람이 볼 만한 범위. 벗어나면 입력 오류로 보고 그 사람의 그 항목만 뺀다
RANGE = {"키": (130, 200), "몸무게": (30, 150), "나이": (18, 90),
         "젖가슴둘레": (60, 140), "허리둘레": (45, 140), "배꼽수준허리둘레": (50, 150), "엉덩이둘레": (70, 140),
         "어깨사이너비": (28, 55), "팔길이": (40, 70), "샅높이": (55, 95)}
VARIANTS = {"키+몸무게": ["키", "몸무게"],
            "키+몸무게+성별": ["키", "몸무게", "성별"],
            "키+몸무게+성별+나이": ["키", "몸무게", "성별", "나이"]}


def dec(b):
    for enc in ("utf-8-sig", "cp949"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            pass
    raise ValueError("decode")


z = zipfile.ZipFile(ZIP)
people = []
for n in z.namelist():
    if not n.endswith(".csv"):
        continue
    rows = list(csv.reader(io.StringIO(dec(z.read(n)))))
    if len(rows) < 3:
        continue
    d = {}
    for k, v in zip(rows[1], rows[2]):
        try:
            d[k] = float(v)
        except ValueError:
            d[k] = v
    d["성별"] = 1.0 if d.get("성별") == "M" else 0.0
    people.append(d)
print("사람 수:", len(people))

# 입력 변수(키·몸무게·나이)가 범위를 벗어난 사람은 통째로 뺀다
def ok(d, k):
    v = d.get(k)
    return isinstance(v, float) and RANGE[k][0] <= v <= RANGE[k][1]

base = [d for d in people if ok(d, "키") and ok(d, "몸무게") and ok(d, "나이")]
print("입력(키·몸무게·나이)이 정상인 사람:", len(base), "(제외 %d명)" % (len(people) - len(base)))

lines = ["# 키·몸무게(·성별·나이)로 치수 예측 — 5겹 교차검증, 평균절대오차(cm), 정답=실측"]
lines.append("사람 %d명 (남 %d / 여 %d), 입력 오류 제외 후" % (len(base), sum(d["성별"] for d in base), len(base) - sum(d["성별"] for d in base)))
rows_out = []
for t in TARGETS:
    sel = [d for d in base if ok(d, t)]
    dropped = len(base) - len(sel)
    y = np.array([d[t] for d in sel])
    sex = np.array([d["성별"] for d in sel])
    res = {"항목": t, "사람": len(sel), "입력오류로 제외": dropped, "중앙값": float(np.median(y)), "표준편차": float(y.std())}
    # 기준선: 성별별 중앙값
    kf = KFold(5, shuffle=True, random_state=0)
    base_err = np.zeros(len(y))
    for tr, te in kf.split(y):
        for s in (0.0, 1.0):
            m = sex[tr] == s
            base_err[te[sex[te] == s]] = np.abs(y[te][sex[te] == s] - np.median(y[tr][m]))
    res["성별별 중앙값만 쓸 때"] = float(base_err.mean())
    for vname, cols in VARIANTS.items():
        X = np.array([[d[c] for c in cols] for d in sel])
        for mname, mk in (("릿지", lambda: make_pipeline(StandardScaler(), Ridge(alpha=1.0))),
                          ("부스팅", lambda: HistGradientBoostingRegressor(max_depth=3, learning_rate=0.07, max_iter=150, random_state=0))):
            pred = np.zeros(len(y))
            for tr, te in kf.split(X):
                mdl = mk()
                mdl.fit(X[tr], y[tr])
                pred[te] = mdl.predict(X[te])
            err = np.abs(pred - y)
            res["%s/%s" % (vname, mname)] = float(err.mean())
            if vname == "키+몸무게+성별+나이" and mname == "릿지":
                res["남 오차"] = float(err[sex == 1].mean())
                res["여 오차"] = float(err[sex == 0].mean())
                res["오차 90%이내"] = float(np.percentile(err, 90))
    rows_out.append(res)

for r in rows_out:
    lines.append("## %s (사람 %d, 입력오류 제외 %d) 중앙값 %.1f, 표준편차 %.1f" % (r["항목"], r["사람"], r["입력오류로 제외"], r["중앙값"], r["표준편차"]))
    lines.append("  성별별 중앙값만: %.1f" % r["성별별 중앙값만 쓸 때"])
    for vname in VARIANTS:
        lines.append("  %-14s 릿지 %.1f / 부스팅 %.1f" % (vname, r["%s/릿지" % vname], r["%s/부스팅" % vname]))
    lines.append("  (키+몸무게+성별+나이 릿지) 남 %.1f, 여 %.1f, 90%%의 사람은 오차 %.1f 이내" % (r["남 오차"], r["여 오차"], r["오차 90%이내"]))
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, "prior_from_csv.txt"), "w", encoding="utf-8").write("\n".join(lines))
print("\n".join(lines))
