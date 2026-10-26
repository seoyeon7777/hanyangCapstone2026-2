// 신체 치수 측정 웹 테스트 버전 (사진 등록 전용).
//   키 입력 → 정면·옆면 사진 선택 → 길이·둘레 계산 → 결과
// 실시간 카메라 촬영은 없앴다. 사진은 사용자가 안내대로 찍어서 올리고, 올린 사진은 분석 뒤 가볍게 점검해서 경고만 보여 준다.
// 계산식은 BodyMeasurementCalculator / CircumferenceCalculator와 같고, 윤곽·팔 제외·허리/골반 탐색은 training/features.py와 같다.

import {
  FilesetResolver,
  PoseLandmarker,
  ImageSegmenter,
} from "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.21/vision_bundle.mjs";

const WASM_URL = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.21/wasm";
const POSE_MODEL =
  "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task";
const SEG_MODEL =
  "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite";

const VALID_LIKELIHOOD = 0.5;       // 치수 계산용 (앱과 같음)

// MediaPipe 관절 번호 (앱의 PoseLandmark와 같다)
const L = {
  NOSE: 0, L_SHOULDER: 11, R_SHOULDER: 12, L_ELBOW: 13, R_ELBOW: 14,
  L_WRIST: 15, R_WRIST: 16, L_HIP: 23, R_HIP: 24, L_KNEE: 25, R_KNEE: 26,
  L_ANKLE: 27, R_ANKLE: 28,
  L_HEEL: 29, R_HEEL: 30, L_FOOT: 31, R_FOOT: 32,
};

// ---- 화면 요소 -------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const screens = { setup: $("screen-setup"), result: $("screen-result") };

function show(name) {
  for (const [k, el] of Object.entries(screens)) el.classList.toggle("hidden", k !== name);
}

// ---- 상태 ------------------------------------------------------------------
let poseLandmarker = null;
let segmenter = null;
let modelsReady = false;
let userHeightCm = 0;

let frontCanvas = null;
let sideCanvas = null;
let frontResult = null;      // 길이
let frontWidths = null;
let sideWidths = null;
let frontDbg = "";           // 확인용 윤곽·측정 위치 그림
let sideDbg = "";
let photoWarns = [];         // 사진 점검 경고 (결과 화면에 보여 준다)

// ---- 모델 불러오기 ---------------------------------------------------------
async function loadModels() {
  const vision = await FilesetResolver.forVisionTasks(WASM_URL);
  poseLandmarker = await PoseLandmarker.createFromOptions(vision, {
    baseOptions: { modelAssetPath: POSE_MODEL, delegate: "CPU" },
    runningMode: "IMAGE",
    numPoses: 1,
    minPoseDetectionConfidence: 0.3,
    minPosePresenceConfidence: 0.3,
    minTrackingConfidence: 0.3,
  });
  segmenter = await ImageSegmenter.createFromOptions(vision, {
    baseOptions: { modelAssetPath: SEG_MODEL, delegate: "CPU" },
    runningMode: "IMAGE",
    outputConfidenceMasks: true,
    outputCategoryMask: false,
  });
}

loadModels()
  .then(() => {
    modelsReady = true;
    $("setup-msg").textContent = "";
    updateAnalyzeButton();
  })
  .catch((e) => {
    console.error(e);
    $("setup-msg").textContent = "모델을 불러오지 못했어요. 인터넷 연결을 확인하고 새로고침하세요. (" + e.message + ")";
  });
$("setup-msg").textContent = "모델을 불러오는 중이에요…";

const nextFrame = () => new Promise((r) => setTimeout(r, 30));

function setBusy(on, text) {
  $("busy").classList.toggle("hidden", !on);
  if (text) $("busy-text").textContent = text;
}

// ---- 계산: BodyMeasurementCalculator --------------------------------------
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

