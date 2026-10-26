"""웹(web/app.js)의 측정 로직을 파이썬으로 옮긴 것.
학습 전처리와 '지금 방식' 기준 오차 계산에 같이 쓴다.
계산식·상수는 web/app.js와 같게 유지할 것 (바꾸면 둘 다 바꾼다).
"""
import math
import os

import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSE_MODEL = os.environ.get(
    "POSE_MODEL", os.path.join(REPO, "android", "app", "src", "main", "assets", "models", "pose_landmarker_full.task"))
SEG_MODEL = os.environ.get("SEG_MODEL", r"D:\Dataset\Capstone\training_models\selfie_segmenter.tflite")

# ---- web/app.js와 같은 상수 ----
VALID_LIKELIHOOD = 0.5
ARM_RADIUS_RATIO = 0.03
LEG_Y_OFFSET_RATIO = 0.02
MAX_HALF_WIDTH_RATIO = 0.16
MAX_GAP_RATIO = 0.12
DEFAULT_BODY_FRACTION = 0.8
FIXED_RATIOS = {"chest": 0.29, "waist": 0.43, "hip": 0.52}

L = dict(NOSE=0, L_SHOULDER=11, R_SHOULDER=12, L_ELBOW=13, R_ELBOW=14, L_WRIST=15, R_WRIST=16,
         L_HIP=23, R_HIP=24, L_KNEE=25, R_KNEE=26, L_ANKLE=27, R_ANKLE=28)


class Silhouette:
    """가로줄마다 몸으로 고른 덩어리의 양 끝 (없으면 -1)"""

    def __init__(self, width, height, top, bottom, left, right):
        self.width, self.height, self.top, self.bottom = width, height, top, bottom
        self.left, self.right = left, right

    def body_px(self):
        return self.bottom - self.top

    def to_mask(self):
        m = np.zeros((self.height, self.width), dtype=bool)
        for y in range(self.height):
            if self.left[y] >= 0:
                m[y, self.left[y]:self.right[y] + 1] = True
        return m


