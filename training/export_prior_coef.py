"""AI Hub 「전신 형상 및 치수 측정」 실측 CSV(여성)로 키·몸무게(·나이) → 치수 일차식의 계수를 만들어 web/app.js에 넣을 JS 코드로 출력한다.
서비스는 여성만 받으므로 여성 데이터만 쓴다.
사용: python export_prior_coef.py <CSV들이 든 zip> [출력 파일]
계수는 표준화한 릿지 회귀를 원래 단위의 일차식(절편 + 계수×키 + 계수×몸무게 + 계수×나이)으로 풀어 쓴 것이다.
"""
import csv
import io
import json
import sys
import zipfile

import numpy as np
from sklearn.linear_model import Ridge
from sklearn.preprocessing import StandardScaler

ZIP = sys.argv[1]
OUT = sys.argv[2] if len(sys.argv) > 2 else None
TARGETS = {"chest": "젖가슴둘레", "waist": "허리둘레", "hip": "엉덩이둘레", "shoulder": "어깨사이너비", "arm": "팔길이", "leg": "샅높이"}
# 사람이 볼 만한 범위. 벗어나면 입력 오류로 보고 그 사람의 그 항목만 뺀다
RANGE = {"키": (130, 200), "몸무게": (30, 150), "나이": (18, 90),
         "젖가슴둘레": (60, 140), "허리둘레": (45, 140), "엉덩이둘레": (70, 140),
         "어깨사이너비": (28, 55), "팔길이": (40, 70), "샅높이": (55, 95)}
VARIANTS = {"hw": ["키", "몸무게"], "hwa": ["키", "몸무게", "나이"]}


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
    people.append(d)


def ok(d, k):
    v = d.get(k)
    return isinstance(v, float) and RANGE[k][0] <= v <= RANGE[k][1]


women = [d for d in people if d.get("성별") == "F" and ok(d, "키") and ok(d, "몸무게") and ok(d, "나이")]
coef = {}
for vname, cols in VARIANTS.items():
    coef[vname] = {}
    for key, name in TARGETS.items():
        sel = [d for d in women if ok(d, name)]
        X = np.array([[d[c] for c in cols] for d in sel])
        y = np.array([d[name] for d in sel])
        sc = StandardScaler().fit(X)
        m = Ridge(alpha=1.0).fit(sc.transform(X), y)
        c = m.coef_ / sc.scale_
        b0 = m.intercept_ - float(np.sum(c * sc.mean_))
        coef[vname][key] = [round(float(b0), 4)] + [round(float(x), 4) for x in c]

# 교차검증 평균 절대 오차 (화면에 보여 줄 설명용)
from sklearn.model_selection import KFold
from sklearn.pipeline import make_pipeline
mae = {}
for key, name in TARGETS.items():
    sel = [d for d in women if ok(d, name)]
    y = np.array([d[name] for d in sel])
    X = np.array([[d[c] for c in VARIANTS["hwa"]] for d in sel])
    err = np.zeros(len(y))
    for tr, te in KFold(5, shuffle=True, random_state=0).split(X):
        mdl = make_pipeline(StandardScaler(), Ridge(alpha=1.0)).fit(X[tr], y[tr])
        err[te] = np.abs(mdl.predict(X[te]) - y[te])
    mae[key] = round(float(err.mean()), 1)

js = ("// 키·몸무게(·나이) → 치수 일차식 (여성 %d명, AI Hub 「한국인 전신 형상 및 치수 측정」 실측으로 맞춤: training/export_prior_coef.py)\n"
      "// 값 = [절편, 키 계수, 몸무게 계수(, 나이 계수)]. 정답 정의는 사이즈코리아 표준 항목(젖가슴둘레·허리둘레(허리옆점)·엉덩이둘레·어깨사이너비·팔길이·샅높이)\n"
      "const PRIOR_COEF = %s;\n"
      "// 5겹 교차검증 평균 절대 오차(cm, 키+몸무게+나이)\n"
      "const PRIOR_MAE = %s;\n") % (len(women), json.dumps(coef, ensure_ascii=False), json.dumps(mae, ensure_ascii=False))
print(js)
if OUT:
    open(OUT, "w", encoding="utf-8").write(js)