function calcLengths(p, heightCm, cmPerPx = 0) {
  const need = [L.NOSE, L.L_SHOULDER, L.R_SHOULDER, L.L_ELBOW, L.R_ELBOW, L.L_WRIST, L.R_WRIST,
    L.L_HIP, L.R_HIP, L.L_KNEE, L.R_KNEE, L.L_ANKLE, L.R_ANKLE];
  if (need.some((i) => !p[i] || p[i].v < VALID_LIKELIHOOD)) return null;

  const ankleY = (p[L.L_ANKLE].y + p[L.R_ANKLE].y) / 2;
  // 머리 꼭대기 landmark가 없어서 코 위쪽을 어깨너비*0.35로 추정
  const headTopY = p[L.NOSE].y - dist(p[L.L_SHOULDER], p[L.R_SHOULDER]) * 0.35;
  const bodyPx = Math.abs(ankleY - headTopY);
  if (bodyPx <= 0) return null;
  // 윤곽에서 구한 스케일을 받으면 폭과 같은 기준을 쓴다 (머리 꼭대기 추정 오차 제거)
  const cm = cmPerPx > 0 ? cmPerPx : heightCm / bodyPx;

  const mid =(a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
  return {
    shoulderWidth: dist(p[L.L_SHOULDER], p[L.R_SHOULDER]) * cm,
    leftArmLength: (dist(p[L.L_SHOULDER], p[L.L_ELBOW]) + dist(p[L.L_ELBOW], p[L.L_WRIST])) * cm,
    rightArmLength: (dist(p[L.R_SHOULDER], p[L.R_ELBOW]) + dist(p[L.R_ELBOW], p[L.R_WRIST])) * cm,
    torsoLength: dist(mid(p[L.L_SHOULDER], p[L.R_SHOULDER]), mid(p[L.L_HIP], p[L.R_HIP])) * cm,
    leftLegLength: (dist(p[L.L_HIP], p[L.L_KNEE]) + dist(p[L.L_KNEE], p[L.L_ANKLE])) * cm,
    rightLegLength: (dist(p[L.R_HIP], p[L.R_KNEE]) + dist(p[L.R_KNEE], p[L.R_ANKLE])) * cm,
  };
}

// ---- 몸의 중심선 (앱의 BodyShapeFeatures.bodyAxis) ---------------------------
// 마스크에는 배경 물건(선반·침대·옷더미)도 사람처럼 잡힌다. 어깨·엉덩이 관절의 평균 x(0~1)와
// 몸 키(코~발목 ÷ 0.88, 이미지 높이 대비)를 구해서 그 중심선이 지나는 덩어리를 몸으로 고른다.
function bodyAxis(lms) {
  if (!lms) return null;
  const vis = (i) => lms[i] && (lms[i].visibility ?? 1) >= 0.3;
  const torso = [L.L_SHOULDER, L.R_SHOULDER, L.L_HIP, L.R_HIP].filter(vis);
  if (torso.length < 2) return null;
  const x = torso.reduce((sum, i) => sum + lms[i].x, 0) / torso.length;
  if (x < 0 || x > 1) return null;
  let bodyHeight = -1;
  if (vis(L.NOSE) && vis(L.L_ANKLE) && vis(L.R_ANKLE)) {
    const span = ((lms[L.L_ANKLE].y + lms[L.R_ANKLE].y) / 2 - lms[L.NOSE].y) / 0.88;
    if (span > 0.1 && span <= 1.2) bodyHeight = span;
  }
  return { x, bodyHeight };
}

// 앱 SilhouetteBuilder와 같은 값
const MAX_HALF_WIDTH_RATIO = 0.16; // 중심선 좌우로 몸 키의 이 비율까지만 몸으로 본다 (160cm 기준 ±26cm)
const MAX_GAP_RATIO = 0.12;        // 중심선에서 이보다 먼 덩어리는 배경 물건 (160cm 기준 19cm)
const DEFAULT_BODY_FRACTION = 0.8; // 몸 키를 모를 때: 화면 높이의 이 비율

// 마스크 → 가로줄마다 몸으로 고른 덩어리의 양 끝 (없으면 -1)
function buildSilhouette(data, width, height, axis) {
  const axisX = axis ? axis.x * width : width / 2;
  const bodyRows = axis && axis.bodyHeight > 0 ? axis.bodyHeight * height : DEFAULT_BODY_FRACTION * height;
  let from = 0, to = width - 1, maxGap = Infinity;
  if (axis) {
    const half = Math.round(MAX_HALF_WIDTH_RATIO * bodyRows);
    from = Math.max(0, Math.round(axisX) - half);
    to = Math.min(width - 1, Math.round(axisX) + half);
    maxGap = MAX_GAP_RATIO * bodyRows;
  }
  const left = new Int32Array(height).fill(-1);
  const right = new Int32Array(height).fill(-1);
  let top = -1, bottom = -1;
  for (let y = 0; y < height; y++) {
    let bestStart = -1, bestEnd = -1, curStart = -1, bestDist = Infinity;
    for (let x = from; x <= to + 1; x++) {
      const isPerson = x <= to && data[y * width + x] >= 0.5;
      if (isPerson && curStart === -1) curStart = x;
      if (!isPerson && curStart !== -1) {
        const end = x - 1;
        const d = axisX < curStart ? curStart - axisX : axisX > end ? axisX - end : 0;
        if (d < bestDist) { bestDist = d; bestStart = curStart; bestEnd = end; }
        curStart = -1;
      }
    }
    if (bestDist > maxGap) { bestStart = -1; bestEnd = -1; }
    left[y] = bestStart;
    right[y] = bestEnd;
    if (bestStart >= 0) {
      if (top === -1) top = y;
      bottom = y;
    }
  }
  if (top < 0 || bottom <= top) return null;
  return { width, height, top, bottom, left, right };
}

// ---- 개선된 측정 -----------------------------------------------------------
// 1) 관절로 사람 영역을 잘라 확대한 뒤 윤곽을 딴다 (사람이 작은 사진도 윤곽이 거칠어지지 않게)
// 2) 정면은 팔 부분을 윤곽에서 지운다 (팔이 몸통 폭에 합쳐지는 문제)
// 3) 허리는 몸통에서 가장 좁은 곳, 골반은 엉덩이 관절 근처에서 가장 넓은 곳을 찾는다 (고정 비율 대신)
// 4) 길이와 폭 모두 윤곽의 머리 꼭대기~발바닥으로 한 가지 스케일을 쓴다
const ARM_RADIUS_RATIO = 0.03;   // 팔 반쪽 굵기 / 몸 키
const LEG_Y_OFFSET_RATIO = 0.02; // 엉덩이 관절보다 Y존이 조금 아래라서 키의 이만큼을 뺀다 (샘플 1명 기준, 더 모아서 맞출 것)
const FIXED_RATIOS = { chest: 0.29, waist: 0.43, hip: 0.52 }; // 관절을 못 찾을 때 (옛 방식)

function detectPose(canvas) {
  const res = poseLandmarker.detect(canvas);
  return res.landmarks && res.landmarks[0] ? res.landmarks[0] : null;
}

function cropToPerson(lms, W, H) {
  if (lms) {
    let minX = 1, minY = 1, maxX = 0, maxY = 0, n = 0;
    for (const l of lms) {
      if ((l.visibility ?? 1) < 0.3) continue;
      n++;
      minX = Math.min(minX, l.x); maxX = Math.max(maxX, l.x);
      minY = Math.min(minY, l.y); maxY = Math.max(maxY, l.y);
    }
    if (n >= 8) {
      const ph = (maxY - minY) * H;
      const x0 = Math.max(0, Math.floor(minX * W - 0.3 * ph));
      const x1 = Math.min(W, Math.ceil(maxX * W + 0.3 * ph));
      const y0 = Math.max(0, Math.floor(minY * H - 0.2 * ph));
      const y1 = Math.min(H, Math.ceil(maxY * H + 0.08 * ph));
      if (x1 - x0 > 32 && y1 - y0 > 32) return { x0, y0, w: x1 - x0, h: y1 - y0 };
    }
  }
  return { x0: 0, y0: 0, w: W, h: H };
}

function getMask(canvas) {
  const result = segmenter.segment(canvas);
  try {
    const masks = result.confidenceMasks;
    if (!masks || !masks.length) return null;
    const m = masks.length > 1 ? masks[1] : masks[0];
    return { data: new Float32Array(m.getAsFloat32Array()), width: m.width, height: m.height };
  } finally {
    result.close ? result.close() : (result.confidenceMasks || []).forEach((m) => m.close && m.close());
  }
}

// 사람 영역을 잘라 윤곽을 딴다. 관절·중심선도 잘라낸 좌표로 바꿔 돌려준다
function segmentCrop(canvas, lms) {
  const W = canvas.width, H = canvas.height;
  const crop = cropToPerson(lms, W, H);
  const cc = document.createElement("canvas");
  cc.width = crop.w;
  cc.height = crop.h;
  cc.getContext("2d").drawImage(canvas, crop.x0, crop.y0, crop.w, crop.h, 0, 0, crop.w, crop.h);
  const mask = getMask(cc);
  if (!mask) return null;
  const lmsC = lms
    ? lms.map((l) => ({ x: (l.x * W - crop.x0) / crop.w, y: (l.y * H - crop.y0) / crop.h, visibility: l.visibility }))
    : null;
  const p = lmsC ? lmsC.map((l) => ({ x: l.x * mask.width, y: l.y * mask.height, v: l.visibility ?? 1 })) : null;
  return { mask, crop, axis: bodyAxis(lmsC), p, f: crop.w / mask.width };
}

// 팔 모양(관절을 잇는 선 + 굵기)에 들어오는 윤곽 픽셀을 지운다. 어깨 쪽은 굵기를 줄여 몸통을 덜 깎는다
function eraseArms(data, W, H, p, bodyRows) {
  const r0 = ARM_RADIUS_RATIO * bodyRows;
  const ok = (i) => p && p[i] && p[i].v >= 0.3;
  const segs = [];
  for (const [s, e, w] of [[L.L_SHOULDER, L.L_ELBOW, L.L_WRIST], [L.R_SHOULDER, L.R_ELBOW, L.R_WRIST]]) {
    if (ok(s) && ok(e)) segs.push({ a: p[s], b: p[e], r1: 0.35 * r0, r2: r0 });
    if (ok(e) && ok(w)) {
      segs.push({ a: p[e], b: p[w], r1: 0.9 * r0, r2: 0.9 * r0 });
      const hand = { x: p[w].x + 0.4 * (p[w].x - p[e].x), y: p[w].y + 0.4 * (p[w].y - p[e].y) };
      segs.push({ a: p[w], b: hand, r1: 0.9 * r0, r2: 0.9 * r0 });
    }
  }
  for (const sg of segs) {
    const r = Math.max(sg.r1, sg.r2);
    const x0 = Math.max(0, Math.floor(Math.min(sg.a.x, sg.b.x) - r));
    const x1 = Math.min(W - 1, Math.ceil(Math.max(sg.a.x, sg.b.x) + r));
    const y0 = Math.max(0, Math.floor(Math.min(sg.a.y, sg.b.y) - r));
    const y1 = Math.min(H - 1, Math.ceil(Math.max(sg.a.y, sg.b.y) + r));
    const dx = sg.b.x - sg.a.x, dy = sg.b.y - sg.a.y;
    const len2 = dx * dx + dy * dy || 1;
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        if (data[y * W + x] < 0.5) continue;
        const t = Math.min(1, Math.max(0, ((x - sg.a.x) * dx + (y - sg.a.y) * dy) / len2));
        const rr = sg.r1 + (sg.r2 - sg.r1) * t;
        if (Math.hypot(x - (sg.a.x + t * dx), y - (sg.a.y + t * dy)) <= rr) data[y * W + x] = 0;
      }
    }
  }
}