class Measurer:
    def __init__(self):
        self.pose = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
            base_options=mpp.BaseOptions(model_asset_path=POSE_MODEL),
            running_mode=vision.RunningMode.IMAGE, num_poses=1,
            min_pose_detection_confidence=0.3, min_pose_presence_confidence=0.3, min_tracking_confidence=0.3))
        self.seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
            base_options=mpp.BaseOptions(model_asset_path=SEG_MODEL),
            running_mode=vision.RunningMode.IMAGE, output_confidence_masks=True, output_category_mask=False))

    # ---- 모델 호출 ----
    def landmarks(self, rgb):
        res = self.pose.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb)))
        if not res.pose_landmarks:
            return None
        return [(l.x, l.y, l.visibility if l.visibility is not None else 1.0) for l in res.pose_landmarks[0]]

    def mask(self, rgb):
        res = self.seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb)))
        masks = res.confidence_masks
        if not masks:
            return None
        m = masks[1] if len(masks) > 1 else masks[0]
        arr = np.array(m.numpy_view(), dtype=np.float32, copy=True)
        return arr.reshape(arr.shape[0], arr.shape[1])      # (높이, 너비, 1)로 나오는 경우가 있다

    # ---- 정면 ----
    def measure_front(self, rgb, height_cm):
        if rgb is None:                       # 샘플에 없는 사진
            return None
        lms = self.landmarks(rgb)
        if lms is None:
            return None
        H, W = rgb.shape[:2]
        crop = crop_to_person(lms, W, H)
        x0, y0, cw, ch = crop
        data = self.mask(rgb[y0:y0 + ch, x0:x0 + cw])
        if data is None:
            return None
        mh, mw = data.shape
        p = [((x * W - x0) / cw * mw, (y * H - y0) / ch * mh, v) for x, y, v in lms]
        axis = body_axis([((x * W - x0) / cw, (y * H - y0) / ch, v) for x, y, v in lms])
        body_rows = axis["bodyHeight"] * mh if axis and axis["bodyHeight"] > 0 else DEFAULT_BODY_FRACTION * mh
        erase_arms(data, p, body_rows)
        sil = build_silhouette(data, axis)
        if sil is None:
            return None
        cm_mask = height_cm / sil.body_px()
        ratios = pick_ratios(sil, p)
        widths = widths_at(sil, ratios, cm_mask)
        if widths is None:
            return None
        f = cw / mw
        cm_full = cm_mask / f
        pf = [(x * W, y * H, v) for x, y, v in lms]
        lengths = calc_lengths(pf, cm_full)
        if lengths is None:
            return None
        if p[L["L_HIP"]][2] >= 0.3 and p[L["R_HIP"]][2] >= 0.3:
            hip_y = (p[L["L_HIP"]][1] + p[L["R_HIP"]][1]) / 2
            leg = (sil.bottom - hip_y) * cm_mask - LEG_Y_OFFSET_RATIO * height_cm
            if leg > 0:
                lengths["leg"] = leg
        return {"sil": sil, "ratios": ratios, "widths": widths, "lengths": lengths, "cm_mask": cm_mask}

    # ---- 옆면 ----
    def measure_side(self, rgb, height_cm):
        """윤곽만 만든다. 두께는 정면에서 찾은 비율로 pair_thickness가 잰다"""
        if rgb is None:                       # 샘플에 없는 사진
            return None
        try:
            lms = self.landmarks(rgb)
        except Exception:
            lms = None
        H, W = rgb.shape[:2]
        crop = crop_to_person(lms, W, H) if lms else (0, 0, W, H)
        x0, y0, cw, ch = crop
        data = self.mask(rgb[y0:y0 + ch, x0:x0 + cw])
        if data is None:
            return None
        axis = body_axis([((x * W - x0) / cw, (y * H - y0) / ch, v) for x, y, v in lms]) if lms else None
        sil = build_silhouette(data, axis)
        if sil is None:
            return None
        return {"sil": sil, "cm_mask": height_cm / sil.body_px()}


def pair_thickness(side, ratios):
    return widths_at(side["sil"], ratios or FIXED_RATIOS, side["cm_mask"], search_hip=True)


def ellipse(width_cm, depth_cm):
    if width_cm <= 0 or depth_cm <= 0:
        return 0.0
    a, b = width_cm / 2, depth_cm / 2
    return math.pi * (3 * (a + b) - math.sqrt((3 * a + b) * (a + 3 * b)))


# ---------------------------------------------------------------------------
def crop_to_person(lms, W, H):
    vis = [(x, y) for x, y, v in lms if v >= 0.3]
    if len(vis) >= 8:
        xs, ys = [p[0] for p in vis], [p[1] for p in vis]
        ph = (max(ys) - min(ys)) * H
        x0 = max(0, math.floor(min(xs) * W - 0.3 * ph))
        x1 = min(W, math.ceil(max(xs) * W + 0.3 * ph))
        y0 = max(0, math.floor(min(ys) * H - 0.2 * ph))
        y1 = min(H, math.ceil(max(ys) * H + 0.08 * ph))
        if x1 - x0 > 32 and y1 - y0 > 32:
            return (x0, y0, x1 - x0, y1 - y0)
    return (0, 0, W, H)


def body_axis(lms):
    """어깨·엉덩이 관절의 평균 x(0~1)와 몸 키(이미지 높이 대비). lms는 (x, y, visibility), 0~1 좌표"""
    if not lms:
        return None
    vis = lambda i: lms[i][2] >= 0.3
    torso = [i for i in (L["L_SHOULDER"], L["R_SHOULDER"], L["L_HIP"], L["R_HIP"]) if vis(i)]
    if len(torso) < 2:
        return None
    x = sum(lms[i][0] for i in torso) / len(torso)
    if x < 0 or x > 1:
        return None
    body_h = -1.0
    if vis(L["NOSE"]) and vis(L["L_ANKLE"]) and vis(L["R_ANKLE"]):
        span = ((lms[L["L_ANKLE"]][1] + lms[L["R_ANKLE"]][1]) / 2 - lms[L["NOSE"]][1]) / 0.88
        if 0.1 < span <= 1.2:
            body_h = span
    return {"x": x, "bodyHeight": body_h}


