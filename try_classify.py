"""
classify() 사용 예시 (try_classify.py)

B·C는 아래 코드처럼 자기 파일(matching.py, feed.py 등)에서 classify()를 불러 쓰면 된다.
실행: 터미널에 python try_classify.py
※ body_profile.py와 같은 폴더에 있어야 한다.
"""
from body_profile import classify, InputError


def run(title, answers, measurements=None):
    print(f"\n▶ {title}")
    try:
        if measurements is None:
            result = classify(answers)                 # 설문만
        else:
            result = classify(answers, measurements)   # 설문 + 치수
    except InputError as e:
        print("  입력 오류:", e)                        # 여기서 재입력·사진 재촬영 안내
        return

    print("  결과:", result)
    print("  대표 체형:", result["primary"],
          "| 혼합형" if result["is_mixed"] else "")


# 설문 답: 새 설문 15문항. "version": 2를 꼭 넣고, q1부터 이어서 7~15개 (값 1·2·3 = 보기 A·B·C)
#   7번부터 "여기까지 하고 결과보기"가 있어서 q8 이후는 없을 수 있다
#   version이 없으면 옛 7문항(q1~q7) 표로 채점한다 (sample.csv·evaluate.py용)
answers = {"version": 2, "q1": 1, "q2": 3, "q3": 1, "q4": 2, "q5": 1, "q6": 1, "q7": 3,
           "q8": 1, "q9": 2, "q10": 1, "q11": 1, "q12": 1, "q13": 2, "q14": 1, "q15": 3}

# 치수 5개 (cm)
measurements = {"height": 165.0, "shoulder_width": 31.5, "arm_length": 53.1,
                "upper_body_length": 47.0, "leg_length": 86.1}

# 1. 설문 + 치수 (기본 사용법)
run("예시 1: 설문 + 치수", answers, measurements)

# 2. 치수가 아직 없을 때 → 설문만으로 계산
run("예시 2: 설문만", answers)

# 3. 7문항까지만 답하고 결과를 볼 때
run("예시 3: 7문항에서 멈춤", {k: v for k, v in answers.items() if k in ("version", "q1", "q2", "q3", "q4", "q5", "q6", "q7")})

# 4. 입력 오류 → InputError
run("예시 4: 설문 q4가 빠진 경우",
    {k: v for k, v in answers.items() if k != "q4"}, measurements)
run("예시 5: 사진 인식 실패로 어깨너비가 비어 있는 경우",
    answers, {**measurements, "shoulder_width": None})