const rowWidthAt = (sil, y) => (sil.left[y] < 0 ? 0 : sil.right[y] - sil.left[y] + 1);

// 몇 줄의 중앙값 (윤곽이 한 줄 튀는 것 방지)
function bandWidth(sil, y, k) {
  const v = [];
  for (let i = y - k; i <= y + k; i++) if (i >= 0 && i < sil.height) v.push(rowWidthAt(sil, i));
  v.sort((a, b) => a - b);
  return v[v.length >> 1] || 0;
}

// 정면 윤곽과 관절로 허리·골반 위치(윤곽 위쪽부터 몸 키 대비 비율)를 찾는다
function pickRatios(sil, p) {
  const ok = (i) => p && p[i] && p[i].v >= 0.3;
  if (!(ok(L.L_SHOULDER) && ok(L.R_SHOULDER) && ok(L.L_HIP) && ok(L.R_HIP))) return { ...FIXED_RATIOS };
  const bodyPx = sil.bottom - sil.top;
  const sY = (p[L.L_SHOULDER].y + p[L.R_SHOULDER].y) / 2;
  const hY = (p[L.L_HIP].y + p[L.R_HIP].y) / 2;
  if (hY - sY < 0.1 * bodyPx) return { ...FIXED_RATIOS };
  const k = Math.max(1, Math.round(0.008 * bodyPx));
  const inside = (y) => y >= sil.top && y <= sil.bottom;

  let waistY = -1, minW = Infinity;
  for (let y = Math.round(sY + 0.5 * (hY - sY)); y <= Math.round(sY + 0.9 * (hY - sY)); y++) {
    if (!inside(y)) continue;
    const w = bandWidth(sil, y, k);
    if (w > 0 && w < minW) { minW = w; waistY = y; }
  }
  let hipY = -1, maxW = 0;
  for (let y = Math.round(hY - 0.02 * bodyPx); y <= Math.round(hY + 0.04 * bodyPx); y++) {
    if (!inside(y)) continue;
    const w = bandWidth(sil, y, k);
    if (w > maxW) { maxW = w; hipY = y; }
  }
  const ratio = (y) => (y - sil.top) / bodyPx;
  return {
    chest: FIXED_RATIOS.chest,
    waist: waistY >= 0 ? ratio(waistY) : FIXED_RATIOS.waist,
    hip: hipY >= 0 ? ratio(hipY) : FIXED_RATIOS.hip,
  };
}

