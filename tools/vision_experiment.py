"""상세 이미지의 글을 Gemini(이미지 입력)로 읽어 태깅이 늘어나는지 보는 실험. 내 PC에서 실행한다 (Colab 불필요).

준비:   pip install pillow          (없어도 되지만 긴 이미지를 조각으로 잘라 읽으려면 필요)
        set GEMINI_API_KEY=...       (모델을 바꾸려면 set GEMINI_MODEL=...)
실행:   python tools\\vision_experiment.py --limit 3
        python tools\\vision_experiment.py --ids 6746997,6272202
        python tools\\vision_experiment.py --data samples\\validation_items.json --out samples\\vision_new_result.json --limit 20 --max-images 6

하는 일: samples/detail_images.json 의 상세 이미지를 받아 -> 세로로 긴 이미지는 조각으로 자르고
         -> Gemini에 '보이는 글자만 그대로 옮겨 적어라'고 요청 -> '이름만' 대 '이름+읽힌 글'의 규칙 태그를 비교.
이미지를 못 내려받으면: samples/ocr_images/<상품id>/ 폴더에 이미지를 저장해 두면 그 파일을 쓴다.
결과: samples/vision_result.json. 읽힌 글은 반드시 눈으로도 확인할 것 (정확도 수치는 없다).
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import os
import sys
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import matching  # noqa: E402
import tagger_llm  # noqa: E402

CHUNK_H = 2400   # 조각이 길수록 호출 수가 준다 (예전 실험은 1600)
OVERLAP = 120
MAX_W = 960
HEADERS = {"User-Agent": "Mozilla/5.0", "Referer": "https://www.musinsa.com/"}
PROMPT = ("이 이미지는 의류 쇼핑몰 상품 상세 설명의 일부다. 이미지에 실제로 보이는 글자를 줄 단위로 그대로 옮겨 적어라. "
          "보이지 않는 내용을 추측하거나 요약하지 마라. 글자가 없으면 빈 문자열만 답해라. 모델 착용 설명이나 사이즈표의 숫자도 보이는 대로 적어라.")


def pick(seq: list, n: int) -> list:
    """이미지가 n장보다 많으면 앞쪽과 뒤쪽을 절반씩 고른다 (앞은 모델 사진, 사이즈표·소재 정보는 뒤쪽에 있는 경우가 많다)."""
    if len(seq) <= n:
        return list(seq)
    head = (n + 1) // 2
    return list(seq[:head]) + list(seq[len(seq) - (n - head):])


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def chunks(data: bytes) -> list[bytes]:
    try:
        from PIL import Image
    except ImportError:
        return [data]           # pillow가 없으면 통째로 보낸다 (긴 이미지는 글이 작게 줄어 덜 읽힌다)
    try:
        img = Image.open(io.BytesIO(data)).convert("RGB")
    except Exception:  # noqa: BLE001  (깨진 파일, 이미지가 아닌 응답)
        return []
    w, h = img.size
    if w < 50 or h < 50:                 # 구분선 같은 아주 작은 이미지는 건너뛴다
        return []
    if w > MAX_W:
        img = img.resize((MAX_W, max(1, int(h * MAX_W / w))))
        w, h = img.size
    out, y = [], 0
    while y < h:
        buf = io.BytesIO()
        img.crop((0, y, w, min(h, y + CHUNK_H))).save(buf, "JPEG", quality=88)
        out.append(buf.getvalue())
        if y + CHUNK_H >= h:
            break
        y += CHUNK_H - OVERLAP
    return out


_gate, _next = threading.Lock(), [0.0]


def _wait_turn(interval: float) -> None:
    """요청 시작 간격을 전체에서 interval초 이상으로 맞춘다. 대기만 겹치고 한도(분당 요청 수)는 지킨다."""
    with _gate:
        now = time.monotonic()
        t = max(now, _next[0])
        _next[0] = t + interval
    if t > now:
        time.sleep(t - now)


def read_chunk(jpg: bytes, model: str, key: str, interval: float = 0.0) -> str:
    for attempt in range(4):
        try:
            return _read_chunk_once(jpg, model, key, interval)
        except RuntimeError as e:
            if "429" in str(e) and attempt < 3:      # 분당 한도 초과: 길게 쉬고 다시 시도한다
                time.sleep(30 * (attempt + 1))
                continue
            raise


def _read_chunk_once(jpg: bytes, model: str, key: str, interval: float = 0.0) -> str:
    _wait_turn(interval)
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    body = {"contents": [{"parts": [{"text": PROMPT},
                                     {"inline_data": {"mime_type": "image/jpeg",
                                                      "data": base64.b64encode(jpg).decode()}}]}],
            "generationConfig": {"temperature": 0}}
    data = tagger_llm._post(url, {"Content-Type": "application/json", "x-goog-api-key": key}, body, 120)
    parts = data["candidates"][0]["content"].get("parts", [])
    return "\n".join(p.get("text", "") for p in parts).strip()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=str(ROOT / "samples" / "detail_images.json"))
    ap.add_argument("--out", default=str(ROOT / "samples" / "vision_result.json"), help="결과 파일 (새 상품 검증은 samples/vision_new_result.json)")
    ap.add_argument("--limit", type=int, default=3)
    ap.add_argument("--ids", default="")
    ap.add_argument("--sleep", type=float, default=4.0, help="요청 시작 간격 초 (무료 한도 분당 15회면 4초)")
    ap.add_argument("--workers", type=int, default=4, help="동시에 보내는 요청 수 (간격은 --sleep으로 지켜진다)")
    ap.add_argument("--max-images", type=int, default=6, help="상품당 읽을 이미지 수 (앞쪽부터. 뒤쪽은 반품 안내가 많다)")
    ap.add_argument("--force", action="store_true", help="이미 읽은 상품도 다시 읽는다")
    a = ap.parse_args()

    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        raise SystemExit("환경변수 GEMINI_API_KEY가 없습니다. 키를 채팅에 적지 말고 본인 PC 환경변수로 설정하세요.")
    model = os.environ.get("GEMINI_MODEL", tagger_llm.DEFAULT_MODELS["gemini"])

    d = json.load(open(a.data, encoding="utf-8"))
    items = d["items"] if isinstance(d, dict) else d
    if a.ids:
        want = set(a.ids.split(","))
        items = [x for x in items if x["id"] in want]
    out = Path(a.out)
    prev = {}
    if out.exists() and not a.force:       # 이미 읽은 상품은 건너뛴다 (무료 한도 절약)
        prev = {r["id"]: r for r in json.load(open(out, encoding="utf-8")).get("items", []) if not r.get("failures")}   # 실패가 있던 상품은 다시 읽는다
    items = [x for x in items if x["id"] not in prev][:a.limit]
    print(f"이번에 읽을 상품 {len(items)}개 (이미 읽은 {len(prev)}개는 건너뜀)")

    rules = matching.load_rules()
    results = list(prev.values())
    for it in items:
        folder = ROOT / "samples" / "ocr_images" / it["id"]
        if folder.is_dir():
            blobs = [p.read_bytes() for p in sorted(folder.iterdir()) if p.suffix.lower() in (".jpg", ".jpeg", ".png")]
            blobs = pick(blobs, a.max_images)
        else:
            blobs = []
            for u in pick(it.get("images", []), a.max_images):
                try:
                    blobs.append(fetch(u))
                except Exception as e:  # noqa: BLE001
                    print(f"  이미지 다운로드 실패: {u[-40:]} ({e})")
        pieces = [c for b in blobs for c in chunks(b)]

        def one(c):
            try:
                return read_chunk(c, model, key, a.sleep), None
            except Exception as e:  # noqa: BLE001
                return "", e

        with ThreadPoolExecutor(max_workers=a.workers) as ex:
            res = list(ex.map(one, pieces))          # map은 순서를 지킨다
        texts = [r[0] for r in res]
        fail = sum(1 for r in res if r[1])
        for msg in sorted({str(r[1])[:200] for r in res if r[1]}):
            print(f"  읽기 실패: {msg}")        # 같은 오류는 한 번만 보여준다
        text = "\n".join(t for t in texts if t)
        before = matching.tag_item(it["name"], "", rules)
        after = matching.tag_item(it["name"], text, rules)
        print(f"[{it['id']}] {it['name'][:30]}  이미지 {len(blobs)}장, 읽은 글 {len(text)}자, 실패 {fail}")
        print(f"   이름만 : {before}\n   이름+글: {after}\n   정답 핏: {it.get('fit')}")
        results.append({"id": it["id"], "name": it["name"], "fit": it.get("fit"), "images": len(blobs),
                        "failures": fail, "text": text, "tags_name_only": before, "tags_with_text": after})
        json.dump({"model": model, "items": results}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    json.dump({"model": model, "items": results}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"\n저장: {out}  (읽힌 글을 직접 열어 실제 이미지와 맞는지 확인하세요)")


if __name__ == "__main__":
    main()
