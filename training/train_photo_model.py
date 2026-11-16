"""사진 치수 모델 학습: 정면·옆면 사진 두 장 + [키, 몸무게, 나이, 정면 가로세로 비율, 옆면 가로세로 비율]
→ 기준선(키·몸무게·나이 일차식) 대비 잔차(표준화). 앱의 body_photo.onnx를 만든 것과 같은 구조다. (README 5·9장)

- 데이터: 151 여성 사진을 미리 가공한 배열(dl151/imgs.npy, meta.pkl, prep151.py가 만든 것)과 make_inputs.py가 만든 표
- 점검용 사람 8%(약 35명)는 항상 같은 사람이다(--seed와 무관). 실행끼리 공정하게 비교하려는 것이다
- 입력 오차: 학습 때 키·몸무게에 평균 0, 표준편차 --noise-sd의 오차를 섞는다(사용자가 직접 입력한 값의 오차를 흉내)
- 출력: --outputs 3 = 젖가슴·허리·엉덩이, 4 = 여기에 넙다리(허벅지) 추가
- 결과: <out>/<tag>/ 에 results.json, model.pt, model.onnx, meta.json

실행 예 (WSL 가상환경, GPU 필요):
  python train_photo_model.py --inputs /mnt/d/workspace/Capstone/데이터/training_work \\
      --out /mnt/d/workspace/Capstone/데이터/training_work/runs --noise-sd 1.5 --outputs 4 --seed 0

주의: Windows에서 torch가 Smart App Control에 막히면 WSL에서 돌린다. README 7장 참고.
"""
import argparse
import json
import os
import pickle

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as Fn
import torchvision

ap = argparse.ArgumentParser()
ap.add_argument("--noise-sd", type=float, default=2.5, help="학습 때 키·몸무게에 섞는 오차의 표준편차")
ap.add_argument("--outputs", choices=["3", "4"], default="4", help="3=젖가슴·허리·엉덩이, 4=+넙다리")
ap.add_argument("--seed", type=int, default=0)
ap.add_argument("--epochs", type=int, default=12)
ap.add_argument("--data-dir", default="/mnt/d/workspace/Capstone/데이터/dl151", help="imgs.npy, meta.pkl이 있는 폴더")
ap.add_argument("--inputs", required=True, help="slices_women.csv, prior_coef_final.json이 있는 폴더(make_inputs.py의 --out)")
ap.add_argument("--out", required=True)
ap.add_argument("--tag", default=None)
args = ap.parse_args()

EP, SEED, TRSD = args.epochs, args.seed, args.noise_sd
T = ("bust", "waist", "hip", "thigh")[:int(args.outputs)]
tag = args.tag or f"sd{TRSD:g}_o{args.outputs}_s{SEED}"
out_dir = os.path.join(args.out, tag)
os.makedirs(out_dir, exist_ok=True)

torch.manual_seed(SEED)
np.random.seed(SEED)

imgs = np.load(os.path.join(args.data_dir, "imgs.npy"))
meta = pickle.load(open(os.path.join(args.data_dir, "meta.pkl"), "rb"))
# 점검용 사람 목록이 출력 개수와 상관없이 같도록, 네 정답이 모두 있는 사람만 쓴다
sl = pd.read_csv(os.path.join(args.inputs, "slices_women.csv"))
sl = sl.dropna(subset=["tape_bust", "tape_waist", "tape_hip", "tape_thigh", "kg", "key", "age"]).set_index("id")
C = json.load(open(os.path.join(args.inputs, "prior_coef_final.json")))
CO = np.array([C[t] for t in T])


def base(key, kg, age):
    """기준선: 절편 + 키·몸무게·나이 일차식 (나이는 19~79로 자른다)"""
    return CO[:, 0][None] + np.outer(key, CO[:, 1]) + np.outer(kg, CO[:, 2]) + np.outer(np.clip(age, 19, 79), CO[:, 3])


grp = {}
for i, m in enumerate(meta):
    if m["pid"] in sl.index:
        grp.setdefault((m["pid"], m["cloth"]), {}).setdefault(m["view"], []).append(i)
pairs = [(k[0], k[1], f, s) for k, v in grp.items() if "front" in v and "side" in v for f in v["front"] for s in v["side"]]
allp = sorted({p[0] for p in pairs})
np.random.RandomState(1).shuffle(allp)  # 점검용 사람은 seed와 무관하게 고정
val_p = set(allp[:int(len(allp) * 0.08)])
tr_pairs = [p for p in pairs if p[0] not in val_p]
va_pairs = [p for p in pairs if p[0] in val_p]