// searchHip: 옆면 골반은 같은 높이 근처에서 가장 두꺼운 곳(엉덩이)을 찾는다. 정면 폭이 가장 넓은 높이와 다를 수 있다
function widthsAt(sil, ratios, cmPerPx, searchHip = false) {
  const bodyPx = sil.bottom - sil.top;
  const k = Math.max(1, Math.round(0.008 * bodyPx));
  const out = {};
  for (const key of ["chest", "waist", "hip"]) {
    const y = Math.min(sil.height - 1, Math.max(0, sil.top + Math.round(bodyPx * ratios[key])));
    let w = bandWidth(sil, y, k);
    if (key === "hip" && searchHip) {
      const reach = Math.round(0.04 * bodyPx);
      for (let yy = Math.max(sil.top, y - reach); yy <= Math.min(sil.bottom, y + reach); yy++) {
        w = Math.max(w, bandWidth(sil, yy, k));
      }
    }
    if (w <= 0) return null;
    out[key] = w * cmPerPx;
  }
  return out;
}

// 확인용: 잘라낸 사진 위에 몸으로 고른 윤곽(초록)과 측정 위치(선)를 그린다
function makeOverlay(canvas, seg, sil, ratios) {
  const c = document.createElement("canvas");
  c.width = seg.crop.w;
  c.height = seg.crop.h;
  const x = c.getContext("2d");
  x.drawImage(canvas, seg.crop.x0, seg.crop.y0, seg.crop.w, seg.crop.h, 0, 0, c.width, c.height);
  const sx = c.width / seg.mask.width, sy = c.height / seg.mask.height;
  x.fillStyle = "rgba(0,229,160,0.45)";
  for (let y = 0; y < sil.height; y++) {
    if (sil.left[y] < 0) continue;
    x.fillRect(sil.left[y] * sx, y * sy, (sil.right[y] - sil.left[y] + 1) * sx, sy + 1);
  }
  const bodyPx = sil.bottom - sil.top;
  const colors = { chest: "#ffd400", waist: "#ff3b30", hip: "#0a84ff" };
  const names = { chest: "가슴", waist: "허리", hip: "골반" };
  x.lineWidth = Math.max(2, c.width / 250);
  x.font = `bold ${Math.max(14, Math.round(c.width / 22))}px sans-serif`;
  for (const key of ["chest", "waist", "hip"]) {
    const y = (sil.top + ratios[key] * bodyPx) * sy;
    x.strokeStyle = x.fillStyle = colors[key];
    x.beginPath();
    x.moveTo(0, y);
    x.lineTo(c.width, y);
    x.stroke();
    x.fillText(names[key], 6, y - 4);
  }
  return c.toDataURL("image/jpeg", 0.8);
}

