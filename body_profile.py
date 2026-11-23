"""
A. 체형 분류 (body_profile.py)

설문 답(새 설문 15문항 중 7~15개, "version": 2) + 아바타 치수를 받아 S(스트레이트)·W(웨이브)·N(내추럴) 비율과 판정 결과를 낸다.
예전 7문항 답(version 없음)도 옛 표(SURVEY)로 그대로 채점한다 (sample.csv·evaluate.py는 옛 7문항 기준).

  [작업 1] 설문 점수 계산, 아바타 점수 계산      score_survey(), score_avatar()
  [작업 2] 비율 맞추기(정규화), 0.6/0.4 합산    normalize(), combine()
  [작업 3] 혼합형 판정, 입력 예외 처리           decide_type(), validate_*()
  [작업 4] 치수 구간 기준값                     THRESHOLDS  (evaluate.py thresholds 로 계산)
  [작업 5] 정확도 검증                          evaluate.py + sample.csv

B·C가 쓰는 함수는 classify() 하나뿐이다.
  classify(answers, measurements)  →  {"ratios", "primary", "secondary", "confidence", "is_mixed"}

  ratios     : S·W·N 특징을 각각 얼마나 가졌는지 나타내는 비율 (합 = 1, 반올림 안 함)
  primary    : 1순위 체형 (대표 체형)
  secondary  : 2순위 체형
  confidence : 1위와 2위의 비율 차이. 클수록 확실함
  is_mixed   : 1·2위 차이가 MIX_GAP(0.10)보다 작으면 True (혼합형)
"""

TYPES = ("S", "W", "N")   # S=스트레이트, W=웨이브, N=내추럴

# ─────────────────────────────────────────────
# 조정용 상수 (튜닝할 때 여기 숫자만 바꾼다)
# ─────────────────────────────────────────────
W_SURVEY, W_AVATAR = 0.6, 0.4   # [작업 2] 설문 / 아바타 비중
MIX_GAP = 0.10                  # [작업 3] 1·2위 차이가 이보다 작으면 혼합형
W_CHEST_DEPTH = 0.5             # 카메라 보조 지표 가중치: 가슴두께 (논문 기준)
W_SHOULDER_SLOPE = 0.5          # 카메라 보조 지표 가중치: 어깨 경사각 (논문 기준)
DEFAULT_AGE_BAND = "30대"       # 나이를 모를 때 쓰는 연령대 기준


# ═════════════════════════════════════════════
# [작업 1-1] 설문 점수 계산
#   7문항, 답은 1·2·3 (숫자로 받음)
#   점수 규칙: 2점 = 그 체형의 뚜렷한 특징
#             1점 = 약한 신호 ("중간", "비슷", "조금" 같은 답)
#             0점 = 정보 없음
#   weight   : 해당 설문 문항의 점수에 곱해지는 가중치. 다른 문항보다 체형 판별에 더 중요하게 반영하겠다.
# ═════════════════════════════════════════════
SURVEY = {
    "q1": {  # 어깨
        "text": "내 어깨는 어떤 모습에 가까운가요?",
        "weight": 1.5,
        "choices": {1: "둥글고 도톰해 보여요", 2: "얇고 부드러워 보여요", 3: "넓고 각진 느낌이에요"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # 둥글고 도톰해 보여요
            2: {"S": 0, "W": 2, "N": 0},   # 얇고 부드러워 보여요
            3: {"S": 0, "W": 0, "N": 2},   # 넓고 각진 느낌이에요
        },
    },
    "q2": {  # 목
        "text": "목은 어떤 모습에 가까운가요?",
        "weight": 1.0,
        "choices": {1: "짧고 굵어 보여요", 2: "길고 가늘어 보여요", 3: "그 중간인 것 같아요"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # 짧고 굵어 보여요
            2: {"S": 0, "W": 2, "N": 0},   # 길고 가늘어 보여요
            3: {"S": 0, "W": 0, "N": 1},   # 그 중간인 것 같아요 (약한 신호)
        },
    },
    "q3": {  # 쇄골
        "text": "쇄골은 평소에 어떻게 보이나요?",
        "weight": 1.0,
        "choices": {1: "거의 보이지 않아요", 2: "조금 보여요", 3: "선명하게 보여요"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # 거의 보이지 않아요
            2: {"S": 0, "W": 1, "N": 0},   # 조금 보여요 (약한 신호)
            3: {"S": 0, "W": 0, "N": 2},   # 선명하게 보여요
        },
    },
    "q4": {  # 뼈마디
        "text": "손가락이나 무릎을 봤을 때 뼈마디가 어떤가요?",
        "weight": 1.0,
        "choices": {1: "크게 튀어나와 보이지 않아요", 2: "보통이에요", 3: "뼈마디가 눈에 잘 보여요"},
        "options": {
            1: {"S": 1, "W": 1, "N": 0},   # 크게 튀어나와 보이지 않아요 (N이 아님)
            2: {"S": 0, "W": 0, "N": 0},   # 보통이에요 (정보 없음)
            3: {"S": 0, "W": 0, "N": 2},   # 뼈마디가 눈에 잘 보여요
        },
    },
    "q5": {  # 상하체 길이 (허리 위치)
        "text": "거울로 봤을 때 어느 쪽이 더 길어 보이나요?",
        "weight": 1.0,
        "choices": {1: "다리가 더 길어 보여요", 2: "비슷해 보여요", 3: "상체가 더 길어 보여요"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # 다리가 더 길어 보여요 (허리 높음)
            2: {"S": 0, "W": 0, "N": 1},   # 비슷해 보여요 (약한 신호)
            3: {"S": 0, "W": 2, "N": 0},   # 상체가 더 길어 보여요 (허리 낮음)
        },
    },
    "q6": {  # 전체 느낌
        "text": "내 몸은 전체적으로 어떤 느낌에 가까운가요?",
        "weight": 1.5,
        "choices": {1: "두께감이 있고 볼륨감 있어 보여요", 2: "얇고 부드럽게 보여요", 3: "몸의 선보다 뼈대가 더 눈에 띄어요"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # 두께감이 있고 볼륨감 있어 보여요
            2: {"S": 0, "W": 2, "N": 0},   # 얇고 부드럽게 보여요
            3: {"S": 0, "W": 0, "N": 2},   # 몸의 선보다 뼈대가 더 눈에 띄어요
        },
    },
    "q7": {  # 몸 전체 모양
        "text": "몸 전체의 모양은 어떤 것과 비슷해 보이나요?",
        "weight": 1.0,
        "choices": {1: "둥근 모양", 2: "아래가 넓은 모양", 3: "위가 넓은 모양"},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},       # 둥근 모양
            2: {"S": 0, "W": 2, "N": 0},       # 아래가 넓은 모양
            3: {"S": 0.5, "W": 0, "N": 1.5},   # 위가 넓은 모양 (S도 상체가 큰 편이라 일부)
        },
    },
}

