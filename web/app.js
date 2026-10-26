// 신체 치수 측정 웹 테스트 버전 (아이폰 테스트용).
// 안드로이드 앱의 BodyMeasurementActivity 흐름을 그대로 옮겼다:
//   키 입력 → 정면 자동 촬영 → 측면 자동 촬영 → 길이·둘레 계산 → 결과
// 계산식은 BodyMeasurementCalculator / CircumferenceCalculator / BodyMeasurementActivity와 같다.

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

// ---- 앱과 같은 기준값 ------------------------------------------------------
const STABLE_DURATION_MS = 150;     // 가이드에 맞는 프레임이 이만큼 이어지면 바로 촬영
const POSE_READY_DELAY_MS = 3000;   // 측면 안내 뒤 몸 돌릴 시간
const GRACE_DURATION_MS = 650;
const TOLERANCE_X = 0.16;
const TOLERANCE_Y = 0.13;
const MIN_LIKELIHOOD = 0.4;         // 정렬 판단용
const VALID_LIKELIHOOD = 0.5;       // 치수 계산용 (앱과 같음)
const GUIDE_HEAD_Y = 80;            // 머리 끝 가이드선 (300x600 가이드 기준, 화면 높이의 13%)
const GUIDE_FOOT_Y = 565;           // 발끝 가이드선 (화면 높이의 94%)
const HEADFOOT_TOL = 0.025;         // 머리 끝·발끝이 가이드선에서 벗어나도 되는 범위 (화면 높이 대비)
const SPAN_TOL = 0.06;              // 몸 높이가 가이드와 이만큼 다르면 가까이/멀리 안내
const LEVEL_TOL_DEG = 3;            // 폰이 서 있어야 하는 기울기 허용 범위 (도)
const FRONT_REQUIRED_MATCHES = 5;
const SIDE_REQUIRED_MATCHES = 3;

// MediaPipe 관절 번호 (앱의 PoseLandmark와 같다)
const L = {
  NOSE: 0, L_SHOULDER: 11, R_SHOULDER: 12, L_ELBOW: 13, R_ELBOW: 14,
  L_WRIST: 15, R_WRIST: 16, L_HIP: 23, R_HIP: 24, L_KNEE: 25, R_KNEE: 26,
  L_ANKLE: 27, R_ANKLE: 28,
  L_HEEL: 29, R_HEEL: 30, L_FOOT: 31, R_FOOT: 32,
};

// ---- 화면 요소 -------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const screens = { setup: $("screen-setup"), album: $("screen-album"), camera: $("screen-camera"), result: $("screen-result") };
const video = $("video");
const overlay = $("overlay");
const octx = overlay.getContext("2d");

function show(name) {
  for (const [k, el] of Object.entries(screens)) el.classList.toggle("hidden", k !== name);
}

// ---- 상태 ------------------------------------------------------------------
let poseLandmarker = null;
let segmenter = null;
let stream = null;
let facing = "environment";
let userHeightCm = 0;

let phase = "idle";          // idle | live | busy
let mode = "front";          // front | side
let stableStart = 0;
let lastAligned = 0;
let captureReadyTime = 0;
let lastVideoTime = -1;
let alignHint = "";          // 지금 자세가 가이드에 안 맞는 이유 (상태 문구로 보여 준다)

let frontCanvas = null;
let sideCanvas = null;
let frontResult = null;      // 길이
let frontWidths = null;
let sideWidths = null;
let frontRatios = null;      // 정면에서 찾은 가슴·허리·골반 위치 (키 대비 비율). 옆면도 같은 위치를 잰다
let frontDbg = "";           // 확인용 윤곽·측정 위치 그림
let sideDbg = "";