async function measureFront(canvas) {
  const lms = detectPose(canvas);
  const poseErr = { error: "정면 관절 분석 실패. 다른 사진으로 다시 시도해 주세요." };
  if (!lms) return poseErr;
  const seg = segmentCrop(canvas, lms);
  const silErr = { error: "신체 윤곽 분석 실패. 다른 사진으로 다시 시도해 주세요." };
  if (!seg) return silErr;
  const { mask } = seg;
  const bodyRows = seg.axis && seg.axis.bodyHeight > 0 ? seg.axis.bodyHeight * mask.height : DEFAULT_BODY_FRACTION * mask.height;
  eraseArms(mask.data, mask.width, mask.height, seg.p, bodyRows);
  const sil = buildSilhouette(mask.data, mask.width, mask.height, seg.axis);
  if (!sil) return silErr;
  const cmMask = userHeightCm / (sil.bottom - sil.top);
  const ratios = pickRatios(sil, seg.p);
  const widths = widthsAt(sil, ratios, cmMask);
  if (!widths) return silErr;
  const px = lms.map((l) => ({ x: l.x * canvas.width, y: l.y * canvas.height, v: l.visibility ?? 1 }));
  const lengths = calcLengths(px, userHeightCm, cmMask / seg.f);
  if (!lengths) return poseErr;
  // 다리 길이 = 바닥(윤곽 맨 아래)에서 엉덩이 관절 높이까지 - Y존 보정. 무릎·발목 관절을 잇는 것보다 정확하다
  if (seg.p && seg.p[L.L_HIP].v >= 0.3 && seg.p[L.R_HIP].v >= 0.3) {
    const hipY = (seg.p[L.L_HIP].y + seg.p[L.R_HIP].y) / 2;
    const leg = (sil.bottom - hipY) * cmMask - LEG_Y_OFFSET_RATIO * userHeightCm;
    if (leg > 0) lengths.leftLegLength = lengths.rightLegLength = leg;
  }
  return { lengths, widths, ratios, overlay: makeOverlay(canvas, seg, sil, ratios),
    warnings: photoWarnings(lms, canvas.width, canvas.height, "front") };
}