# ═════════════════════════════════════════════
# [작업 1-1b] 새 설문 15문항 (11/15, 앱 설문 화면과 같은 순서)
#   answers에 "version": 2 가 있으면 이 표로 채점한다 (없으면 위의 옛 7문항 표).
#   보기는 문항마다 A = 스트레이트, B = 웨이브, C = 내추럴 → 고른 체형에 2점 (옛 표의 "뚜렷한 특징" 점수)
#   weight: 옛 표처럼 어깨(Q3)·전체 실루엣(Q14)은 1.5, 나머지 1.0,
#           Q15(어울리는 옷)는 몸이 아니라 옷 입은 경험·취향으로 답할 수 있어 0.5
#   문항마다 세 체형 최고점이 같아(2 × weight) 어느 체형도 유리하지 않다.
#   7번부터 "여기까지 하고 결과보기"가 있어 q1~q7은 꼭, q8~q15는 앞에서부터 이어서 답한 만큼만 온다.
# ═════════════════════════════════════════════
SURVEY_VERSION_KEY = "version"
SURVEY_V2 = 2
SURVEY15_MIN = 7           # 이만큼은 꼭 답해야 결과를 낸다

_SURVEY15_ITEMS = [
    # (문항, weight, A 스트레이트, B 웨이브, C 내추럴)
    ("목의 길이와 특징은?", 1.0,
     "목이 비교적 짧고 탄탄하며 두께감이 있다.", "목이 길고 가늘며 여리여리하다.", "목의 힘줄이나 뼈가 도드라져 보인다."),
    ("쇄골은 어떻게 보이나?", 1.0,
     "쇄골이 크게 드러나지 않는다.", "쇄골이 가늘고 선명하게 보인다.", "쇄골이 굵거나 뼈의 윤곽이 뚜렷하다."),
    ("어깨의 특징은?", 1.5,
     "어깨가 둥글고 두께감이 있다.", "어깨가 좁거나 부드럽게 내려간다.", "어깨뼈가 크거나 각진 느낌이 있다."),
    ("상체를 옆에서 보면?", 1.0,
     "가슴과 등 쪽에 입체적인 두께감이 있다.", "상체가 얇고 납작한 편이다.", "두께보다 뼈대나 골격이 눈에 띈다."),
    ("가슴과 허리의 위치는?", 1.0,
     "가슴과 허리선이 비교적 높은 편이다.", "가슴과 허리선이 비교적 낮은 편이다.", "위치보다 전체적인 골격의 직선감이 두드러진다."),
    ("허리의 특징은?", 1.0,
     "허리 위치가 높고 탄탄한 느낌이다.", "허리가 길어 보이고 곡선이 부드럽다.", "허리 굴곡보다 골반뼈나 골격이 눈에 띈다."),
    ("골반과 엉덩이의 특징은?", 1.0,
     "엉덩이가 높고 입체적으로 볼륨감이 있다.", "엉덩이 볼륨이 아래쪽에 있고 부드럽다.", "골반뼈가 뚜렷하고 직선적인 느낌이다."),
    ("팔과 다리의 특징은?", 1.0,
     "살과 근육이 탄탄하고 입체적이다.", "가늘고 부드러우며 살이 말랑하다.", "길거나 관절과 뼈의 윤곽이 두드러진다."),
    ("손목의 특징은?", 1.0,
     "손목이 둥글고 두께감이 있다.", "손목이 가늘고 납작하다.", "손목뼈가 크거나 튀어나와 보인다."),
    ("손과 손가락의 특징은?", 1.0,
     "손바닥에 두께감이 있고 손가락이 탄탄하다.", "손이 얇고 손가락이 가늘다.", "손마디가 크고 관절이 뚜렷하다."),
    ("무릎과 발목의 특징은?", 1.0,
     "관절이 작거나 살에 덮여 둥글게 보인다.", "관절이 작고 가느다란 느낌이다.", "무릎뼈와 복사뼈가 크고 선명하다."),
    ("살이 찌면 어디에 먼저 붙는 편인가?", 1.0,
     "상체, 가슴, 배, 팔뚝", "허벅지, 엉덩이, 하체", "특정 부위보다 전신에 비교적 고르게 붙는다."),
    ("피부와 살의 촉감은?", 1.0,
     "탄력 있고 단단한 편이다.", "부드럽고 말랑한 편이다.", "살보다 뼈나 힘줄의 존재감이 크다."),
    ("몸 전체의 실루엣은?", 1.5,
     "입체적이고 탄탄하며 볼륨감이 있다.", "곡선적이고 부드러우며 섬세하다.", "직선적이고 프레임이 크거나 각진 편이다."),
    ("어떤 옷에서 체형의 장점이 잘 드러나나?", 0.5,
     "깔끔한 정사이즈 셔츠와 일자핏", "허리선을 강조한 짧은 상의와 부드러운 소재", "여유 있는 셔츠와 루즈한 실루엣"),
]

