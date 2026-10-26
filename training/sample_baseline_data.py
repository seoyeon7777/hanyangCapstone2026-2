"""AI Hub '한국인 전신 형상 및 치수 측정' 샘플 zip을 읽는 도우미 (압축을 풀지 않고 zip에서 바로 읽는다)."""
import csv
import glob
import io
import os
import zipfile

import numpy as np
from PIL import Image, ImageOps

FRONT_CAMS, SIDE_CAMS = [1, 2, 3, 4], [9, 10, 11, 12]
CODES = ["01_01", "01_02", "01_03", "01_04", "01_05", "01_06"]   # 자세 '차렷' × 옷 6종
SCALE = 0.5                                                       # 사진을 절반으로 줄여서 쓴다


def _dec(b):
    for enc in ("utf-8-sig", "cp949"):
        try:
            return b.decode(enc)
        except UnicodeDecodeError:
            pass
    raise ValueError("decode")


class SampleZip:
    def __init__(self, path):
        if "*" in path:                       # 한글 이름이 명령줄에서 깨질 때를 대비해 와일드카드로 찾는다 (가장 큰 파일)
            path = max(glob.glob(path), key=lambda p: os.path.getsize(p))
        self.z = zipfile.ZipFile(path)
        self.names = {}
        for info in self.z.infolist():
            if info.is_dir():
                continue
            try:
                n = info.filename.encode("cp437").decode("cp949")
            except Exception:
                n = info.filename
            self.names[n] = info

    def gt(self, subject):
        key = [k for k in self.names if k.endswith("%s.csv" % subject)][0]
        rows = list(csv.reader(io.StringIO(_dec(self.z.read(self.names[key])))))
        vals = {}
        for name, v in zip(rows[1], rows[2]):
            try:
                vals[name] = float(v)
            except ValueError:
                vals[name] = v
        return vals

    def image(self, subject, code, cam):
        key = [k for k in self.names if k.endswith("TS_%s/Image/%s_%s_%02d.jpg" % (subject, code, subject, cam))]
        if not key:
            return None
        img = ImageOps.exif_transpose(Image.open(io.BytesIO(self.z.read(self.names[key[0]]))).convert("RGB"))
        if SCALE != 1:
            img = img.resize((int(img.width * SCALE), int(img.height * SCALE)))
        return np.asarray(img)

    def subjects(self):
        return sorted({k.split("TS_")[1].split("/")[0] for k in self.names if "/csv/" in k})


def collect(zip_path, meas, max_pairs_per_person=24, subjects=None):
    """정면·옆면 윤곽 쌍과 정답을 모은다 (CNN 점검용)."""
    sz = SampleZip(zip_path)
    out = []
    for subject in subjects or sz.subjects():
        gt = sz.gt(subject)
        height = float(gt["키"])
        pairs = []
        for code in CODES:
            fronts = {c: meas.measure_front(sz.image(subject, code, c), height) for c in FRONT_CAMS}
            sides = {c: meas.measure_side(sz.image(subject, code, c), height) for c in SIDE_CAMS}
            for fc, f in fronts.items():
                for sc, s in sides.items():
                    if f and s:
                        pairs.append({"subject": subject, "front_sil": f["sil"], "side_sil": s["sil"],
                                      "height": height, "weight": float(gt["몸무게"]), "gt": gt})
        step = max(1, len(pairs) // max_pairs_per_person)
        out.extend(pairs[::step][:max_pairs_per_person])
    return out
