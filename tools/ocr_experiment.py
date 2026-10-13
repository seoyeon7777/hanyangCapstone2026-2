"""상세 이미지 속 글자(OCR)가 태깅에 도움이 되는지 보는 실험. 내 PC에서 실행한다.

준비:   pip install paddleocr paddlepaddle pillow requests
실행:   python tools/ocr_experiment.py                  (samples/detail_images.json 사용)
        python tools/ocr_experiment.py --limit 2 --keep   (2개만, 잘라낸 조각 저장)

하는 일: 이미지를 내려받아 -> 긴 세로 이미지는 조각으로 자르고 -> 한국어 OCR -> 읽힌 글을 합쳐
         '이름만' 대 '이름+OCR글'로 키워드 태그를 비교한다. 읽힌 글은 samples/ocr_result.json 에 저장.
이미지를 PC에서도 못 내려받으면: 상품 페이지에서 상세 이미지를 직접 저장해
        samples/ocr_images/<상품id>/ 폴더에 넣는다 (폴더가 있으면 주소 대신 그 파일을 쓴다).
첫 실행은 모델을 내려받느라 느리다. 결과는 반드시 읽힌 글을 눈으로도 확인할 것(정확도 수치는 없다).
"""
from __future__ import annotations

import argparse
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import matching  # noqa: E402

CHUNK_H = 1600      # 조각 높이(px)
OVERLAP = 120       # 조각이 겹치는 높이 (글자가 경계에서 잘리는 것 방지)
HEADERS = {"User-Agent": "Mozilla/5.0", "Referer": "https://www.musinsa.com/"}


def download(url: str) -> bytes:
    import requests
    r = requests.get(url, headers=HEADERS, timeout=30)
    r.raise_for_status()
    return r.content


def slices(img):
    w, h = img.size
    if w > 1000:                      # 너무 크면 줄인다 (글자 인식에는 이 정도면 충분)
        img = img.resize((960, int(h * 960 / w)))
        w, h = img.size
    y = 0
    while y < h:
        yield img.crop((0, y, w, min(h, y + CHUNK_H)))
        if y + CHUNK_H >= h:
            break
        y += CHUNK_H - OVERLAP


def make_ocr(light: bool = True):
    from paddleocr import PaddleOCR
    base = dict(lang="korean", use_doc_orientation_classify=False,
                use_doc_unwarping=False, use_textline_orientation=False,
                enable_mkldnn=False)      # paddlepaddle 3.3.x CPU 오류(oneDNN) 회피
    attempts = []
    if light:                             # 글자 위치 찾는 모델을 가벼운 것으로 (CPU에서 훨씬 빠름)
        attempts.append(dict(base, text_detection_model_name="PP-OCRv5_mobile_det"))
    attempts.append(base)
    attempts.append(dict(lang="korean", use_angle_cls=False))   # 옛 버전
    last = None
    for kw in attempts:
        try:
            return PaddleOCR(**kw)
        except (TypeError, ValueError) as e:
            last = e
    raise last


def read_texts(ocr, pil_img) -> list[str]:
    import numpy as np
    arr = np.array(pil_img.convert("RGB"))
    if hasattr(ocr, "predict"):
        res = ocr.predict(arr)
        out = []
        for r in res:
            d = r if isinstance(r, dict) else getattr(r, "json", {}).get("res", {})
            out += list(d.get("rec_texts", []))
        return out
    res = ocr.ocr(arr)
    return [ln[1][0] for page in (res or []) for ln in (page or [])]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=str(ROOT / "samples" / "detail_images.json"))
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--keep", action="store_true", help="잘라낸 조각을 samples/ocr_chunks/ 에 저장")
    ap.add_argument("--max-chunks", type=int, default=0, help="상품당 읽을 조각 수 상한 (0=제한 없음, 시간 줄이기용)")
    ap.add_argument("--full-det", action="store_true", help="무거운 기본 검출 모델 사용 (느리지만 더 정확할 수 있음)")
    a = ap.parse_args()

    from PIL import Image
    items = json.load(open(a.src, encoding="utf-8"))["items"]
    if a.limit:
        items = items[: a.limit]
    rules = matching.load_rules()
    ocr = make_ocr(light=not a.full_det)
    results = []
    import time
    t_all = time.time()
    for it in items:
        texts: list[str] = []
        fails = 0
        n_chunks = 0
        t0 = time.time()
        local = ROOT / "samples" / "ocr_images" / it["id"]      # 직접 저장한 이미지가 있으면 그걸 쓴다
        sources = (sorted(str(f) for f in local.glob("*") if f.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp"))
                   if local.is_dir() else it["images"])
        for n, url in enumerate(sources):
            try:
                img = Image.open(url) if local.is_dir() else Image.open(io.BytesIO(download(url)))
            except Exception as e:                       # 다운로드 실패는 건너뛰고 세어 둔다
                fails += 1
                print(f"  [실패] {url[-40:]}  {e}")
                continue
            for k, ch in enumerate(slices(img)):
                if a.max_chunks and n_chunks >= a.max_chunks:
                    break
                n_chunks += 1
                if a.keep:
                    d = ROOT / "samples" / "ocr_chunks"
                    d.mkdir(exist_ok=True)
                    ch.save(d / f"{it['id']}_{n}_{k}.png")
                texts += read_texts(ocr, ch)
                print(f"  {it['id']} 조각 {n_chunks}개 처리 ({time.time() - t0:.0f}초 경과)", flush=True)
        full = "\n".join(texts)
        base = matching.tag_item(it["name"], "", rules)
        withocr = matching.tag_item(it["name"], full, rules)
        results.append({"id": it["id"], "name": it["name"], "fit": it.get("fit"), "download_fail": fails,
                        "ocr_chars": len(full), "tags_name_only": base, "tags_with_ocr": withocr, "ocr_text": full})
        print(f"[{time.time() - t_all:.0f}초] {it['id']} 핏={it.get('fit')}  글자수={len(full)}  이름만={base}  이름+OCR={withocr}")

    out = ROOT / "samples" / "ocr_result.json"
    json.dump(results, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("\n=== 요약 ===")
    for r in results:
        print(r["id"], "글자수", r["ocr_chars"], "| 이름만", len(r["tags_name_only"]), "-> OCR", len(r["tags_with_ocr"]))
    print("읽힌 글 전체:", out)


if __name__ == "__main__":
    main()
