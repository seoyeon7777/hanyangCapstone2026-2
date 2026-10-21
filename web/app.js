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
const FRONT_REQUIRED_MATCHES = 5;
const SIDE_REQUIRED_MATCHES = 3;

// MediaPipe 관절 번호 (앱의 PoseLandmark와 같다)
const L = {
  NOSE: 0, L_SHOULDER: 11, R_SHOULDER: 12, L_ELBOW: 13, R_ELBOW: 14,
  L_WRIST: 15, R_WRIST: 16, L_HIP: 23, R_HIP: 24, L_KNEE: 25, R_KNEE: 26,
  L_ANKLE: 27, R_ANKLE: 28,
};

// ---- 화면 요소 -------------------------------------------------------------
const $ = (id) => document.getElementById(id);
const screens = { setup: $("screen-setup"), camera: $("screen-camera"), result: $("screen-result") };
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

let frontCanvas = null;
let sideCanvas = null;
let frontResult = null;      // 길이
let frontWidths = null;
let sideWidths = null;

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

// ---- 자세 정렬 판단 + 즉시 촬영 (앱 isPoseAligned / checkPoseStability) ----
const REQUIRED = [L.NOSE, L.L_SHOULDER, L.R_SHOULDER, L.L_HIP, L.R_HIP, L.L_KNEE, L.R_KNEE, L.L_ANKLE, L.R_ANKLE];

function isPoseAligned(lms) {
  const W = overlay.clientWidth, H = overlay.clientHeight;
  if (!W || !H) return false;
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
  return maxY - minY >= H * 0.48;
}

function checkStability(lms) {
  if (!isPoseAligned(lms)) {
    handleMisalignment(
      mode === "front" ? "정면: 어깨와 골반을 가이드에 맞춰주세요." : "측면: 옆으로 서서 전신을 보여주세요."
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
    await setRunningMode("IMAGE");
    const res = poseLandmarker.detect(canvas);
    const lms = res.landmarks && res.landmarks[0];
    if (!lms) return failAnalysis("정면 관절 분석 실패. 다시 촬영해주세요.");

    // 픽셀 좌표로 변환
    const px = lms.map((l) => ({ x: l.x * canvas.width, y: l.y * canvas.height, v: l.visibility ?? 1 }));
    frontResult = calcLengths(px, userHeightCm);
    if (!frontResult) return failAnalysis("정면 관절 분석 실패. 다시 촬영해주세요.");

    const widths = measureSilhouette(canvas, userHeightCm);
    if (!widths) return failAnalysis("신체 윤곽 분석 실패. 다시 촬영해주세요.");
    frontWidths = widths;
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
    const widths = measureSilhouette(canvas, userHeightCm);
    if (!widths) return failAnalysis("신체 윤곽 분석 실패. 다시 촬영해주세요.");
    sideWidths = widths;
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

function calcLengths(p, heightCm) {
  const need = [L.NOSE, L.L_SHOULDER, L.R_SHOULDER, L.L_ELBOW, L.R_ELBOW, L.L_WRIST, L.R_WRIST,
    L.L_HIP, L.R_HIP, L.L_KNEE, L.R_KNEE, L.L_ANKLE, L.R_ANKLE];
  if (need.some((i) => !p[i] || p[i].v < VALID_LIKELIHOOD)) return null;

  const ankleY = (p[L.L_ANKLE].y + p[L.R_ANKLE].y) / 2;
  // 머리 꼭대기 landmark가 없어서 코 위쪽을 어깨너비*0.35로 추정
  const headTopY = p[L.NOSE].y - dist(p[L.L_SHOULDER], p[L.R_SHOULDER]) * 0.35;
  const bodyPx = Math.abs(ankleY - headTopY);
  if (bodyPx <= 0) return null;
  const cm = heightCm / bodyPx;

  const mid = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
  return {
    shoulderWidth: dist(p[L.L_SHOULDER], p[L.R_SHOULDER]) * cm,
    leftArmLength: (dist(p[L.L_SHOULDER], p[L.L_ELBOW]) + dist(p[L.L_ELBOW], p[L.L_WRIST])) * cm,
    rightArmLength: (dist(p[L.R_SHOULDER], p[L.R_ELBOW]) + dist(p[L.R_ELBOW], p[L.R_WRIST])) * cm,
    torsoLength: dist(mid(p[L.L_SHOULDER], p[L.R_SHOULDER]), mid(p[L.L_HIP], p[L.R_HIP])) * cm,
    leftLegLength: (dist(p[L.L_HIP], p[L.L_KNEE]) + dist(p[L.L_KNEE], p[L.L_ANKLE])) * cm,
    rightLegLength: (dist(p[L.R_HIP], p[L.R_KNEE]) + dist(p[L.R_KNEE], p[L.R_ANKLE])) * cm,
  };
}

// ---- 계산: 실루엣 폭 (BodyMeasurementActivity.measureSilhouette) ------------
function measureSilhouette(canvas, heightCm) {
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
    out = silhouetteWidths(data, width, height, heightCm);
  } finally {
    result.close ? result.close() : masks.forEach((m) => m.close && m.close());
  }
  return out;
}

function silhouetteWidths(data, width, height, heightCm) {
  if (!width || !height) return null;
  let top = -1, bottom = -1;
  for (let y = 0; y < height; y++) {
    let has = false;
    for (let x = 0; x < width; x++) {
      if (data[y * width + x] >= 0.5) { has = true; break; }
    }
    if (has) {
      if (top === -1) top = y;
      bottom = y;
    }
  }
  if (top < 0 || bottom <= top) return null;

  const bodyPx = bottom - top;
  const cm = heightCm / bodyPx;
  const chestPx = centralWidth(data, width, height, top + Math.round(bodyPx * 0.29));
  const waistPx = centralWidth(data, width, height, top + Math.round(bodyPx * 0.43));
  const hipPx = centralWidth(data, width, height, top + Math.round(bodyPx * 0.52));
  if (chestPx <= 0 || waistPx <= 0 || hipPx <= 0) return null;
  return { chest: chestPx * cm, waist: waistPx * cm, hip: hipPx * cm };
}

// 가로줄에서 화면 가운데에 가장 가까운 사람 덩어리의 폭
function centralWidth(data, width, height, y) {
  if (y < 0 || y >= height) return 0;
  let bestStart = -1, bestEnd = -1, curStart = -1;
  const centerX = width / 2;
  let bestDist = Infinity;
  for (let x = 0; x <= width; x++) {
    const isPerson = x < width && data[y * width + x] >= 0.5;
    if (isPerson && curStart === -1) curStart = x;
    if (!isPerson && curStart !== -1) {
      const end = x - 1;
      const d = Math.abs((curStart + end) / 2 - centerX);
      if (d < bestDist) { bestDist = d; bestStart = curStart; bestEnd = end; }
      curStart = -1;
    }
  }
  if (bestStart === -1) return 0;
  return bestEnd - bestStart + 1;
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
  show("result");
  window.scrollTo(0, 0);
}

$("btn-again").addEventListener("click", () => show("setup"));

// 디버그용 (콘솔에서 사진 한 장으로 계산을 확인할 때)
window.__dbg = { get poseLandmarker() { return poseLandmarker; }, calcLengths, measureSilhouette, setRunningMode };