SURVEY15 = {
    f"q{i + 1}": {
        "text": text,
        "weight": weight,
        "choices": {1: a, 2: b, 3: c},
        "options": {
            1: {"S": 2, "W": 0, "N": 0},   # A
            2: {"S": 0, "W": 2, "N": 0},   # B
            3: {"S": 0, "W": 0, "N": 2},   # C
        },
    }
    for i, (text, weight, a, b, c) in enumerate(_SURVEY15_ITEMS)
}


def is_survey15(answers):
    """새 15문항 설문 답이면 True ("version": 2)"""
    return isinstance(answers, dict) and _to_option(answers.get(SURVEY_VERSION_KEY)) == SURVEY_V2


def answered_survey15(answers):
    """새 설문에서 답한 문항 표 (q1부터 이어서 답한 곳까지)"""
    out = {}
    for q, spec in SURVEY15.items():
        if answers.get(q) in (None, ""):
            break
        out[q] = spec
    return out


# 새 설문에서 동점일 때 먼저 보는 문항: 전체 실루엣(Q14) → 어깨(Q3) (가중치 1.5인 두 문항)
SURVEY15_TIE_QUESTIONS = ("q14", "q3")
_OPTION_TYPE = {1: "S", 2: "W", 3: "N"}


def survey_tie_order(answers):
    """동점일 때 체형 순서. 옛 설문은 S, W, N. 새 설문은 Q14·Q3(그다음 마지막 문항부터)에서 고른 체형을 앞에 둔다
    (늘 S부터 고르면 무작위 답에서도 S가 조금 더 많이 나온다)"""
    if not is_survey15(answers):
        return TYPES
    order = []
    # Q14 → Q3 → 그래도 못 가르면 마지막에 답한 문항부터 거꾸로
    later = [q for q in reversed(list(answered_survey15(answers))) if q not in SURVEY15_TIE_QUESTIONS]
    for q in list(SURVEY15_TIE_QUESTIONS) + later:
        t = _OPTION_TYPE.get(_to_option(answers.get(q)))
        if t and t not in order:
            order.append(t)
    return tuple(order + [t for t in TYPES if t not in order])