// 옆면은 정면에서 찾은 위치와 같은 높이를 잰다
async function measureSide(canvas, ratios) {
  let lms = null;
  try {
    lms = detectPose(canvas);
  } catch (e) {
    console.warn("옆면 관절 인식 실패, 화면 가운데 기준으로 윤곽을 고릅니다", e);
  }
  const seg = segmentCrop(canvas, lms);
  const silErr = { error: "신체 윤곽 분석 실패. 다른 사진으로 다시 시도해 주세요." };
  if (!seg) return silErr;
  const sil = buildSilhouette(seg.mask.data, seg.mask.width, seg.mask.height, seg.axis);
  if (!sil) return silErr;
  const use = ratios || FIXED_RATIOS;
  const widths = widthsAt(sil, use, userHeightCm / (sil.bottom - sil.top), true);
  if (!widths) return silErr;
  return { widths, overlay: makeOverlay(canvas, seg, sil, use),
    warnings: photoWarnings(lms, canvas.width, canvas.height, "side") };
}

// ---- 사진 점검 (막지 않고 경고만 한다) ---------------------------------------
// 사용자가 올린 사진은 거리·높이·기울기를 통제할 수 없어서, 정확도에 큰 영향을 주는 흔한 경우만 알려 준다.
function photoWarnings(lms, W, H, view) {
  const label = view === "front" ? "정면 사진" : "옆면 사진";
  if (!lms) return [label + ": 사람의 관절이 잘 안 잡혔어요. 전신이 보이게 찍은 사진인지 확인해 주세요."];
  const ok = (i) => lms[i] && (lms[i].visibility ?? 1) >= 0.3;
  const out = [];
  if (ok(L.NOSE) && ok(L.L_ANKLE) && ok(L.R_ANKLE)) {
    const noseY = lms[L.NOSE].y;
    const ankleY = (lms[L.L_ANKLE].y + lms[L.R_ANKLE].y) / 2;
    const base = ankleY - noseY;
    const headY = noseY - 0.075 * base;
    const feet = [L.L_HEEL, L.R_HEEL, L.L_FOOT, L.R_FOOT].filter(ok).map((i) => lms[i].y);
    const footY = feet.length ? Math.max(...feet) : ankleY + 0.05 * base;
    if (footY - headY < 0.65) out.push(label + ": 몸이 사진에서 작게 나왔어요. 전신이 사진 높이의 70% 이상이 되게 가까이서 찍어 주세요.");
    if (headY < 0.01) out.push(label + ": 머리가 사진 위쪽에서 잘렸을 수 있어요.");
    if (footY > 0.995) out.push(label + ": 발이 사진 아래쪽에서 잘렸을 수 있어요.");
    // 정면인지 옆면인지 어깨너비로 어림한다 (정면은 몸 키의 약 22%, 옆면은 10% 안팎)
    if (ok(L.L_SHOULDER) && ok(L.R_SHOULDER)) {
      const sw = Math.abs(lms[L.L_SHOULDER].x - lms[L.R_SHOULDER].x) * W;
      const bodyPx = Math.max(1, (footY - headY) * H);
      if (view === "front" && sw / bodyPx < 0.15) out.push("정면 사진: 몸이 옆을 향한 것 같아요. 정면 사진이 맞는지 확인해 주세요.");
      if (view === "side" && sw / bodyPx > 0.16) out.push("옆면 사진: 몸이 정면을 향한 것 같아요. 옆으로 선 사진이 맞는지 확인해 주세요.");
      if (view === "front") {
        let a = (Math.atan2((lms[L.R_SHOULDER].y - lms[L.L_SHOULDER].y) * H, (lms[L.R_SHOULDER].x - lms[L.L_SHOULDER].x) * W) * 180) / Math.PI;
        if (a > 90) a -= 180;
        if (a < -90) a += 180;
        if (Math.abs(a) > 5) out.push("정면 사진: 어깨선이 기울었어요(" + Math.abs(a).toFixed(0) + "°). 폰을 똑바로 세워서 찍으면 더 정확해요.");
      }
    }
  } else {
    out.push(label + ": 머리부터 발끝까지 전신이 보이지 않아요. 전신이 나오게 찍어 주세요.");
  }
  return out;
}