// ---- 모델 불러오기 ---------------------------------------------------------
async function loadModels() {
  const vision = await FilesetResolver.forVisionTasks(WASM_URL);
  poseLandmarker = await PoseLandmarker.createFromOptions(vision, {
    baseOptions: { modelAssetPath: POSE_MODEL, delegate: "CPU" },
    runningMode: "VIDEO",
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

const startBtn = $("btn-start");
loadModels()
  .then(() => {
    startBtn.disabled = false;
    $("btn-album").disabled = false;
    startBtn.textContent = "측정 시작";
  })
  .catch((e) => {
    console.error(e);
    startBtn.textContent = "모델 불러오기 실패";
    $("setup-msg").textContent = "모델을 불러오지 못했어요. 인터넷 연결을 확인하고 새로고침하세요. (" + e.message + ")";
  });

startBtn.addEventListener("click", async () => {
  const h = parseFloat($("height").value);
  if (!(h >= 100 && h <= 220)) {
    $("setup-msg").textContent = "키를 100~220 사이로 입력해주세요.";
    return;
  }
  userHeightCm = h;
  $("setup-msg").textContent = "";
  frontCanvas = sideCanvas = null;
  frontResult = frontWidths = sideWidths = null;
  mode = "front";
  show("camera");
  $("top-height").textContent = "입력 키: " + h.toFixed(1) + " cm";
  setStatus("정면: 전신이 화면에 보이도록 서주세요.");
  try {
    await enableLevel(); // 아이폰은 사용자가 버튼을 누른 직후에만 센서 권한을 물을 수 있다
    await startCamera();
  } catch (e) {
    show("setup");
    $("setup-msg").textContent =
      "카메라를 켤 수 없어요: " + e.message + " (HTTPS 주소인지, 카메라 권한을 허용했는지 확인하세요.)";
  }
});

$("btn-close").addEventListener("click", () => {
  stopCamera();
  show("setup");
});

$("btn-switch").addEventListener("click", async () => {
  if (phase !== "live") return;
  facing = facing === "environment" ? "user" : "environment";
  resetTimers();
  setStatus("카메라를 전환하고 있습니다...");
  try {
    await startCamera();
  } catch (e) {
    setStatus("카메라 전환 실패: " + e.message);
  }
});

// ---- 카메라 ----------------------------------------------------------------
async function startCamera() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error("이 주소에서는 카메라를 쓸 수 없어요");
  }
  stopStreamOnly();
  stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: { ideal: facing },
      width: { ideal: 1280 },
      height: { ideal: 1280 },
    },
  });
  video.srcObject = stream;
  video.classList.toggle("mirror", facing === "user");
  await video.play();
  await new Promise((r) => {
    if (video.videoWidth) r();
    else video.addEventListener("loadedmetadata", r, { once: true });
  });
  lastVideoTime = -1;
  await setRunningMode("VIDEO");
  if (phase !== "live") {
    phase = "live";
    requestAnimationFrame(loop);
  }
  setStatus(mode === "front" ? "정면: 전신이 화면에 보이도록 서주세요." : "측면: 옆으로 서서 전신을 보여주세요.");
}

function stopStreamOnly() {
  if (stream) stream.getTracks().forEach((t) => t.stop());
  stream = null;
}

function stopCamera() {
  phase = "idle";
  stopStreamOnly();
  video.srcObject = null;
  resetTimers();
}

let currentRunningMode = "VIDEO";
async function setRunningMode(m) {
  if (currentRunningMode === m) return;
  await poseLandmarker.setOptions({ runningMode: m });
  currentRunningMode = m;
}

function resetTimers() {
  stableStart = 0;
  lastAligned = 0;
}

function setStatus(t) {
  $("status").textContent = t;
}

// ---- 프레임 반복 -----------------------------------------------------------
function loop() {
  if (phase !== "live") return;
  if (video.readyState >= 2 && video.currentTime !== lastVideoTime && video.videoWidth) {
    lastVideoTime = video.currentTime;
    let lms = null;
    try {
      const res = poseLandmarker.detectForVideo(video, performance.now());
      lms = res.landmarks && res.landmarks[0] ? res.landmarks[0] : null;
    } catch (e) {
      console.warn(e);
    }
    drawOverlay(lms);
    if (lms) checkStability(lms);
    else handleMisalignment("전신 인식을 확인하고 있어요.");
  }
  requestAnimationFrame(loop);
}