def arr(ps):
    pid = np.array([p[0] for p in ps])
    return dict(pid=pid, cloth=np.array([p[1] for p in ps]), FI=np.array([p[2] for p in ps]), SI=np.array([p[3] for p in ps]),
                key=sl.loc[pid, "key"].to_numpy(float), kg=sl.loc[pid, "kg"].to_numpy(float), age=sl.loc[pid, "age"].to_numpy(float),
                Y=np.stack([sl.loc[pid, f"tape_{t}"].to_numpy(float) for t in T], 1))


TR, VA = arr(tr_pairs), arr(va_pairs)
R0 = TR["Y"] - base(TR["key"], TR["kg"], TR["age"])
mu, sd = R0.mean(0), R0.std(0)


def tab_of(d, key, kg):
    wh = lambda idx: np.array([(meta[i]["wpx"] / meta[i]["hpx"] - 0.4) / 0.15 for i in idx])
    return np.column_stack([(key - 160) / 8, (kg - 55) / 10, (d["age"] - 35) / 15, wh(d["FI"]), wh(d["SI"])]).astype(np.float32)


dev = "cuda"
X = torch.from_numpy(imgs).to(dev)
mean = torch.tensor([0.485, 0.456, 0.406], device=dev).view(1, 3, 1, 1)
std = torch.tensor([0.229, 0.224, 0.225], device=dev).view(1, 3, 1, 1)


def prep(idx, train):
    x = X[idx].permute(0, 3, 1, 2).float() / 255
    if train:  # 크기·위치·밝기 흔들기, 저해상도, 잡음 (앱 조건 흉내. README 9-4: 저해상도·잡음 증강은 필수)
        n = len(idx)
        sc = 1 + (torch.rand(n, device=dev) - 0.5) * 0.2
        tx = (torch.rand(n, device=dev) - 0.5) * 0.12
        ty = (torch.rand(n, device=dev) - 0.5) * 0.12
        th = torch.zeros(n, 2, 3, device=dev)
        th[:, 0, 0] = sc
        th[:, 1, 1] = sc
        th[:, 0, 2] = tx
        th[:, 1, 2] = ty
        x = Fn.grid_sample(x - 1, Fn.affine_grid(th, x.shape, align_corners=False), padding_mode="zeros", align_corners=False) + 1
        x = (x * (0.85 + 0.3 * torch.rand(n, 1, 1, 1, device=dev))).clamp(0, 1)
        r = 0.4 + 0.6 * torch.rand(1).item()
        s = max(32, int(224 * r))
        x = Fn.interpolate(Fn.interpolate(x, size=(s, s), mode="bilinear", align_corners=False), size=(224, 224), mode="bilinear", align_corners=False)
        x = (x + 0.02 * torch.randn_like(x)).clamp(0, 1)
    return (x - mean) / std


class Net(nn.Module):
    def __init__(self, nout):
        super().__init__()
        bb = torchvision.models.resnet18(weights="IMAGENET1K_V1")
        bb.fc = nn.Identity()
        self.bb = bb
        self.t = nn.Sequential(nn.Linear(5, 64), nn.ReLU())
        self.h = nn.Sequential(nn.Dropout(0.3), nn.Linear(512 * 2 + 64, 128), nn.ReLU(), nn.Linear(128, nout))

    def forward(self, xf, xs, t):
        f = self.bb(torch.cat([xf, xs], 0))
        n = len(t)
        return self.h(torch.cat([f[:n], f[n:], self.t(t)], 1))


net = Net(len(T)).to(dev)
FIt, SIt = torch.from_numpy(TR["FI"]).to(dev), torch.from_numpy(TR["SI"]).to(dev)
opt = torch.optim.AdamW([{"params": net.bb.parameters(), "lr": 1e-4},
                         {"params": list(net.t.parameters()) + list(net.h.parameters()), "lr": 5e-4}], weight_decay=1e-2)
n_tr = len(tr_pairs)
BS = 32
steps = EP * (n_tr // BS)
sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=[3e-4, 1e-3], total_steps=steps)
rg = np.random.RandomState(SEED)
print(f"[{tag}] 출력 {T} 학습쌍 {n_tr} 점검쌍 {len(va_pairs)} (점검 사람 {len(val_p)}명) mu {np.round(mu, 3).tolist()} sd {np.round(sd, 3).tolist()}", flush=True)