def erase_arms(data, p, body_rows):
    r0 = ARM_RADIUS_RATIO * body_rows
    ok = lambda i: p[i][2] >= 0.3
    segs = []
    for s, e, w in ((L["L_SHOULDER"], L["L_ELBOW"], L["L_WRIST"]), (L["R_SHOULDER"], L["R_ELBOW"], L["R_WRIST"])):
        if ok(s) and ok(e):
            segs.append((p[s], p[e], 0.35 * r0, r0))
        if ok(e) and ok(w):
            segs.append((p[e], p[w], 0.9 * r0, 0.9 * r0))
            hand = (p[w][0] + 0.4 * (p[w][0] - p[e][0]), p[w][1] + 0.4 * (p[w][1] - p[e][1]))
            segs.append((p[w], hand, 0.9 * r0, 0.9 * r0))
    H, W = data.shape
    for a, b, r1, r2 in segs:
        r = max(r1, r2)
        x0, x1 = max(0, int(math.floor(min(a[0], b[0]) - r))), min(W - 1, int(math.ceil(max(a[0], b[0]) + r)))
        y0, y1 = max(0, int(math.floor(min(a[1], b[1]) - r))), min(H - 1, int(math.ceil(max(a[1], b[1]) + r)))
        if x1 < x0 or y1 < y0:
            continue
        dx, dy = b[0] - a[0], b[1] - a[1]
        len2 = dx * dx + dy * dy or 1.0
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        t = np.clip(((xs - a[0]) * dx + (ys - a[1]) * dy) / len2, 0, 1)
        d = np.hypot(xs - (a[0] + t * dx), ys - (a[1] + t * dy))
        rr = r1 + (r2 - r1) * t
        sub = data[y0:y1 + 1, x0:x1 + 1]
        sub[(sub >= 0.5) & (d <= rr)] = 0


def _runs(row_bool):
    """True 구간들의 (시작, 끝)"""
    if not row_bool.any():
        return []
    d = np.diff(np.concatenate(([0], row_bool.astype(np.int8), [0])))
    return list(zip(np.where(d == 1)[0], np.where(d == -1)[0] - 1))


def build_silhouette(data, axis):
    h, w = data.shape
    axis_x = axis["x"] * w if axis else w / 2
    body_rows = axis["bodyHeight"] * h if axis and axis["bodyHeight"] > 0 else DEFAULT_BODY_FRACTION * h
    lo, hi, max_gap = 0, w - 1, math.inf
    if axis:
        half = round(MAX_HALF_WIDTH_RATIO * body_rows)
        lo, hi = max(0, round(axis_x) - half), min(w - 1, round(axis_x) + half)
        max_gap = MAX_GAP_RATIO * body_rows
    left = np.full(h, -1, dtype=np.int32)
    right = np.full(h, -1, dtype=np.int32)
    top = bottom = -1
    on = data >= 0.5
    for y in range(h):
        best = None
        best_d = math.inf
        for s, e in _runs(on[y, lo:hi + 1]):
            s += lo
            e += lo
            d = s - axis_x if axis_x < s else axis_x - e if axis_x > e else 0
            if d < best_d:
                best_d, best = d, (s, e)
        if best is None or best_d > max_gap:
            continue
        left[y], right[y] = best
        if top == -1:
            top = y
        bottom = y
    if top < 0 or bottom <= top:
        return None
    return Silhouette(w, h, top, bottom, left, right)


def _row_width(sil, y):
    return 0 if sil.left[y] < 0 else int(sil.right[y] - sil.left[y] + 1)