// ---- 가이드 (BodyGuideOverlayView와 같은 좌표, 300x600 기준) ---------------
const GUIDE_FRONT = {
  [L.NOSE]: [150, 115],
  [L.L_SHOULDER]: [110, 170], [L.R_SHOULDER]: [190, 170],
  [L.L_ELBOW]: [85, 265], [L.R_ELBOW]: [215, 265],
  [L.L_WRIST]: [70, 360], [L.R_WRIST]: [230, 360],
  [L.L_HIP]: [125, 340], [L.R_HIP]: [175, 340],
  [L.L_KNEE]: [125, 445], [L.R_KNEE]: [175, 445],
  [L.L_ANKLE]: [120, 545], [L.R_ANKLE]: [180, 545],
};
const GUIDE_SIDE = {
  [L.NOSE]: [168, 112],
  [L.L_SHOULDER]: [148, 170], [L.L_ELBOW]: [150, 265], [L.L_WRIST]: [152, 350],
  [L.L_HIP]: [145, 340], [L.L_KNEE]: [150, 445], [L.L_ANKLE]: [148, 545],
};
const BONES_FRONT = [
  [L.L_SHOULDER, L.R_SHOULDER],
  [L.L_SHOULDER, L.L_ELBOW], [L.L_ELBOW, L.L_WRIST],
  [L.R_SHOULDER, L.R_ELBOW], [L.R_ELBOW, L.R_WRIST],
  [L.L_SHOULDER, L.L_HIP], [L.R_SHOULDER, L.R_HIP], [L.L_HIP, L.R_HIP],
  [L.L_HIP, L.L_KNEE], [L.L_KNEE, L.L_ANKLE],
  [L.R_HIP, L.R_KNEE], [L.R_KNEE, L.R_ANKLE],
];
const BONES_SIDE = [
  [L.L_SHOULDER, L.L_ELBOW], [L.L_ELBOW, L.L_WRIST],
  [L.L_SHOULDER, L.L_HIP], [L.L_HIP, L.L_KNEE], [L.L_KNEE, L.L_ANKLE],
];

function guidePoint(type, W, H) {
  const g = (mode === "side" ? GUIDE_SIDE : GUIDE_FRONT)[type];
  return g ? { x: (g[0] * W) / 300, y: (g[1] * H) / 600 } : null;
}

function fitOverlay() {
  const dpr = window.devicePixelRatio || 1;
  const W = overlay.clientWidth;
  const H = overlay.clientHeight;
  if (overlay.width !== Math.round(W * dpr) || overlay.height !== Math.round(H * dpr)) {
    overlay.width = Math.round(W * dpr);
    overlay.height = Math.round(H * dpr);
  }
  octx.setTransform(dpr, 0, 0, dpr, 0, 0);
  return { W, H };
}

// object-fit: cover 방식 (앱의 FILL_CENTER와 같다). 미러 카메라면 x를 뒤집는다.
function toView(lm, W, H) {
  const vw = video.videoWidth, vh = video.videoHeight;
  const scale = Math.max(W / vw, H / vh);
  const offX = (W - vw * scale) / 2;
  const offY = (H - vh * scale) / 2;
  let x = lm.x * vw * scale + offX;
  const y = lm.y * vh * scale + offY;
  if (facing === "user") x = W - x;
  return { x, y };
}

function drawOverlay(lms) {
  const { W, H } = fitOverlay();
  octx.clearRect(0, 0, W, H);

  // 가이드
  octx.save();
  octx.scale(W / 300, H / 600);
  octx.strokeStyle = "rgba(0,229,160,.53)";
  octx.fillStyle = "#00e5a0";
  octx.lineWidth = 3;
  octx.beginPath();
  octx.roundRect(48, 35, 204, 530, 18);
  octx.stroke();
  octx.beginPath();
  octx.ellipse(mode === "side" ? 152.5 : 150, 105, mode === "side" ? 22.5 : 20, 30, 0, 0, Math.PI * 2);
  octx.stroke();
  octx.strokeStyle = "rgba(0,229,160,.67)";
  octx.lineWidth = 5;
  octx.lineCap = "round";
  const g = mode === "side" ? GUIDE_SIDE : GUIDE_FRONT;
  for (const [a, b] of mode === "side" ? BONES_SIDE : BONES_FRONT) {
    octx.beginPath();
    octx.moveTo(...g[a]);
    octx.lineTo(...g[b]);
    octx.stroke();
    for (const p of [g[a], g[b]]) {
      octx.beginPath();
      octx.arc(p[0], p[1], 6, 0, Math.PI * 2);
      octx.fill();
    }
  }
  octx.restore();

  drawHeadFootLines(W, H, lms);
  drawLevel(W);

  // 인식된 골격 (흰색)
  if (!lms) return;
  const ok = (i) => lms[i] && (lms[i].visibility ?? 1) >= VALID_LIKELIHOOD;
  octx.strokeStyle = "#fff";
  octx.lineWidth = 3;
  for (const [a, b] of BONES_FRONT) {
    if (!ok(a) || !ok(b)) continue;
    const p = toView(lms[a], W, H), q = toView(lms[b], W, H);
    octx.beginPath();
    octx.moveTo(p.x, p.y);
    octx.lineTo(q.x, q.y);
    octx.stroke();
  }
  octx.fillStyle = "#fff";
  octx.strokeStyle = "#000";
  octx.lineWidth = 1.5;
  for (const i of Object.values(L)) {
    if (!ok(i)) continue;
    const p = toView(lms[i], W, H);
    octx.beginPath();
    octx.arc(p.x, p.y, 7, 0, Math.PI * 2);
    octx.fill();
    octx.stroke();
  }
}