// ---- 계산: CircumferenceCalculator (라마누잔 타원 둘레) -------------------
function ellipse(widthCm, depthCm) {
  if (widthCm <= 0 || depthCm <= 0) return 0;
  const a = widthCm / 2, b = depthCm / 2;
  return Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b)));
}

// ---- 결과 ------------------------------------------------------------------
function showResult() {
  const r = frontResult;
  const chest = ellipse(frontWidths.chest, sideWidths.chest);
  const waist = ellipse(frontWidths.waist, sideWidths.waist);
  const hip = ellipse(frontWidths.hip, sideWidths.hip);
  if (chest <= 0 || waist <= 0 || hip <= 0) {
    show("setup");
    $("setup-msg").textContent = "둘레 계산에 실패했습니다. 다시 측정해주세요.";
    return;
  }

  const avg = (a, b) => (a > 0 && b > 0 ? (a + b) / 2 : a > 0 ? a : b > 0 ? b : -1);
  const arm = avg(r.leftArmLength, r.rightArmLength);
  const leg = avg(r.leftLegLength, r.rightLegLength);
  const cell = (label, v, unit = " cm") =>
    `<div class="cell"><small>${label}</small><b>${v}</b>${v === "-" ? "" : unit}</div>`;
  const cm = (v) => (v > 0 ? v.toFixed(1) : "-");

  $("basic").innerHTML = [
    cell("키", cm(userHeightCm)),
    cell("어깨너비", cm(r.shoulderWidth)),
    cell("팔 길이", cm(arm)),
    cell("상체 길이", cm(r.torsoLength)),
    cell("다리 길이", cm(leg)),
  ].join("");

  $("circ").innerHTML = [
    cell("가슴둘레", cm(chest)),
    cell("허리둘레", cm(waist)),
    cell("골반둘레", cm(hip)),
    cell("가슴 폭 / 두께", `${frontWidths.chest.toFixed(1)} / ${sideWidths.chest.toFixed(1)}`),
    cell("허리 폭 / 두께", `${frontWidths.waist.toFixed(1)} / ${sideWidths.waist.toFixed(1)}`),
    cell("골반 폭 / 두께", `${frontWidths.hip.toFixed(1)} / ${sideWidths.hip.toFixed(1)}`),
  ].join("");

  $("ratio").innerHTML = [
    cell("상체 비율", r.torsoLength > 0 ? ((r.torsoLength / userHeightCm) * 100).toFixed(1) : "-", "%"),
    cell("하체 비율", leg > 0 ? ((leg / userHeightCm) * 100).toFixed(1) : "-", "%"),
    cell("허리-골반 비율 (WHR)", (waist / hip).toFixed(2), ""),
  ].join("");

  $("img-front").src = frontCanvas.toDataURL("image/jpeg", 0.8);
  $("img-side").src = sideCanvas.toDataURL("image/jpeg", 0.8);
  $("dbg-front").src = frontDbg;
  $("dbg-side").src = sideDbg;
  const warnBox = $("warnings");
  warnBox.innerHTML = photoWarns.map((t) => "<li>" + t + "</li>").join("");
  warnBox.classList.toggle("hidden", photoWarns.length === 0);
  show("result");
  window.scrollTo(0, 0);
}