def band_width(sil, y, k):
    v = sorted(_row_width(sil, i) for i in range(y - k, y + k + 1) if 0 <= i < sil.height)
    return v[len(v) // 2] if v else 0


def pick_ratios(sil, p):
    ok = lambda i: p[i][2] >= 0.3
    if not all(ok(L[n]) for n in ("L_SHOULDER", "R_SHOULDER", "L_HIP", "R_HIP")):
        return dict(FIXED_RATIOS)
    body_px = sil.body_px()
    s_y = (p[L["L_SHOULDER"]][1] + p[L["R_SHOULDER"]][1]) / 2
    h_y = (p[L["L_HIP"]][1] + p[L["R_HIP"]][1]) / 2
    if h_y - s_y < 0.1 * body_px:
        return dict(FIXED_RATIOS)
    k = max(1, round(0.008 * body_px))
    inside = lambda y: sil.top <= y <= sil.bottom
    waist_y, min_w = -1, math.inf
    for y in range(round(s_y + 0.5 * (h_y - s_y)), round(s_y + 0.9 * (h_y - s_y)) + 1):
        if not inside(y):
            continue
        w = band_width(sil, y, k)
        if 0 < w < min_w:
            min_w, waist_y = w, y
    hip_y, max_w = -1, 0
    for y in range(round(h_y - 0.02 * body_px), round(h_y + 0.04 * body_px) + 1):
        if not inside(y):
            continue
        w = band_width(sil, y, k)
        if w > max_w:
            max_w, hip_y = w, y
    ratio = lambda y: (y - sil.top) / body_px
    return {"chest": FIXED_RATIOS["chest"],
            "waist": ratio(waist_y) if waist_y >= 0 else FIXED_RATIOS["waist"],
            "hip": ratio(hip_y) if hip_y >= 0 else FIXED_RATIOS["hip"]}


def widths_at(sil, ratios, cm_per_px, search_hip=False):
    body_px = sil.body_px()
    k = max(1, round(0.008 * body_px))
    out = {}
    for key in ("chest", "waist", "hip"):
        y = min(sil.height - 1, max(0, sil.top + round(body_px * ratios[key])))
        w = band_width(sil, y, k)
        if key == "hip" and search_hip:
            reach = round(0.04 * body_px)
            for yy in range(max(sil.top, y - reach), min(sil.bottom, y + reach) + 1):
                w = max(w, band_width(sil, yy, k))
        if w <= 0:
            return None
        out[key] = w * cm_per_px
    return out


def calc_lengths(p, cm):
    need = [L[n] for n in ("NOSE", "L_SHOULDER", "R_SHOULDER", "L_ELBOW", "R_ELBOW", "L_WRIST", "R_WRIST",
                           "L_HIP", "R_HIP", "L_KNEE", "R_KNEE", "L_ANKLE", "R_ANKLE")]
    if any(p[i][2] < VALID_LIKELIHOOD for i in need):
        return None
    dist = lambda a, b: math.hypot(p[a][0] - p[b][0], p[a][1] - p[b][1])
    mid = lambda a, b: ((p[a][0] + p[b][0]) / 2, (p[a][1] + p[b][1]) / 2)
    sm, hm = mid(L["L_SHOULDER"], L["R_SHOULDER"]), mid(L["L_HIP"], L["R_HIP"])
    arm_l = dist(L["L_SHOULDER"], L["L_ELBOW"]) + dist(L["L_ELBOW"], L["L_WRIST"])
    arm_r = dist(L["R_SHOULDER"], L["R_ELBOW"]) + dist(L["R_ELBOW"], L["R_WRIST"])
    leg_l = dist(L["L_HIP"], L["L_KNEE"]) + dist(L["L_KNEE"], L["L_ANKLE"])
    leg_r = dist(L["R_HIP"], L["R_KNEE"]) + dist(L["R_KNEE"], L["R_ANKLE"])
    return {"shoulder": dist(L["L_SHOULDER"], L["R_SHOULDER"]) * cm,
            "arm": (arm_l + arm_r) / 2 * cm,
            "torso": math.hypot(sm[0] - hm[0], sm[1] - hm[1]) * cm,
            "leg": (leg_l + leg_r) / 2 * cm}
