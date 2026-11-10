"""입력 오차(--noise-sd)와 시드를 바꿔 train_photo_model.py를 여러 번 돌린다. 이미 끝난 실행은 건너뛴다.

  python run_compare.py --inputs <입력 폴더> --out <runs 폴더> --sds 1.0 1.5 2.0 2.5 --seeds 0 1 2 --outputs 4 --parallel 2

끝나면 summarize_runs.py <runs 폴더> 로 표를 만든다. GPU 하나에 --parallel 2까지는 거의 같은 속도로 돈다(16GB 기준).
"""
import argparse
import itertools
import os
import subprocess
import sys
import time

ap = argparse.ArgumentParser()
ap.add_argument("--inputs", required=True)
ap.add_argument("--out", required=True)
ap.add_argument("--sds", type=float, nargs="+", default=[1.0, 1.5, 2.0, 2.5])
ap.add_argument("--seeds", type=int, nargs="+", default=[0, 1, 2])
ap.add_argument("--outputs", default="4", choices=["3", "4"])
ap.add_argument("--parallel", type=int, default=2)
ap.add_argument("--epochs", type=int, default=12)
args = ap.parse_args()

here = os.path.dirname(os.path.abspath(__file__))
script = os.path.join(here, "train_photo_model.py")
os.makedirs(args.out, exist_ok=True)

jobs = []
for sd, seed in itertools.product(args.sds, args.seeds):
    tag = f"sd{sd:g}_o{args.outputs}_s{seed}"
    if os.path.exists(os.path.join(args.out, tag, "results.json")):
        print("건너뜀(이미 있음):", tag, flush=True)
        continue
    cmd = [sys.executable, script, "--inputs", args.inputs, "--out", args.out, "--noise-sd", str(sd),
           "--outputs", args.outputs, "--seed", str(seed), "--epochs", str(args.epochs)]
    jobs.append((tag, cmd))

running = []
t0 = time.time()
done = 0
total = len(jobs)
while jobs or running:
    while jobs and len(running) < args.parallel:
        tag, cmd = jobs.pop(0)
        log = open(os.path.join(args.out, f"{tag}.log"), "w")
        running.append((tag, subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT), log))
        print(f"시작: {tag} ({time.time() - t0:.0f}초)", flush=True)
    for item in list(running):
        tag, proc, log = item
        if proc.poll() is not None:
            log.close()
            running.remove(item)
            done += 1
            print(f"끝: {tag} 종료코드 {proc.returncode} ({done}/{total}, {time.time() - t0:.0f}초)", flush=True)
    time.sleep(5)
print("모두 끝", flush=True)
