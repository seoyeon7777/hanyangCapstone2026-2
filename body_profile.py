"""
A. 체형 분류 (body_profile.py)

7개 설문 답 + 아바타 치수를 받아 S(스트레이트)·W(웨이브)·N(내추럴) 비율과 판정 결과를 낸다.

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

# 설문 점수 계산
def score_survey(answers):
    """설문 원점수 {"S":…, "W":…, "N":…} = 선택지 점수 × 문항 weight 의 합"""
    scores = {t: 0.0 for t in TYPES}
    for q, spec in SURVEY.items():
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
    return normalize({t: raw[t] / SURVEY_MAX[t] for t in TYPES})


def combine(survey_r, avatar_r, w_survey=W_SURVEY, w_avatar=W_AVATAR):
    """설문 비율 × 0.6 + 아바타 비율 × 0.4"""
    return {t: w_survey * survey_r[t] + w_avatar * avatar_r[t] for t in TYPES}


# ═════════════════════════════════════════════
# [작업 3-1] 혼합형 판정
#  설문,아바타 점수 바탕으로 대표 체형과 보조 체형을 정하고, 혼합형인지 판정.
#  S,W,N 중에서 가장 점수가 높은 체형과 두 번째로 높은 체형을 찾고, 둘의 점수 차이가 작으면 혼합형으로 판단
# ═════════════════════════════════════════════
def decide_type(ratios, mix_gap=MIX_GAP):
    ranked = sorted(TYPES, key=lambda t: ratios[t], reverse=True)  # 동점이면 S, W, N 순
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
    for q, spec in SURVEY.items():
        if q not in answers or answers[q] in (None, ""):
            raise InputError(f"설문 {q}에 답하지 않았습니다.")
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
    answers      : {"q1": 1, "q2": 3, ..., "q7": 2}   (1·2·3)
    measurements : {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1,
                    "upper_body_length": 47.0, "leg_length": 86.1}   (cm)
                   없으면 None → 설문만으로 계산
    """
    validate_answers(answers)                              # 설문 답변 검증
    
    survey_r = survey_ratios(answers)                      # 설문 점수 계산 및 정규화 (타입별 최고점 보정)
    
    if measurements is None:
        return decide_type(survey_r)                       # 신체 치수가 없는 경우, 
    
    validate_measurements(measurements)                    # 신체 치수 검증
    
    avatar_r = normalize(score_avatar(measurements))       # 아바타 점수 계산 및 최종 판정
    return decide_type(combine(survey_r, avatar_r))    


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
    for q, spec in SURVEY.items():
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
    explain("테스트 1: 설문을 모두 1번으로 답한 사람 (S 특징이 뚜렷)",
            {"q1": 1, "q2": 1, "q3": 1, "q4": 1, "q5": 1, "q6": 1, "q7": 1},
            {"height": 163.0, "shoulder_width": 31.8, "arm_length": 52.0,
             "upper_body_length": 45.0, "leg_length": 86.5})

    explain("테스트 2: 설문을 모두 2번으로 답한 사람 (W 특징이 뚜렷)",
            {"q1": 2, "q2": 2, "q3": 2, "q4": 2, "q5": 2, "q6": 2, "q7": 2},
            {"height": 158.0, "shoulder_width": 29.4, "arm_length": 50.6,
             "upper_body_length": 47.0, "leg_length": 80.5})

    explain("테스트 3: S 답과 N 답이 섞인 사람 (혼합형 판정 확인)",
            {"q1": 3, "q2": 1, "q3": 1, "q4": 3, "q5": 2, "q6": 1, "q7": 3},
            {"height": 165.0, "shoulder_width": 32.6, "arm_length": 53.9,
             "upper_body_length": 45.5, "leg_length": 87.0})

    explain("테스트 4: 어깨너비 값이 비어 있는 경우 (입력 예외 처리 확인)",
            {"q1": 1, "q2": 1, "q3": 1, "q4": 1, "q5": 1, "q6": 1, "q7": 1},
            {"height": 162.0, "shoulder_width": None, "arm_length": 52.0,
             "upper_body_length": 45.0, "leg_length": 85.0})
