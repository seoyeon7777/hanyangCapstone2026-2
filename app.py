"""통합 서버: A(체형 분류) + B(옷 매칭·상품 등록) + C(추천·홈 피드)를 한 번에 켠다.

실행: python app.py
  - 같은 PC 브라우저:       http://127.0.0.1:5000/feed-demo
  - 안드로이드 에뮬레이터:  http://10.0.2.2:5000
  - 같은 와이파이의 휴대폰:  http://<이 PC의 IP>:5000   (실행하면 주소를 화면에 찍어 준다)
  환경변수: HOST(기본 0.0.0.0 = 같은 네트워크에서 접속 허용), PORT(기본 5000),
           FLASK_DEBUG=1(디버그 모드. 네트워크에 열린 상태에서는 위험하니 PC 혼자 쓸 때만)

B (server.py)        GET /api/form, POST /api/items/validate, GET·POST /api/items, POST /api/recommend
C (recommend/)       GET·POST /api/feed, GET·POST /api/feed/scroll, POST /api/feed/event, GET /feed-demo
A (body_profile.py)  POST /api/feed에 "answers"(설문)를 보내면 C가 A를 불러 진단한다
오류는 모두 JSON {"ok": false, "error": "..."} 로 돌려준다 (앱이 HTML 오류 화면을 받지 않게).
"""
import os
import socket
import sys

from werkzeug.exceptions import HTTPException

ROOT = os.path.dirname(os.path.abspath(__file__))
# C 파트 모듈(feed, feed_api …)은 recommend/ 안에 있다. 맨 위 폴더(B·A)가 먼저 찾히도록 뒤에 붙인다.
sys.path.append(os.path.join(ROOT, "recommend"))

from flask import jsonify  # noqa: E402

from server import create_app  # noqa: E402  B 파트 서버
from feed_api import feed_bp, STORE   # noqa: E402  C 파트 API

app = create_app()
app.register_blueprint(feed_bp)


@app.errorhandler(HTTPException)
def http_error(e):
    """없는 주소(404), 허용 안 된 방식(405) 등도 JSON으로."""
    return jsonify({"ok": False, "error": f"{e.code} {e.name}"}), e.code


@app.errorhandler(Exception)
def server_error(e):
    """예상 못 한 오류도 HTML 대신 JSON으로 (자세한 내용은 서버 화면에만 남긴다)."""
    app.logger.exception(e)
    return jsonify({"ok": False, "error": "서버 오류가 났어요"}), 500


def lan_ip():
    """같은 와이파이의 휴대폰이 접속할 이 PC의 IP (실제로 패킷을 보내지는 않는다)."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0")
    port = int(os.environ.get("PORT", "5000"))
    debug = os.environ.get("FLASK_DEBUG") == "1"
    # 상품·사용자 기록을 어디에 두는지 (recommend/store.py)
    if STORE.kind == "postgres":
        print("\n  데이터           DB (database_url.txt 또는 DATABASE_URL) — 서버를 꺼도 기록이 남아요")
    else:
        print("\n  데이터           메모리 + recommend/data/items.json — 서버를 끄면 기록이 사라져요")
    print(f"  PC 브라우저      http://127.0.0.1:{port}/feed-demo")
    print(f"  에뮬레이터       http://10.0.2.2:{port}")
    if host == "0.0.0.0":
        print(f"  같은 와이파이 폰  http://{lan_ip()}:{port}\n")
    app.run(host=host, port=port, debug=debug)