// ---- 머리 끝·발끝 위치 -----------------------------------------------------
// 머리 끝은 코~발목 거리의 7.5%만큼 코 위, 발끝은 뒤꿈치·발끝 관절 중 가장 아래 (없으면 발목 아래로 추정)
function headFoot(lms, W, H) {
  const ok = (i) => lms[i] && (lms[i].visibility ?? 1) >= MIN_LIKELIHOOD;
  if (!ok(L.NOSE) || !ok(L.L_ANKLE) || !ok(L.R_ANKLE)) return null;
  const noseY = toView(lms[L.NOSE], W, H).y;
  const ankleY = (toView(lms[L.L_ANKLE], W, H).y + toView(lms[L.R_ANKLE], W, H).y) / 2;
  const base = ankleY - noseY;
  if (base <= 0) return null;
  const feet = [L.L_HEEL, L.R_HEEL, L.L_FOOT, L.R_FOOT].filter(ok).map((i) => toView(lms[i], W, H).y);
  return {
    headY: noseY - 0.075 * base,
    footY: feet.length ? Math.max(...feet) : ankleY + 0.05 * base,
  };
}

function drawHeadFootLines(W, H, lms) {
  const gHead = (GUIDE_HEAD_Y / 600) * H;
  const gFoot = (GUIDE_FOOT_Y / 600) * H;
  const tol = HEADFOOT_TOL * H;
  const hf = lms ? headFoot(lms, W, H) : null;
  const headOk = hf && Math.abs(hf.headY - gHead) <= tol;
  const footOk = hf && Math.abs(hf.footY - gFoot) <= tol;

  octx.save();
  octx.lineWidth = 3;
  octx.font = "bold 14px sans-serif";
  for (const [y, ok, label] of [[gHead, headOk, "머리 끝"], [gFoot, footOk, "발끝"]]) {
    const color = ok ? "#00e5a0" : "#ffd400";
    octx.strokeStyle = color;
    octx.fillStyle = color;
    octx.setLineDash([10, 6]);
    octx.beginPath();
    octx.moveTo(0, y);
    octx.lineTo(W, y);
    octx.stroke();
    octx.setLineDash([]);
    octx.fillText(label, 8, y - 6);
  }
  // 지금 인식된 머리 끝·발끝 (짧은 선)
  if (hf) {
    octx.lineWidth = 4;
    for (const [y, ok] of [[hf.headY, headOk], [hf.footY, footOk]]) {
      octx.strokeStyle = ok ? "#00e5a0" : "#ff3b30";
      octx.beginPath();
      octx.moveTo(W / 2 - 40, y);
      octx.lineTo(W / 2 + 40, y);
      octx.stroke();
    }
  }
  octx.restore();
}

// ---- 수평계 (가속도 센서의 중력 방향으로 폰이 서 있는지 잰다) ---------------
let tilt = null; // { roll, pitch, t }  roll: 화면 면 안에서 기운 정도, pitch: 앞뒤로 기운 정도 (도, 부호 없음)

function onMotion(e) {
  const a = e.accelerationIncludingGravity;
  if (!a || a.x == null || a.y == null || a.z == null) return;
  const roll = (Math.atan2(Math.abs(a.x), Math.abs(a.y)) * 180) / Math.PI;
  const pitch = (Math.atan2(Math.abs(a.z), Math.hypot(a.x, a.y)) * 180) / Math.PI;
  const now = performance.now();
  // 손떨림을 줄이려고 조금 부드럽게
  tilt = tilt && now - tilt.t < 1000
    ? { roll: tilt.roll * 0.7 + roll * 0.3, pitch: tilt.pitch * 0.7 + pitch * 0.3, t: now }
    : { roll, pitch, t: now };
}

