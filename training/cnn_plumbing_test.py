"""BMnet 방식 모델이 우리 입력으로 끝까지 도는지 확인하는 '배관 점검' (정확도 평가가 아니다).
정면·옆면 윤곽을 한 장으로 붙이고 키·몸무게 채널을 쌓아 MNASNet에 넣어 몇 번 학습한다.
사람 2명으로 하는 학습이라 '손실이 줄어드는지, 모양·메모리·저장이 맞는지'만 본다.
사용: python cnn_plumbing_test.py <New_Sample.zip> <출력 폴더>
"""
import io
import os
import sys
import time
import zipfile

import numpy as np
import torch
import torch.nn as nn
from PIL import Image, ImageOps
from torchvision.models import mnasnet0_5

from features import Measurer
import sample_baseline_data as sbd   # 아래에서 만든 작은 도우미

ZIP, OUT = sys.argv[1], sys.argv[2]
CANVAS_H, CANVAS_W = 256, 128         # 윤곽 한 장의 크기. 정면|옆면을 가로로 붙인다 (256x256)
TARGETS = ["젖가슴둘레", "허리둘레", "엉덩이둘레", "팔길이", "어깨사이너비", "샅높이"]


def normalize_sil(sil):
    """윤곽을 몸 높이 기준 고정 크기로 맞춘다 (몸 높이가 항상 CANVAS_H의 90%)"""
    m = sil.to_mask()
    ys = np.where(m.any(axis=1))[0]
    top, bottom = ys[0], ys[-1]
    body_h = bottom - top + 1
    scale = 0.9 * CANVAS_H / body_h
    img = Image.fromarray((m[top:bottom + 1] * 255).astype(np.uint8))
    img = img.resize((max(1, round(img.width * scale)), round(body_h * scale)), Image.BILINEAR)
    canvas = Image.new("L", (CANVAS_W, CANVAS_H), 0)
    canvas.paste(img, (max(0, (CANVAS_W - img.width) // 2), round(0.05 * CANVAS_H)))
    return np.asarray(canvas, dtype=np.float32) / 255.0


def main():
    t0 = time.time()
    meas = Measurer()
    samples = sbd.collect(ZIP, meas, max_pairs_per_person=24)
    print("샘플 쌍:", len(samples), "(%.0f초)" % (time.time() - t0), flush=True)
    X, Y = [], []
    for s in samples:
        front = normalize_sil(s["front_sil"])
        side = normalize_sil(s["side_sil"])
        sil = np.concatenate([front, side], axis=1)                   # 256 x 256
        hc = np.full_like(sil, s["height"] / 200.0)
        wc = np.full_like(sil, s["weight"] / 150.0)
        X.append(np.stack([sil, hc, wc]))
        Y.append([s["gt"][t] for t in TARGETS])
    X = torch.tensor(np.array(X), dtype=torch.float32)
    Y = torch.tensor(np.array(Y), dtype=torch.float32)
    y_mean, y_std = Y.mean(0), Y.std(0) + 1e-6
    Yn = (Y - y_mean) / y_std

    net = mnasnet0_5(weights=None)
    net.classifier = nn.Sequential(nn.Dropout(0.0), nn.Linear(net.classifier[1].in_features, 128), nn.ReLU(),
                                   nn.Linear(128, len(TARGETS)))
    opt = torch.optim.Adam(net.parameters(), lr=1e-3)
    loss_fn = nn.L1Loss()
    net.train()
    losses = []
    for step in range(60):
        idx = torch.randint(0, len(X), (8,))
        opt.zero_grad()
        loss = loss_fn(net(X[idx]), Yn[idx])
        loss.backward()
        opt.step()
        losses.append(loss.item())
    print("손실 처음 5회 평균 %.3f → 마지막 5회 평균 %.3f" % (np.mean(losses[:5]), np.mean(losses[-5:])))
    net.eval()
    with torch.no_grad():
        pred = net(X) * y_std + y_mean
    err = (pred - Y).abs().mean(0)
    for t, e in zip(TARGETS, err):
        print("  %-8s 학습 데이터 평균절대오차 %.1f cm (같은 데이터로 맞춘 값이라 의미 없음, 버그 확인용)" % (t, e))
    os.makedirs(OUT, exist_ok=True)
    torch.save(net.state_dict(), os.path.join(OUT, "plumbing_model.pt"))
    print("저장:", os.path.join(OUT, "plumbing_model.pt"), "(%.0f초 총)" % (time.time() - t0))


if __name__ == "__main__":
    main()
