"""LLM 태깅 (텍스트 전용, 표준 라이브러리만 사용).

상품명·설명 텍스트에서 정해진 태그 목록 안의 태그만 고르게 한다.
- 모델이 목록 밖 태그를 만들어도 코드에서 버린다 (validate).
- 태그마다 입력에서 그대로 인용한 근거(evidence)를 받아, 실제로 입력에 있는지 확인한다 (grounded).
- 결과는 파일 캐시에 남겨 같은 요청을 다시 보내지 않는다.

API 키는 환경변수로만 받는다 (코드·채팅에 적지 않는다).
  GEMINI_API_KEY  (모델: GEMINI_MODEL, 기본값은 아래 DEFAULT_MODELS. AI Studio에서 정확한 ID 확인)
  GROQ_API_KEY    (모델: GROQ_MODEL)

이 모듈은 matching.py의 점수 계산과 독립이다. 태깅은 상품 등록 때 한 번 하고 item_tag로 저장한다.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Callable

DATA_DIR = Path(__file__).parent / "data"

# 모델 ID는 공식 문서 기준 이름에서 추정한 값이다. 실제 ID가 다르면 환경변수로 덮어쓴다.
DEFAULT_MODELS = {"gemini": "gemini-3.5-flash-lite", "groq": "openai/gpt-oss-120b"}


# ---------------------------------------------------------------- 어휘·프롬프트

def load_vocab(path: Path | str = DATA_DIR / "keywords_expanded.json") -> dict[str, list[str]]:
    """태그 -> 힌트 키워드 목록. 모델에게 태그의 뜻을 알려 주는 용도."""
    with open(path, encoding="utf-8") as f:
        return json.load(f)["keywords"]


def build_prompt(name: str, text: str, category: str, vocab: dict[str, list[str]]) -> str:
    tag_lines = "\n".join(f"- {tag}: {', '.join(kws)}" for tag, kws in vocab.items())
    return (
        "너는 의류 상품 정보에서 정해진 태그만 골라내는 분류기다.\n"
        "규칙:\n"
        "1. 아래 [태그 목록]에 있는 태그만 쓴다. 목록에 없는 태그는 만들지 않는다.\n"
        "2. 상품명이나 설명에 근거가 있는 태그만 고른다. 브랜드나 일반 상식으로 추측하지 않는다.\n"
        "3. 근거가 없으면 빈 목록을 돌려준다.\n"
        "4. 태그마다 evidence에 입력 문장에서 그대로 인용한 짧은 구절을 적는다.\n"
        '5. 출력은 JSON 하나만: {"tags": [{"tag": "...", "evidence": "..."}]}\n'
        "태그 뒤의 단어들은 그 태그를 뜻하는 표현 예시다(동의어·영문 포함).\n\n"
        f"[태그 목록]\n{tag_lines}\n\n"
        f"[상품]\n이름: {name}\n카테고리: {category}\n설명: {text or '(없음)'}\n"
    )


# ---------------------------------------------------------------- 응답 처리

def parse_response(raw: str) -> list[dict]:
    """모델 응답에서 [{"tag","evidence"}, ...]를 꺼낸다. 형식이 깨졌으면 빈 목록."""
    s = raw.strip()
    s = re.sub(r"^```(?:json)?\s*|\s*```$", "", s)
    try:
        data = json.loads(s)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", s, re.S)
        if not m:
            return []
        try:
            data = json.loads(m.group(0))
        except json.JSONDecodeError:
            return []
    out = []
    for t in (data.get("tags", []) if isinstance(data, dict) else []):
        if isinstance(t, str):
            out.append({"tag": t, "evidence": ""})
        elif isinstance(t, dict) and isinstance(t.get("tag"), str):
            out.append({"tag": t["tag"], "evidence": str(t.get("evidence", ""))})
    return out


def validate(entries: list[dict], vocab: dict, source_text: str) -> dict:
    """목록 밖 태그 제거, 중복 제거, 근거가 입력에 실제 있는지 표시."""
    src = source_text.lower()
    tags, grounded, dropped = [], [], []
    for e in entries:
        tag = e["tag"]
        if tag not in vocab:
            dropped.append(tag)
            continue
        if tag in tags:
            continue
        tags.append(tag)
        ev = e["evidence"].strip().lower()
        if ev and ev in src:
            grounded.append(tag)
    return {"tags": tags, "grounded": grounded, "dropped": dropped}


# ---------------------------------------------------------------- 제공자 호출

def _post(url: str, headers: dict, body: dict, timeout: int, retries: int = 3) -> dict:
    # 기본 User-Agent(Python-urllib)는 Cloudflare 뒤의 API(Groq)가 403 error 1010으로 막는다
    headers = {"User-Agent": "capstone-matching/1.0", **headers}
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < retries - 1:
                time.sleep(5 * (attempt + 1))  # 한도 초과·일시 오류는 잠깐 쉬고 재시도
                continue
            raise RuntimeError(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}") from None
    raise RuntimeError("재시도 초과")


def call_gemini(prompt: str, model: str, key: str, timeout: int = 60) -> str:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    body = {"contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {"temperature": 0, "responseMimeType": "application/json"}}
    data = _post(url, {"Content-Type": "application/json", "x-goog-api-key": key}, body, timeout)
    return data["candidates"][0]["content"]["parts"][0]["text"]


def call_groq(prompt: str, model: str, key: str, timeout: int = 60) -> str:
    body = {"model": model, "temperature": 0, "response_format": {"type": "json_object"},
            "messages": [{"role": "user", "content": prompt}]}
    url, hdr = "https://api.groq.com/openai/v1/chat/completions", {"Content-Type": "application/json", "Authorization": f"Bearer {key}"}
    try:
        data = _post(url, hdr, body, timeout)
    except RuntimeError as e:
        if "json_validate_failed" not in str(e):
            raise
        body.pop("response_format")          # JSON 강제 모드가 실패하면 일반 모드로 한 번 더 (파서가 본문에서 JSON을 꺼낸다)
        data = _post(url, hdr, body, timeout)
    return data["choices"][0]["message"]["content"]


PROVIDERS = {"gemini": (call_gemini, "GEMINI_API_KEY", "GEMINI_MODEL"),
             "groq": (call_groq, "GROQ_API_KEY", "GROQ_MODEL")}


def make_caller(provider: str) -> tuple[Callable[[str], str], str]:
    """(prompt -> 응답 문자열 함수, 모델 이름). 키가 없으면 안내 메시지와 함께 종료."""
    fn, key_env, model_env = PROVIDERS[provider]
    key = os.environ.get(key_env)
    if not key:
        raise SystemExit(f"환경변수 {key_env}가 없습니다. 키를 채팅에 적지 말고 본인 PC 환경변수로 설정하세요.")
    model = os.environ.get(model_env, DEFAULT_MODELS[provider])
    return (lambda prompt: fn(prompt, model, key)), model


# ---------------------------------------------------------------- 태깅 본체

def tag_item_llm(name: str, text: str, category: str, vocab: dict, call: Callable[[str], str],
                 cache: dict | None = None, cache_key: str = "") -> dict:
    """상품 하나를 태깅한다. cache는 {key: 원본 응답} 딕셔너리(호출 쪽에서 파일로 저장)."""
    prompt = build_prompt(name, text, category, vocab)
    key = hashlib.sha1((cache_key + prompt).encode("utf-8")).hexdigest()
    if cache is not None and key in cache:
        raw = cache[key]
    else:
        raw = call(prompt)
        if cache is not None:
            cache[key] = raw
    result = validate(parse_response(raw), vocab, f"{name} {text}")
    result["raw"] = raw
    return result