async function enableLevel() {
  try {
    if (typeof DeviceMotionEvent !== "undefined" && typeof DeviceMotionEvent.requestPermission === "function") {
      const r = await DeviceMotionEvent.requestPermission(); // 아이폰: 사용자가 누른 직후에만 물어볼 수 있다
      if (r !== "granted") return;
    }
    window.addEventListener("devicemotion", onMotion);
  } catch (e) {
    console.warn("수평 센서를 켤 수 없어요", e);
  }
}

// available=false(센서 없음·가로 화면)면 조건을 건너뛴다
function levelState() {
  const landscape = screen.orientation && String(screen.orientation.type).startsWith("landscape");
  if (!tilt || performance.now() - tilt.t > 1500 || landscape) return { available: false, ok: true };
  const ok = tilt.roll <= LEVEL_TOL_DEG && tilt.pitch <= LEVEL_TOL_DEG;
  return { available: true, ok, roll: tilt.roll, pitch: tilt.pitch };
}

function drawLevel(W) {
  const lv = levelState();
  const x = W / 2, y = 44;
  octx.save();
  octx.textAlign = "center";
  octx.font = "bold 13px sans-serif";
  if (!lv.available) {
    octx.fillStyle = "rgba(255,255,255,.7)";
    octx.fillText("수평계 사용 불가", x, y + 4);
  } else {
    const color = lv.ok ? "#00e5a0" : "#ff3b30";
    octx.strokeStyle = color;
    octx.fillStyle = color;
    octx.lineWidth = 3;
    octx.beginPath();
    octx.arc(x, y, 14, 0, Math.PI * 2);
    octx.stroke();
    // 앞뒤 기울기는 점이 위아래로, 좌우 기울기는 막대가 기운 정도로 보여 준다
    const dot = Math.min(10, lv.pitch * 2);
    octx.beginPath();
    octx.arc(x, y + dot, 4, 0, Math.PI * 2);
    octx.fill();
    octx.save();
    octx.translate(x, y);
    octx.rotate((Math.min(lv.roll, 20) * Math.PI) / 180);
    octx.beginPath();
    octx.moveTo(-30, 0);
    octx.lineTo(-16, 0);
    octx.moveTo(16, 0);
    octx.lineTo(30, 0);
    octx.stroke();
    octx.restore();
    octx.fillText(lv.ok ? "수평 OK" : `기울기 ${Math.max(lv.roll, lv.pitch).toFixed(1)}°`, x, y + 32);
  }
  octx.restore();
}

// ---- 자세 정렬 판단 + 즉시 촬영 (앱 isPoseAligned / checkPoseStability) ----
const REQUIRED = [L.NOSE, L.L_SHOULDER, L.R_SHOULDER, L.L_HIP, L.R_HIP, L.L_KNEE, L.R_KNEE, L.L_ANKLE, L.R_ANKLE];

