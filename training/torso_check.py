"""AI Hub '한국인 전신 및 포즈' 샘플(8명)의 몸통 라벨로 윤곽 폭과 팔 제외 효과를 검증한다.
정답 = Torso_Front 폴리곤의 폭. 같은 높이에서 (팔 안 지운 윤곽 폭), (팔 지운 윤곽 폭)과 비교한다.
사용: python torso_check.py <Sample.zip 또는 와일드카드> <출력 폴더>
정면에 가까운 카메라(어깨너비/몸 높이가 큰 사진)만 쓴다. 사람 8명이라 통계가 아니라 방향을 보는 용도다.
"""
import glob
import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageOps

from features import L, Measurer, band_width

ZIP, OUT = sys.argv[1], sys.argv[2]
if "*" in ZIP:                               # 가장 작은 zip (포즈 샘플). 한글 이름이 명령줄에서 깨져도 찾는다
    ZIP = min(glob.glob(ZIP), key=os.path.getsize)
ROWS = [0.3, 0.6, 0.9]                       # 몸통 폴리곤 높이의 30·60·90% 지점
FRONT_MIN_RATIO = 0.17                       # 어깨너비 / 몸 높이가 이보다 크면 정면에 가까운 사진


def dec(b):
    for enc in ("utf-8-sig", "cp949"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            pass
    raise ValueError("decode")


z = zipfile.ZipFile(ZIP)
names = {}
for info in z.infolist():
    if info.is_dir():
        continue
    try:
        n = info.filename.encode("cp437").decode("cp949")
    except Exception:
        n = info.filename
    names[n] = info

jpgs = sorted(n for n in names if n.endswith(".jpg") and "/F" in n.replace("\\", "/") or n.endswith(".jpg") and "/M" in n)
jpgs = [n for n in jpgs if "_cam" in n]
meas = Measurer()
rows = []
for jpg in jpgs:
    key = os.path.basename(jpg)[:-4]                                    # A_P01_0261_cam101_100f
    js = [n for n in names if n.endswith(key + "_label.json")]
    if not js:
        continue
    meta = json.loads(dec(z.read(names[js[0]])))
    hs = meta["human_size"]["size"]
    height_cm = float(hs[1])
    sex = meta["normal_info"]["성별"]
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(z.read(names[jpg]))).convert("RGB"))
    arr = np.asarray(img)
    # 몸통 정답 마스크
    gt = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(gt)
    n_poly = 0
    for seg in meta["annotations"]["segmentation_body"]:
        if seg["label"] == "Torso_Front":
            v = seg.get("points")
            if not v:                         # 가려져서 좌표가 없는 부위
                continue
            pts = list(zip(v[0::2], v[1::2]))
            if len(pts) >= 3:
                d.polygon(pts, fill=255)
                n_poly += 1
    if not n_poly:
        continue
    gtm = np.asarray(gt) > 127
    ys = np.where(gtm.any(axis=1))[0]
    if len(ys) < 50:
        continue
    top, bot = ys[0], ys[-1]
    f_raw = meas.measure_front(arr, height_cm, erase=False)
    f_er = meas.measure_front(arr, height_cm, erase=True)
    if not f_raw or not f_er:
        rows.append({"id": key, "sex": sex, "status": "관절·윤곽 실패"})
        continue
    lms = f_er["landmarks"]
    sw = abs(lms[L["L_SHOULDER"]][0] - lms[L["R_SHOULDER"]][0]) * img.width
    body_px = f_er["sil"].body_px() * (f_er["crop"][2] / f_er["crop"][4])   # 전체 사진 좌표의 몸 높이
    ratio = sw / body_px
    if ratio < FRONT_MIN_RATIO:
        rows.append({"id": key, "sex": sex, "status": "정면 아님 (%.2f)" % ratio})
        continue
    x0, y0, cw, ch, mw, mh = f_er["crop"]
    cm_per_full_px = f_er["cm_mask"] * (mw / cw)
    res = []
    for r in ROWS:
        y = int(round(top + r * (bot - top)))
        gw = []
        for yy in range(y - 2, y + 3):
            xs = np.where(gtm[yy])[0]
            if len(xs):
                gw.append(xs[-1] - xs[0] + 1)
        if not gw:
            res.append(None)
            continue
        gt_cm = float(np.median(gw)) * cm_per_full_px
        ym = int(round((y - y0) * mh / ch))
        def sw_cm(f):
            return band_width(f["sil"], min(max(ym, 0), mh - 1), 2) * f["cm_mask"]
        res.append({"r": r, "gt": gt_cm, "raw": sw_cm(f_raw), "erased": sw_cm(f_er)})
    rows.append({"id": key, "sex": sex, "status": "ok", "ratio": ratio, "height": height_cm, "res": res})

ok = [r for r in rows if r["status"] == "ok"]
lines = ["# 몸통 폭 검증 (cm). 쓴 사진 %d장 / 전체 %d장" % (len(ok), len(rows))]
other = {}
for r in rows:
    if r["status"] != "ok":
        other[r["status"].split(" (")[0]] = other.get(r["status"].split(" (")[0], 0) + 1
lines.append("제외된 사진: " + str(other))
for i, rr in enumerate(ROWS):
    g = [(r["res"][i]["gt"], r["res"][i]["raw"], r["res"][i]["erased"]) for r in ok if r["res"][i]]
    if not g:
        continue
    a = np.array(g)
    lines.append("## 몸통 폴리곤 높이 %d%% 지점 (사진 %d장)" % (int(rr * 100), len(g)))
    lines.append("  정답 폭 평균 %.1f cm" % a[:, 0].mean())
    lines.append("  팔 안 지움 - 정답: 평균 %+.1f / 평균절대 %.1f / 최대 %+.1f" % ((a[:, 1] - a[:, 0]).mean(), np.abs(a[:, 1] - a[:, 0]).mean(), (a[:, 1] - a[:, 0]).max()))
    lines.append("  팔 지움   - 정답: 평균 %+.1f / 평균절대 %.1f / 최소 %+.1f / 최대 %+.1f" % ((a[:, 2] - a[:, 0]).mean(), np.abs(a[:, 2] - a[:, 0]).mean(), (a[:, 2] - a[:, 0]).min(), (a[:, 2] - a[:, 0]).max()))
lines.append("## 사진별")
for r in ok:
    lines.append("%s %s 어깨/키=%.2f " % (r["id"], r["sex"], r["ratio"]) + " ".join(
        "[%d%% 정답 %.1f 안지움 %+.1f 지움 %+.1f]" % (int(x["r"] * 100), x["gt"], x["raw"] - x["gt"], x["erased"] - x["gt"]) if x else "[-]" for x in r["res"]))
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, "torso_check.txt"), "w", encoding="utf-8").write("\n".join(lines))
print("\n".join(lines))