def _survey_table(answers):
    """이 답을 채점할 문항 표: 새 설문이면 답한 문항만, 아니면 옛 7문항"""
    return answered_survey15(answers) if is_survey15(answers) else SURVEY


# 설문 점수 계산
def score_survey(answers):
    """설문 원점수 {"S":…, "W":…, "N":…} = 선택지 점수 × 문항 weight 의 합"""
    scores = {t: 0.0 for t in TYPES}
    for q, spec in _survey_table(answers).items():
        opt = spec["options"][_to_option(answers[q])]
        for t in TYPES:
            scores[t] += opt[t] * spec["weight"]
    return scores


# ═════════════════════════════════════════════
# [작업 1-2] 아바타 점수 계산
#   받는 치수 (cm): 키(사용자 입력) + 어깨너비·팔길이·상체길이·다리길이(앱이 카메라로 계산)
#   치수를 그대로 쓰지 않고 "비율 지표"로 바꿔서 키 차이의 영향을 줄인다.
# ═════════════════════════════════════════════
REQUIRED_MEASURES = ("height", "shoulder_width", "arm_length",
                     "upper_body_length", "leg_length")
MEASURE_NAMES = {"height": "키", "shoulder_width": "어깨너비", "arm_length": "팔길이",
                 "upper_body_length": "상체길이", "leg_length": "다리길이"}

# 비율 지표:  키가 다른 사람끼리도 체형의 상대적인 비율을 비교하기 위해서 사용.
INDICATORS = {
    # 상체 ÷ 다리: 상체와 다리의 비율 비교. 
    # 다리가 길면(값이 작으면) S, 상체가 길면(값이 크면) W
    "upper_leg_ratio": (lambda m: m["upper_body_length"] / m["leg_length"],
                        {"S": 1.0}, {"W": 1.0}, 1.0),
    # 어깨너비 ÷ 키: 키에 비해 어깨가 얼마나 넓은지를 계산.
    # 어깨가 넓으면 프레임이 큰 N(일부 S), 좁으면 W
    "shoulder_ratio":  (lambda m: m["shoulder_width"] / m["height"],
                        {"W": 1.0}, {"N": 0.6, "S": 0.4}, 1.0),
    # 팔길이 ÷ 키: 키에 비해 팔이 얼마나 긴지 비교.
    # 팔다리가 길면 N 쪽 (근거가 약해서 가중치 0.5)
    "arm_ratio":       (lambda m: m["arm_length"] / m["height"],
                        {"S": 0.5, "W": 0.5}, {"N": 1.0}, 0.5),
}


def _position(x, low, high):
    """기준값 구간 안에서의 위치: low 이하 0, high 이상 1, 그 사이는 선형 보간."""
    if x <= low:
        return 0.0
    if x >= high:
        return 1.0
    return (x - low) / (high - low)


def score_avatar(measurements):
    """아바타 원점수 {"S":…, "W":…, "N":…}"""
    scores = {t: 0.0 for t in TYPES}
    for name, (calc, low_types, high_types, weight) in INDICATORS.items():
        low, high = THRESHOLDS[name]
        p = _position(calc(measurements), low, high)
        for t, share in high_types.items():
            scores[t] += p * share * weight
        for t, share in low_types.items():
            scores[t] += (1 - p) * share * weight
    for t, v in score_camera_shape(measurements).items():   # 가슴두께·어깨 경사각 (있을 때만)
        scores[t] += v
    return scores


