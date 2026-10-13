"""Gemini 연결·한도 점검 (요청 1~2회만 보낸다).

사용:  python tools\gemini_check.py            # 모델 목록 + 기본 모델로 1회 호출
       python tools\gemini_check.py 모델ID ...  # 지정한 모델들을 각각 1회씩 호출
키는 환경변수 GEMINI_API_KEY에서 읽는다. 오류 전문(어느 한도인지)을 그대로 보여준다.
"""
import json
import os
import sys
import urllib.error
import urllib.request

key = os.environ.get("GEMINI_API_KEY")
if not key:
    raise SystemExit("GEMINI_API_KEY 환경변수가 없습니다.")
H = {"Content-Type": "application/json", "x-goog-api-key": key}


def call(url, body=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body else None, headers=H)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:  # noqa: BLE001
        return 0, repr(e)


models = sys.argv[1:]
if not models:
    st, txt = call("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
    if st == 200:
        names = [m["name"].split("/")[-1] for m in json.loads(txt).get("models", [])
                 if "generateContent" in m.get("supportedGenerationMethods", [])]
        print("generateContent 가능한 모델:\n  " + "\n  ".join(names))
    else:
        print("모델 목록 실패", st, txt[:500])
    models = [os.environ.get("GEMINI_MODEL", "gemini-3.5-flash-lite")]

for m in models:
    st, txt = call(f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent",
                   {"contents": [{"parts": [{"text": "OK라고만 답하세요."}]}]})
    print(f"\n=== {m} -> HTTP {st}")
    print(txt[:3000])