// ---- 사진 올리기 -----------------------------------------------------------
const ALBUM_MAX_SIDE = 1600; // 너무 큰 사진은 줄여서 분석 (비율은 그대로)
let albumFront = null;
let albumSide = null;

function updateAnalyzeButton() {
  const ready = !!(albumFront && albumSide) && modelsReady;
  $("btn-album-analyze").disabled = !ready;
  $("album-status").textContent = !(albumFront && albumSide)
    ? "정면과 옆면 사진을 모두 선택해주세요."
    : modelsReady ? "사진 준비 완료 · 분석하기를 눌러주세요." : "모델을 불러오는 중이에요…";
}

// 사진 파일 → 캔버스. 폰 사진의 회전 정보(EXIF)를 반영한다.
async function fileToCanvas(file) {
  const bmp = await createImageBitmap(file, { imageOrientation: "from-image" });
  const scale = Math.min(1, ALBUM_MAX_SIDE / Math.max(bmp.width, bmp.height));
  const c = document.createElement("canvas");
  c.width = Math.round(bmp.width * scale);
  c.height = Math.round(bmp.height * scale);
  c.getContext("2d").drawImage(bmp, 0, 0, c.width, c.height);
  if (bmp.close) bmp.close();
  return c;
}

function bindAlbumInput(kind) {
  $("file-" + kind).addEventListener("change", async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const name = kind === "front" ? "정면" : "옆면";
    try {
      const c = await fileToCanvas(file);
      if (kind === "front") albumFront = c; else albumSide = c;
      const img = $("album-" + kind + "-img");
      img.src = c.toDataURL("image/jpeg", 0.8);
      img.classList.remove("hidden");
      $("album-" + kind + "-ph").classList.add("hidden");
      $("album-" + kind + "-msg").textContent = "✓ " + name + " 사진 선택 완료";
    } catch (err) {
      console.error(err);
      if (kind === "front") albumFront = null; else albumSide = null;
      $("album-" + kind + "-msg").textContent = "사진을 불러올 수 없습니다. 다른 사진을 골라주세요.";
    }
    updateAnalyzeButton();
  });
}
bindAlbumInput("front");
bindAlbumInput("side");

function albumError(message) {
  setBusy(false);
  $("album-status").textContent = message;
  alert(message);
}

function readHeight() {
  const h = parseFloat($("height").value);
  if (!(h >= 100 && h <= 220)) {
    $("setup-msg").textContent = "키를 100~220 사이로 입력해주세요.";
    return 0;
  }
  $("setup-msg").textContent = "";
  return h;
}

$("btn-album-analyze").addEventListener("click", async () => {
  if (!albumFront || !albumSide) return;
  const h = readHeight();
  if (!h) return;
  userHeightCm = h;
  setBusy(true, "정면 사진을 분석하고 있습니다...");
  await nextFrame();
  try {
    const f = await measureFront(albumFront);
    if (f.error) return albumError("정면 사진: " + f.error);

    setBusy(true, "옆면 사진을 분석하고 있습니다...");
    await nextFrame();
    const s = await measureSide(albumSide, f.ratios);
    if (s.error) return albumError("옆면 사진: " + s.error);

    frontResult = f.lengths;
    frontWidths = f.widths;
    frontDbg = f.overlay;
    sideWidths = s.widths;
    sideDbg = s.overlay;
    frontCanvas = albumFront;
    sideCanvas = albumSide;
    photoWarns = [...f.warnings, ...s.warnings];
  } catch (e) {
    console.error(e);
    return albumError("사진 분석에 실패했습니다: " + e.message);
  }
  setBusy(false);
  showResult();
});

$("btn-again").addEventListener("click", () => show("setup"));

// 디버그용 (콘솔에서 사진 한 장으로 계산을 확인할 때)
window.__dbg = { get poseLandmarker() { return poseLandmarker; }, calcLengths, buildSilhouette, bodyAxis, measureFront, measureSide };