# ═════════════════════════════════════════════
# [작업 1-2b] 카메라 보조 지표 (박선미·정은영 2024, 「신체 유형별 골격진단 요소의 정량화 연구」)
#   치수에 아래 두 값이 있으면 아바타 점수에 더한다. 없으면 이전 계산과 똑같다.
#     chest_depth    : 가슴두께(cm). 옆면 사진 윤곽의 몸통 앞뒤 두께    → 논문 Table 6 '몸매의 인상'
#     shoulder_slope : 어깨 경사각(도). 정면 사진 윤곽의 목옆~어깨 선 기울기 → 논문 Table 6 '어깨 형태'
#     age            : (선택) 나이. 논문 구간이 20~60대로 나뉘어 있다. 없으면 DEFAULT_AGE_BAND
#   논문은 여성 20~69세(사이즈코리아 8차) 기준이고 정답 진단과 맞는지는 검증하지 않았다 → 가중치를 작게 둔다.
# ═════════════════════════════════════════════
# 가슴두께(mm) 구간: 두께감 얇은 편=W, 적당한 편=N, 두께감 있는 편=S  (연령대별)
CHEST_DEPTH_BANDS = {
    "20대": {"W": (140, 187), "N": (188, 211), "S": (212, 285)},
    "30대": {"W": (144, 192), "N": (193, 217), "S": (218, 292)},
    "40대": {"W": (143, 196), "N": (197, 223), "S": (224, 304)},
    "50대": {"W": (153, 202), "N": (203, 226), "S": (227, 302)},
    "60대": {"W": (159, 200), "N": (201, 221), "S": (222, 285)},
}
# 어깨 경사각(도) 구간 (왼쪽, 오른쪽): 일자 어깨=N, 적당한 어깨=S, 쳐진 어깨=W  (연령대별)
SHOULDER_SLOPE_BANDS = {
    "20대": {"N": ((9, 26), (15, 23)), "S": ((27, 32), (24, 27)), "W": ((33, 41), (28, 37))},
    "30대": {"N": ((12, 20), (14, 22)), "S": ((21, 25), (24, 26)), "W": ((26, 40), (27, 37))},
    "40대": {"N": ((11, 23), (16, 21)), "S": ((24, 27), (22, 26)), "W": ((28, 37), (27, 35))},
    "50대": {"N": ((12, 22), (16, 24)), "S": ((23, 26), (25, 27)), "W": ((27, 34), (28, 34))},
    "60대": {"N": ((16, 22), (19, 23)), "S": ((23, 25), (24, 26)), "W": ((26, 35), (27, 35))},
}
# 카메라가 줄 수 있는 값의 허용 범위. 벗어나면 잘못 잰 것으로 보고 그 지표만 무시한다 (진단 전체를 막지 않는다)
CHEST_DEPTH_RANGE = (10.0, 40.0)    # cm
SHOULDER_SLOPE_RANGE = (5.0, 50.0)  # 도
AGE_RANGE = (10, 100)


def age_band(age):
    """나이 → 논문 연령대 키. 모르거나 이상하면 DEFAULT_AGE_BAND, 20 미만은 20대, 60 이상은 60대."""
    if not isinstance(age, (int, float)) or isinstance(age, bool) or not AGE_RANGE[0] <= age <= AGE_RANGE[1]:
        return DEFAULT_AGE_BAND
    return f"{int(min(max(age // 10 * 10, 20), 60))}대"


def _optional_value(measurements, key, value_range):
    """있고 숫자이고 허용 범위 안이면 float, 아니면 None."""
    v = measurements.get(key) if isinstance(measurements, dict) else None
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return None
    return float(v) if value_range[0] <= v <= value_range[1] else None


def _mid(lo_hi):
    return (lo_hi[0] + lo_hi[1]) / 2


def _soft_membership(x, centers):
    """값 x가 각 유형에 얼마나 속하는지. centers = {유형: 중심값}.
    중심값 순서대로 이웃한 두 중심 사이는 선형으로 나누고, 양 끝 바깥은 끝 유형이 전부 가진다. 합 = 1."""
    ordered = sorted(centers.items(), key=lambda kv: kv[1])
    out = {t: 0.0 for t in TYPES}
    if x <= ordered[0][1]:
        out[ordered[0][0]] = 1.0
    elif x >= ordered[-1][1]:
        out[ordered[-1][0]] = 1.0
    else:
        for (t1, c1), (t2, c2) in zip(ordered, ordered[1:]):
            if c1 <= x <= c2:
                out[t2] = (x - c1) / (c2 - c1)
                out[t1] = 1.0 - out[t2]
                break
    return out


def chest_depth_membership(depth_cm, age=None):
    """가슴두께(cm) → {"S","W","N"} 소속 비율. W < N < S 순으로 두꺼워진다. 가운데는 각 구간의 중간값.
    S 구간은 위쪽이 열려 있어서(212~285mm) 중심을 '아래 경계 + 적당한 편 폭의 절반'으로 둔다."""
    b = CHEST_DEPTH_BANDS[age_band(age)]
    centers = {"W": _mid(b["W"]), "N": _mid(b["N"]),
               "S": b["S"][0] + (b["N"][1] - b["N"][0]) / 2}
    return _soft_membership(depth_cm * 10, centers)


def shoulder_slope_membership(angle_deg, age=None):
    """어깨 경사각(도) → 소속 비율. N(일자) < S(적당) < W(쳐진) 순으로 각도가 커진다.
    왼쪽·오른쪽 구간 중간값의 평균을 각 유형의 중심으로 쓴다 (카메라는 좌우 평균 각도 하나를 준다)."""
    b = SHOULDER_SLOPE_BANDS[age_band(age)]
    centers = {t: (_mid(b[t][0]) + _mid(b[t][1])) / 2 for t in TYPES}
    return _soft_membership(angle_deg, centers)