for ep in range(EP):
    net.train()
    perm = rg.permutation(n_tr)
    for i in range(0, n_tr - BS + 1, BS):
        b = perm[i:i + BS]
        key = TR["key"][b] + TRSD * rg.randn(BS)
        kg = TR["kg"][b] + TRSD * rg.randn(BS)
        R = TR["Y"][b] - base(key, kg, TR["age"][b])
        Rn = torch.from_numpy(((R - mu) / sd).astype(np.float32)).to(dev)
        d = {"age": TR["age"][b], "FI": TR["FI"][b], "SI": TR["SI"][b]}
        tb = torch.from_numpy(tab_of(d, key, kg)).to(dev)
        j = torch.from_numpy(b).to(dev)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            out = net(prep(FIt[j], True), prep(SIt[j], True), tb)
        loss = Fn.smooth_l1_loss(out.float(), Rn)
        opt.zero_grad()
        loss.backward()
        opt.step()
        sched.step()
    print(f"[{tag}] epoch {ep + 1}/{EP}", flush=True)

net.eval()
# 점검: 학습에 안 쓴 사람에서 모델과 기준선(+편향)의 오차. 입력 정확 / 약간 틀림(가정A) / 많이 틀림(가정B)
# 가정 값은 임의다. (몸무게 편향, 몸무게 흩어짐, 키 편향, 키 흩어짐)
SCEN = {"정확": (0, 0, 0, 0), "가정A": (-2, 2, 1, 2), "가정B": (-4, 3, 2, 3)}
rt = np.random.RandomState(5)
results = {}
for name, (kb, ks, hb, hs) in SCEN.items():
    users = {p: (rt.randn(), rt.randn()) for p in val_p}
    key = VA["key"] + hb + hs * np.array([users[p][0] for p in VA["pid"]])
    kg = VA["kg"] + kb + ks * np.array([users[p][1] for p in VA["pid"]])
    B = base(key, kg, VA["age"])
    tb = torch.from_numpy(tab_of(VA, key, kg)).to(dev)
    FI_, SI_ = torch.from_numpy(VA["FI"]).to(dev), torch.from_numpy(VA["SI"]).to(dev)
    P = np.zeros_like(B)
    with torch.no_grad():
        for i in range(0, len(B), 128):
            j = torch.arange(i, min(i + 128, len(B)), device=dev)
            P[i:i + 128] = net(prep(FI_[j], False), prep(SI_[j], False), tb[j]).float().cpu().numpy() * sd + mu
    df = pd.DataFrame({"pid": VA["pid"], "cloth": VA["cloth"]})
    for k in range(len(T)):
        df[f"y{k}"] = VA["Y"][:, k]
        df[f"m{k}"] = B[:, k] + P[:, k]
        df[f"b{k}"] = B[:, k] + mu[k]
    g = df.groupby(["pid", "cloth"]).mean(numeric_only=True)  # 사람·옷 단위로 묶어 평균 (README 9-4와 같은 방식)
    mae = lambda c: [round(float((g[f"{c}{k}"] - g[f"y{k}"]).abs().mean()), 3) for k in range(len(T))]
    bias = lambda c: [round(float((g[f"{c}{k}"] - g[f"y{k}"]).mean()), 3) for k in range(len(T))]
    results[name] = dict(model=mae("m"), baseline=mae("b"), model_bias=bias("m"), baseline_bias=bias("b"))
    print(f"[{tag}] {name} 모델 {results[name]['model']} 기준선+편향 {results[name]['baseline']}", flush=True)

json.dump(dict(tag=tag, targets=list(T), noise_sd=TRSD, seed=SEED, epochs=EP, n_val_people=len(val_p), results=results),
          open(os.path.join(out_dir, "results.json"), "w"), ensure_ascii=False, indent=1)

torch.save(net.state_dict(), os.path.join(out_dir, "model.pt"))
json.dump(dict(mu=mu.tolist(), sd=sd.tolist(), targets=list(T), coef=C, tab_norm=dict(key=[160, 8], kg=[55, 10], age=[35, 15], wh=[0.4, 0.15]),
               imagenet_mean=[0.485, 0.456, 0.406], imagenet_std=[0.229, 0.224, 0.225], image_size=224, train_noise_sd=TRSD),
          open(os.path.join(out_dir, "meta.json"), "w"), ensure_ascii=False)
# ONNX 내보내기 (정면, 옆면, 표 입력 → 표준화 잔차)
cpu = Net(len(T))
cpu.load_state_dict(torch.load(os.path.join(out_dir, "model.pt"), map_location="cpu"))
cpu.eval()
torch.onnx.export(cpu, (torch.randn(1, 3, 224, 224), torch.randn(1, 3, 224, 224), torch.randn(1, 5)), os.path.join(out_dir, "model.onnx"),
                  input_names=["front", "side", "tab"], output_names=["residual"], opset_version=17, dynamo=False)
print(f"[{tag}] 저장 완료 {out_dir}", flush=True)