function isPoseAligned(lms) {
  const W = overlay.clientWidth, H = overlay.clientHeight;
  if (!W || !H) return false;
  alignHint = "";
  const lv = levelState();
  if (!lv.ok) {
    alignHint = `폰을 똑바로 세워 주세요 (기울기 ${Math.max(lv.roll, lv.pitch).toFixed(1)}°)`;
    return false;
  }
  const requiredMatches = mode === "side" ? SIDE_REQUIRED_MATCHES : FRONT_REQUIRED_MATCHES;
  const tolX = W * TOLERANCE_X, tolY = H * TOLERANCE_Y;
  let detected = 0, matched = 0;
  let minY = Infinity, maxY = -Infinity;

  for (const type of REQUIRED) {
    const lm = lms[type];
    if (!lm || (lm.visibility ?? 1) < MIN_LIKELIHOOD) continue;
    const p = toView(lm, W, H);
    detected++;
    minY = Math.min(minY, p.y);
    maxY = Math.max(maxY, p.y);

    // 측면은 양쪽 관절이 겹치므로 왼쪽 가이드를 공통 기준으로 쓴다
    let gType = type;
    if (mode === "side") {
      if (type === L.R_SHOULDER) gType = L.L_SHOULDER;
      else if (type === L.R_HIP) gType = L.L_HIP;
      else if (type === L.R_KNEE) gType = L.L_KNEE;
      else if (type === L.R_ANKLE) gType = L.L_ANKLE;
    }
    const gp = guidePoint(gType, W, H);
    if (!gp) continue;
    if (Math.abs(p.x - gp.x) <= tolX && Math.abs(p.y - gp.y) <= tolY) matched++;
  }
  if (detected < requiredMatches || matched < requiredMatches) return false;
  // 머리 끝·발끝이 가이드선에 맞아야 한다. 사람이 작거나 크기가 매번 다르면 윤곽 폭이 크게 틀린다
  const hf = headFoot(lms, W, H);
  if (!hf) {
    alignHint = "머리부터 발끝까지 전신이 보이게 서주세요.";
    return false;
  }
  const gHead = (GUIDE_HEAD_Y / 600) * H, gFoot = (GUIDE_FOOT_Y / 600) * H;
  const span = hf.footY - hf.headY, gSpan = gFoot - gHead;
  if (span < gSpan * (1 - SPAN_TOL)) {
    alignHint = "조금 가까이 와서 머리 끝·발끝을 가이드선에 맞춰주세요.";
    return false;
  }
  if (span > gSpan * (1 + SPAN_TOL)) {
    alignHint = "조금 뒤로 물러나 머리 끝·발끝을 가이드선에 맞춰주세요.";
    return false;
  }
  const off = (hf.headY - gHead + (hf.footY - gFoot)) / 2;
  if (Math.abs(hf.headY - gHead) > HEADFOOT_TOL * H || Math.abs(hf.footY - gFoot) > HEADFOOT_TOL * H) {
    alignHint = off < 0
      ? "몸이 가이드보다 위에 있어요. 폰을 조금 올려주세요."
      : "몸이 가이드보다 아래에 있어요. 폰을 조금 내려주세요.";
    return false;
  }
  return true;
}

function checkStability(lms) {
  if (!isPoseAligned(lms)) {
    handleMisalignment(
      alignHint ||
        (mode === "front"
          ? "정면: 어깨와 골반을 가이드에 맞춰주세요."
          : "측면: 옆으로 서서 전신을 보여주세요.")
    );
    return;
  }
  const now = performance.now();
  lastAligned = now;
  if (!stableStart) stableStart = now;

  if (now < captureReadyTime) {
    setStatus("자세를 잡아주세요...");
    stableStart = 0;
    return;
  }
  if (now - stableStart < STABLE_DURATION_MS) {
    setStatus("자세 인식 중...");
    return;
  }
  setStatus("촬영 중...");
  capturePhoto();
}

function handleMisalignment(message) {
  const now = performance.now();
  if (lastAligned && now - lastAligned <= GRACE_DURATION_MS) return;
  resetTimers();
  setStatus(message);
}

// ---- 촬영 + 확인 -----------------------------------------------------------
function capturePhoto() {
  if (phase !== "live") return;
  phase = "busy";
  const c = document.createElement("canvas");
  c.width = video.videoWidth;
  c.height = video.videoHeight;
  c.getContext("2d").drawImage(video, 0, 0, c.width, c.height);

  // 미러 카메라(전면)는 화면에서 보이는 모습과 같은 방향으로 저장한다
  let shot = c;
  if (facing === "user") {
    shot = document.createElement("canvas");
    shot.width = c.width;
    shot.height = c.height;
    const sc = shot.getContext("2d");
    sc.translate(c.width, 0);
    sc.scale(-1, 1);
    sc.drawImage(c, 0, 0);
  }

  openModal({
    title: mode === "front" ? "정면 사진 확인" : "측면 사진 확인",
    img: shot.toDataURL("image/jpeg", 0.85),
    no: "다시 찍기",
    yes: "사진 사용하기",
    onNo: () => {
      resetTimers();
      phase = "live";
      setStatus("자세를 다시 맞춰주세요.");
      requestAnimationFrame(loop);
    },
    onYes: () => {
      if (mode === "front") analyzeFront(shot);
      else analyzeSide(shot);
    },
  });
}

function openModal({ title, text = "", img = "", no, yes, onNo, onYes }) {
  $("modal-title").textContent = title;
  $("modal-text").textContent = text;
  $("modal-text").classList.toggle("hidden", !text);
  $("modal-img").classList.toggle("hidden", !img);
  if (img) $("modal-img").src = img;
  $("modal-no").classList.toggle("hidden", !no);
  $("modal-no").textContent = no || "";
  $("modal-yes").textContent = yes;
  $("modal").classList.remove("hidden");
  $("modal-no").onclick = () => { closeModal(); onNo && onNo(); };
  $("modal-yes").onclick = () => { closeModal(); onYes && onYes(); };
}
function closeModal() {
  $("modal").classList.add("hidden");
}
function setBusy(on, text) {
  $("busy").classList.toggle("hidden", !on);
  if (text) $("busy-text").textContent = text;
}