def score_camera_shape(measurements):
    """카메라 보조 지표 점수 {"S":…, "W":…, "N":…}. 두 값이 모두 없으면 전부 0."""
    scores = {t: 0.0 for t in TYPES}
    if not measurements:
        return scores
    age = measurements.get("age")
    depth = _optional_value(measurements, "chest_depth", CHEST_DEPTH_RANGE)
    if depth is not None:
        for t, share in chest_depth_membership(depth, age).items():
            scores[t] += share * W_CHEST_DEPTH
    slope = _optional_value(measurements, "shoulder_slope", SHOULDER_SLOPE_RANGE)
    if slope is not None:
        for t, share in shoulder_slope_membership(slope, age).items():
            scores[t] += share * W_SHOULDER_SLOPE
    return scores


# ═════════════════════════════════════════════
# [작업 2] 비율 맞추기(정규화) + 0.6/0.4 합산
#   설문과 아바타는 원점수 범위가 다르므로, 정규화로 두 데이터의 점수 체계를 맞춘 다음
#   각각 합이 1인 비율로 바꾼 뒤 합산한다. (설문점수는 0.6/아바타점수는 0.4)
# ═════════════════════════════════════════════
def normalize(scores):
    """합이 1인 비율로 바꾼다. 전부 0이면 1/3씩."""
    total = sum(scores.values())
    if total == 0:
        return {t: 1 / 3 for t in TYPES}
    return {t: scores[t] / total for t in TYPES}


# 설문에서 타입마다 받을 수 있는 최고점 (선택지 최고점 × 문항 weight 의 합).
# 지금 표로는 S 15 / W 14 / N 13.5 라서, 원점수를 그대로 비율로 바꾸면 S가 유리하다.
SURVEY_MAX = {t: sum(max(opt[t] for opt in spec["options"].values()) * spec["weight"]
                     for spec in SURVEY.values()) for t in TYPES}


def survey_ratios(answers):
    """설문 비율. 타입별 최고점으로 먼저 나눈 뒤(같은 출발선) 합이 1인 비율로 바꾼다.

    최고점으로 나누지 않으면 무작위 답 2,187가지 중 1위가 S 42% / W 31% / N 27%로 기운다.
    튜닝용 21명 기준 정확도: 지금(나누지 않음) 81.0% → 나눔 95.2% (테스트 9명은 둘 다 9/9).
    """
    raw = score_survey(answers)
    if is_survey15(answers):
        # 새 설문은 답한 문항(7~15개)의 최고점으로 나눈다 → 몇 문항에서 멈췄든 같은 기준
        table = answered_survey15(answers)
        top = {t: sum(max(opt[t] for opt in spec["options"].values()) * spec["weight"]
                      for spec in table.values()) for t in TYPES}
        return normalize({t: raw[t] / top[t] for t in TYPES})
    return normalize({t: raw[t] / SURVEY_MAX[t] for t in TYPES})


def combine(survey_r, avatar_r, w_survey=W_SURVEY, w_avatar=W_AVATAR):
    """설문 비율 × 0.6 + 아바타 비율 × 0.4"""
    return {t: w_survey * survey_r[t] + w_avatar * avatar_r[t] for t in TYPES}


# ═════════════════════════════════════════════
# [작업 3-1] 혼합형 판정
#  설문,아바타 점수 바탕으로 대표 체형과 보조 체형을 정하고, 혼합형인지 판정.
#  S,W,N 중에서 가장 점수가 높은 체형과 두 번째로 높은 체형을 찾고, 둘의 점수 차이가 작으면 혼합형으로 판단
# ═════════════════════════════════════════════
def decide_type(ratios, mix_gap=MIX_GAP, tie_order=TYPES):
    # 동점이면 tie_order 순 (기본 S, W, N). 소수 계산 오차로 동점이 갈리지 않게 반올림해서 비교
    ranked = sorted(TYPES, key=lambda t: (-round(ratios[t], 9), tie_order.index(t)))
    first, second = ranked[0], ranked[1]
    confidence = ratios[first] - ratios[second]
    return {
        "ratios": dict(ratios),
        "primary": first,
        "secondary": second,
        "confidence": confidence,
        "is_mixed": confidence < mix_gap,
    }


# ═════════════════════════════════════════════
# [작업 3-2] 입력 예외 처리
# ═════════════════════════════════════════════

# 입력 데이터에 문제가 생겼을 때 사용할 사용자 정의 오류를 만드는 코드
class InputError(ValueError):
    """입력 누락·이상치일 때 발생한다. B·C는 이 에러를 잡아서 처리한다."""

# 치수 허용 범위 (여성 기준, 넉넉하게)
VALID_RANGE = {
    "height": (130, 200),
    "shoulder_width": (22, 50),
    "arm_length": (40, 80),
    "upper_body_length": (30, 75),
    "leg_length": (60, 120),
}

