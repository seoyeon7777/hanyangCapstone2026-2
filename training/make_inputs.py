"""학습 입력 파일 만들기 (사진 모델 학습용).

만드는 것 (모두 --out 폴더에):
  prior_coef_final.json  기준선(키·몸무게·나이 일차식) 계수. README 9-3의 19~79세 계수와 같다
  slices_women.csv       AI Hub 151 여성의 키·몸무게·나이와 줄자 실측(젖가슴·허리·엉덩이·넙다리 둘레)

실행 (WSL 안의 가상환경에서):
  python make_inputs.py --out /mnt/d/workspace/Capstone/데이터/training_work

151 신체정보 CSV는 개인정보가 들어 있는 원본이라 저장소에 올리지 않는다. 이 스크립트는 읽기만 한다.
주의: 151 CSV는 소수점이 쉼표("98,6")이고 인코딩이 utf-8/cp949로 섞여 있다. 여성은 사람 번호가 F로 시작한다.
"""
import argparse
import csv
import glob
import io
import json
import os

DEFAULT_SRC = ("/mnt/d/workspace/Capstone/데이터/체형 데이터/151.한국인 전신 형상 및 치수 측정 데이터/"
               "01.데이터/1.Training/원천데이터_230524add/신체정보데이터/*.csv")

# 계수 순서 [절편, 키, 몸무게, 나이]  (training/README.md 9-3, 19~79세, 입력 오차를 섞어 학습한 값)
COEF = {
    "bust": [81.8554, -0.2593, 0.7417, 0.1177],
    "waist": [77.0192, -0.3637, 0.8587, 0.2033],
    "hip": [62.6130, 0.0028, 0.5509, -0.0367],
    "thigh": [41.7391, -0.0476, 0.4147, -0.0716],
}


def num(x):
    try:
        return float(str(x).replace(",", "."))
    except (TypeError, ValueError):
        return None


def read_text(path):
    raw = open(path, "rb").read()
    for enc in ("utf-8-sig", "cp949"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            pass
    return raw.decode("utf-8", "ignore")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--src", default=DEFAULT_SRC, help="151 신체정보 CSV glob")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    json.dump(COEF, open(os.path.join(args.out, "prior_coef_final.json"), "w"))

    cols = ("키", "몸무게", "나이", "젖가슴둘레", "허리둘레", "엉덩이둘레", "넙다리둘레")
    rows, skipped = [], 0
    for path in sorted(glob.glob(args.src)):
        pid = os.path.basename(path)[:-4]
        if not pid.startswith("F"):  # 여성만
            continue
        r = list(csv.reader(io.StringIO(read_text(path))))
        if len(r) < 3:
            skipped += 1
            continue
        c = {n: v for n, v in zip(r[1], r[2]) if n}
        v = [num(c.get(k)) for k in cols]
        if None in v:
            skipped += 1
            continue
        rows.append([pid] + v)

    with open(os.path.join(args.out, "slices_women.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["id", "key", "kg", "age", "tape_bust", "tape_waist", "tape_hip", "tape_thigh"])
        w.writerows(rows)
    print(f"여성 {len(rows)}명 저장, 건너뜀 {skipped}")


if __name__ == "__main__":
    main()