async function failAnalysis(message) {
  setBusy(false);
  await setRunningMode("VIDEO"); // 정면 분석 중 IMAGE로 바꿨으면 되돌린다
  resetTimers();
  phase = "live";
  setStatus(message);
  alert(message);
  requestAnimationFrame(loop);
}

// ---- 정면 / 측면 분석 ------------------------------------------------------
const nextFrame = () => new Promise((r) => setTimeout(r, 30));

async function analyzeFront(canvas) {
  phase = "busy";
  setBusy(true, "정면 사진 분석 중...");
  await nextFrame();
  try {
    const m = await measureFront(canvas);
    if (m.error) return failAnalysis(m.error);
    frontResult = m.lengths;
    frontWidths = m.widths;
    frontRatios = m.ratios;
    frontDbg = m.overlay;
    frontCanvas = canvas;
  } catch (e) {
    console.error(e);
    return failAnalysis("정면 사진 분석에 실패했습니다.");
  }
  setBusy(false);
  switchToSide();
}

async function analyzeSide(canvas) {
  phase = "busy";
  setBusy(true, "측면 사진 분석 중...");
  await nextFrame();
  try {
    const m = await measureSide(canvas, frontRatios);
    if (m.error) return failAnalysis(m.error);
    sideWidths = m.widths;
    sideDbg = m.overlay;
    sideCanvas = canvas;
  } catch (e) {
    console.error(e);
    return failAnalysis("측면 사진 분석에 실패했습니다.");
  }
  setBusy(false);
  showResult();
}

function switchToSide() {
  mode = "side";
  resetTimers();
  stopStreamOnly();
  openModal({
    title: "정면 촬영 완료",
    text: "정면 사진 분석이 완료되었습니다.\n\n이제 오른쪽을 바라보도록 몸을 돌려주세요.\n측면 자세가 인식되면 자동 촬영됩니다.",
    yes: "측면 촬영 시작",
    onYes: async () => {
      phase = "idle";
      try {
        await startCamera(); // 안내 중에 꺼 둔 카메라를 다시 켠다
      } catch (e) {
        setStatus("카메라를 다시 켤 수 없어요: " + e.message);
        return;
      }
      resetTimers();
      captureReadyTime = performance.now() + POSE_READY_DELAY_MS;
      setStatus("측면: 옆으로 서서 전신을 보여주세요.");
    },
  });
  $("modal-text").style.whiteSpace = "pre-line";
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

// ---- 계산: 실루엣 폭 (BodyMeasurementActivity.measureSilhouette) ------------
function measureSilhouette(canvas, heightCm, axis = null) {
  if (heightCm <= 0) return null;
  const result = segmenter.segment(canvas);
  const masks = result.confidenceMasks;
  if (!masks || !masks.length) return null;
  // 마스크가 2개(배경, 사람)면 사람은 1번, 1개면 그 마스크가 사람
  const mask = masks.length > 1 ? masks[1] : masks[0];
  const width = mask.width, height = mask.height;
  const data = mask.getAsFloat32Array();
  let out = null;
  try {
    out = silhouetteWidths(data, width, height, heightCm, axis);
  } finally {
    result.close ? result.close() : masks.forEach((m) => m.close && m.close());
  }
  return out;
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

function silhouetteWidths(data, width, height, heightCm, axis = null) {
  if (!width || !height) return null;
  const sil = buildSilhouette(data, width, height, axis);
  if (!sil) return null;

  const bodyPx = sil.bottom - sil.top;
  const cm = heightCm / bodyPx;
  const rowWidth = (ratio) => {
    const y = Math.min(height - 1, Math.max(0, sil.top + Math.round(bodyPx * ratio)));
    return sil.left[y] < 0 ? 0 : sil.right[y] - sil.left[y] + 1;
  };
  const chestPx = rowWidth(0.29);
  const waistPx = rowWidth(0.43);
  const hipPx = rowWidth(0.52);
  if (chestPx <= 0 || waistPx <= 0 || hipPx <= 0) return null;
  return { chest: chestPx * cm, waist: waistPx * cm, hip: hipPx * cm };
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
  await setRunningMode("IMAGE");
  const lms = detectPose(canvas);
  const poseErr = { error: "정면 관절 분석 실패. 다시 촬영해주세요." };
  if (!lms) return poseErr;
  const seg = segmentCrop(canvas, lms);
  const silErr = { error: "신체 윤곽 분석 실패. 다시 촬영해주세요." };
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
  return { lengths, widths, ratios, overlay: makeOverlay(canvas, seg, sil, ratios) };
}

// 옆면은 정면에서 찾은 위치와 같은 높이를 잰다
async function measureSide(canvas, ratios) {
  await setRunningMode("IMAGE");
  let lms = null;
  try {
    lms = detectPose(canvas);
  } catch (e) {
    console.warn("옆면 관절 인식 실패, 화면 가운데 기준으로 윤곽을 고릅니다", e);
  }
  const seg = segmentCrop(canvas, lms);
  const silErr = { error: "신체 윤곽 분석 실패. 다시 촬영해주세요." };
  if (!seg) return silErr;
  const sil = buildSilhouette(seg.mask.data, seg.mask.width, seg.mask.height, seg.axis);
  if (!sil) return silErr;
  const use = ratios || FIXED_RATIOS;
  const widths = widthsAt(sil, use, userHeightCm / (sil.bottom - sil.top), true);
  if (!widths) return silErr;
  return { widths, overlay: makeOverlay(canvas, seg, sil, use) };
}

// ---- 계산: CircumferenceCalculator (라마누잔 타원 둘레) -------------------
function ellipse(widthCm, depthCm) {
  if (widthCm <= 0 || depthCm <= 0) return 0;
  const a = widthCm / 2, b = depthCm / 2;
  return Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b)));
}