# 설문 답변을 정수로 변환
def _to_option(v):
    """1, "1", 1.0 모두 정수 1로 바꾼다. 안 되면 None."""
    try:
        f = float(v)
    except (TypeError, ValueError):
        return None
    return int(f) if f.is_integer() else None

# 설문 답변 검증
def validate_answers(answers):
    if not answers:
        raise InputError("설문 답이 없습니다.")
    if is_survey15(answers):
        _validate_survey15(answers)
        return
    for q, spec in SURVEY.items():
        if q not in answers or answers[q] in (None, ""):
            raise InputError(f"설문 {q}에 답하지 않았습니다.")
        if _to_option(answers[q]) not in spec["options"]:
            raise InputError(f"설문 {q}의 답은 1·2·3 중 하나여야 합니다: {answers[q]!r}")

def _validate_survey15(answers):
    """새 15문항: q1~q7은 꼭, q8~q15는 앞에서부터 이어서 (중간을 건너뛰면 오류)"""
    answered = answered_survey15(answers)
    if len(answered) < SURVEY15_MIN:
        missing = f"q{len(answered) + 1}"
        raise InputError(f"설문 {missing}에 답하지 않았습니다. 최소 {SURVEY15_MIN}문항은 답해야 합니다.")
    for q in SURVEY15:
        if q not in answered and answers.get(q) not in (None, ""):
            raise InputError(f"설문 {q} 앞의 문항에 답하지 않았습니다.")
    for q, spec in answered.items():
        if _to_option(answers[q]) not in spec["options"]:
            raise InputError(f"설문 {q}의 답은 1·2·3 중 하나여야 합니다: {answers[q]!r}")


# 신체 치수 검증
def validate_measurements(measurements):
    for key in REQUIRED_MEASURES:
        v = measurements.get(key)
        name = f"{MEASURE_NAMES[key]}({key})"
        if v is None or v == "":
            raise InputError(f"{name} 값이 없습니다. 사진 인식에 실패했을 수 있습니다.")
        if not isinstance(v, (int, float)):
            raise InputError(f"{name}가 숫자가 아닙니다: {v!r}")
        lo, hi = VALID_RANGE[key]
        if not lo <= v <= hi:
            raise InputError(f"{name} {v}cm가 허용 범위({lo}~{hi}cm)를 벗어납니다.")
    m = measurements
    if m["upper_body_length"] + m["leg_length"] >= m["height"]:
        raise InputError("상체길이 + 다리길이가 키보다 깁니다.")
    if m["arm_length"] >= m["height"] / 2:
        raise InputError("팔길이가 키의 절반 이상입니다.")


# ═════════════════════════════════════════════
# [작업 4] 치수 구간 기준값 (여성 기준)
# ═════════════════════════════════════════════

# 아바타의 신체 비율을 체형 점수로 변환할 때 사용하는 기준값
THRESHOLDS = {
    "upper_leg_ratio": (0.5511, 0.5639),
    "shoulder_ratio": (0.1923, 0.1982),
    "arm_ratio": (0.3199, 0.3223),
}


# ═════════════════════════════════════════════
# B·C가 호출하는 함수
# ═════════════════════════════════════════════
def classify(answers, measurements=None):
    """
    answers      : {"q1": 1, "q2": 3, ..., "q7": 2}   (1·2·3, 옛 7문항)
                   또는 {"version": 2, "q1": 1, ..., "q7"~"q15": …}   (새 15문항, 7개 이상 앞에서부터)
    measurements : {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1,
                    "upper_body_length": 47.0, "leg_length": 86.1}   (cm)
                   없으면 None → 설문만으로 계산.
                   선택 키(있으면 점수에 더함): "chest_depth"(가슴두께 cm), "shoulder_slope"(어깨 경사각 도), "age"(나이)
    """
    validate_answers(answers)                              # 설문 답변 검증
    
    survey_r = survey_ratios(answers)                      # 설문 점수 계산 및 정규화 (타입별 최고점 보정)
    tie = survey_tie_order(answers)                        # 동점일 때 먼저 볼 체형 순서
    
    if measurements is None:
        return decide_type(survey_r, tie_order=tie)        # 신체 치수가 없는 경우, 
    
    validate_measurements(measurements)                    # 신체 치수 검증
    
    avatar_r = normalize(score_avatar(measurements))       # 아바타 점수 계산 및 최종 판정
    return decide_type(combine(survey_r, avatar_r), tie_order=tie)


# ═════════════════════════════════════════════
# 가상의 사람으로 테스트
# ═════════════════════════════════════════════
TYPE_NAMES = {"S": "스트레이트", "W": "웨이브", "N": "내추럴"}


def _pct(r):
    return "   ".join(f"{t} {r[t] * 100:4.1f}%" for t in TYPES)


def explain(title, answers, measurements=None):
    """입력 → 계산 과정 → 판정 → 출력 순서로 보기 쉽게 보여준다."""
    import json
    line = "─" * 64
    print(f"\n{line}\n▶ {title}\n{line}")

    print("[1] 입력")
    table = SURVEY15 if is_survey15(answers) else SURVEY   # 새 설문이면 15문항 표
    for q, spec in table.items():
        if is_survey15(answers) and answers.get(q) in (None, ""):
            continue                                       # 새 설문은 7번 이후 안 답한 문항이 있을 수 있다
        n = _to_option(answers.get(q))
        choice = spec["choices"].get(n, f"(잘못된 답: {answers.get(q)!r})")
        print(f"    {q.upper()} {spec['text']}")
        print(f"        → {n}번 {choice}")
    if measurements is None:
        print("    치수: (없음)")
    else:
        print("    치수: " + ", ".join(
            f"{MEASURE_NAMES.get(k, k)} " + (f"{v}cm" if v not in (None, "") else "(값 없음)")
            for k, v in measurements.items()))

    try:
        result = classify(answers, measurements)
    except InputError as e:
        print("\n[2] 결과  (작업 3: 입력 예외 처리)")
        print("    ✖ 입력 오류 → 체형을 계산하지 않음")
        print(f"      이유: {e}")
        return

    print("\n[2] 계산 과정  (작업 1: 점수 계산 → 작업 2: 정규화·합산)")
    survey_r = survey_ratios(answers)
    print(f"    설문 비율          {_pct(survey_r)}")
    if measurements is None:
        print("    (치수가 없어서 설문 비율이 그대로 최종 비율)")
    else:
        avatar_r = normalize(score_avatar(measurements))
        print(f"    아바타 비율        {_pct(avatar_r)}")
        print(f"    합산 ({W_SURVEY}/{W_AVATAR})     {_pct(result['ratios'])}")

    p, s = result["primary"], result["secondary"]
    print("\n[3] 판정  (작업 3: 혼합형 판정)")
    print(f"    1순위: {TYPE_NAMES[p]} ({p})   2순위: {TYPE_NAMES[s]} ({s})")
    print(f"    1·2위 차이(confidence): {result['confidence']:.3f}  (혼합형 기준 {MIX_GAP})")
    if result["is_mixed"]:
        print(f"    ⇒ 차이가 기준보다 작음 → {TYPE_NAMES[p]}+{TYPE_NAMES[s]} 혼합형")
    else:
        print(f"    ⇒ 차이가 기준보다 큼 → {TYPE_NAMES[p]} 체형")

    print("\n[4] B·C에 넘기는 출력 (classify 반환값)")
    for row in json.dumps(result, indent=2).splitlines():
        print("    " + row)


if __name__ == "__main__":
    # 새 설문 15문항: "version": 2, 답 1·2·3 = 보기 A(스트레이트)·B(웨이브)·C(내추럴)
    explain("테스트 1: 15문항을 모두 A로 답한 사람 (S 특징이 뚜렷)",
            {"version": 2, **{f"q{i}": 1 for i in range(1, 16)}},
            {"height": 163.0, "shoulder_width": 31.8, "arm_length": 52.0,
             "upper_body_length": 45.0, "leg_length": 86.5})

    explain("테스트 2: 10문항까지만 답하고 결과를 본 사람, 모두 B (W 특징이 뚜렷)",
            {"version": 2, **{f"q{i}": 2 for i in range(1, 11)}},
            {"height": 158.0, "shoulder_width": 29.4, "arm_length": 50.6,
             "upper_body_length": 47.0, "leg_length": 80.5})

    explain("테스트 3: S 답과 N 답이 섞인 사람 (혼합형 판정 확인)",
            {"version": 2, "q1": 3, "q2": 1, "q3": 1, "q4": 3, "q5": 2, "q6": 1, "q7": 3,
             "q8": 3, "q9": 1, "q10": 3, "q11": 1, "q12": 3, "q13": 1, "q14": 3, "q15": 1},
            {"height": 165.0, "shoulder_width": 32.6, "arm_length": 53.9,
             "upper_body_length": 45.5, "leg_length": 87.0})

    explain("테스트 4: 어깨너비 값이 비어 있는 경우 (입력 예외 처리 확인)",
            {"version": 2, **{f"q{i}": 1 for i in range(1, 16)}},
            {"height": 162.0, "shoulder_width": None, "arm_length": 52.0,
             "upper_body_length": 45.0, "leg_length": 85.0})