// ---- 결과 ------------------------------------------------------------------
function showResult() {
  stopCamera();
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
  show("result");
  window.scrollTo(0, 0);
}

// ---- 앨범 사진으로 측정 (앱 PhotoMeasurementActivity와 같은 흐름) -----------
const ALBUM_MAX_SIDE = 1600; // 너무 큰 사진은 줄여서 분석 (비율은 그대로)
let albumFront = null;
let albumSide = null;

$("btn-album").addEventListener("click", () => {
  const h = parseFloat($("height").value);
  if (!(h >= 100 && h <= 220)) {
    $("setup-msg").textContent = "키를 100~220 사이로 입력해주세요.";
    return;
  }
  $("setup-msg").textContent = "";
  userHeightCm = h;
  albumFront = albumSide = null;
  for (const k of ["front", "side"]) {
    $("album-" + k + "-img").classList.add("hidden");
    $("album-" + k + "-ph").classList.remove("hidden");
    $("file-" + k).value = "";
  }
  $("album-front-msg").textContent = "전신이 보이는 정면 사진";
  $("album-side-msg").textContent = "오른쪽을 본 옆면 사진";
  $("album-height").textContent = "입력 키: " + h.toFixed(1) + " cm";
  updateAlbumButton();
  show("album");
});

$("btn-album-back").addEventListener("click", () => show("setup"));

function updateAlbumButton() {
  const ready = !!(albumFront && albumSide);
  $("btn-album-analyze").disabled = !ready;
  $("album-status").textContent = ready
    ? "사진 준비 완료 · 신체 분석을 시작해주세요."
    : "정면과 옆면 사진을 모두 선택해주세요.";
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
    updateAlbumButton();
  });
}
bindAlbumInput("front");
bindAlbumInput("side");

function albumError(message) {
  setBusy(false);
  $("album-status").textContent = message;
  alert(message);
}

$("btn-album-analyze").addEventListener("click", async () => {
  if (!albumFront || !albumSide) return;
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
    frontRatios = f.ratios;
    frontDbg = f.overlay;
    sideWidths = s.widths;
    sideDbg = s.overlay;
    frontCanvas = albumFront;
    sideCanvas = albumSide;
  } catch (e) {
    console.error(e);
    return albumError("사진 분석에 실패했습니다: " + e.message);
  }
  setBusy(false);
  showResult();
});

$("btn-again").addEventListener("click", () => show("setup"));

// 디버그용 (콘솔에서 사진 한 장으로 계산을 확인할 때)
window.__dbg = { get poseLandmarker() { return poseLandmarker; }, calcLengths, measureSilhouette, buildSilhouette, bodyAxis, setRunningMode };
