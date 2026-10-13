"""통합 서버: A(체형 분류) + B(옷 매칭·상품 등록) + C(추천·홈 피드)를 한 번에 켠다.

실행: python app.py  →  http://127.0.0.1:5000/feed-demo

B (server.py)        GET /api/form, POST /api/items/validate, GET·POST /api/items, POST /api/recommend
C (recommend/)       GET·POST /api/feed, GET·POST /api/feed/scroll, POST /api/feed/event, GET /feed-demo
A (body_profile.py)  POST /api/feed에 "answers"(설문)를 보내면 C가 A를 불러 진단한다
"""
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
# C 파트 모듈(feed, feed_api …)은 recommend/ 안에 있다. 맨 위 폴더(B·A)가 먼저 찾히도록 뒤에 붙인다.
sys.path.append(os.path.join(ROOT, "recommend"))

from server import create_app  # noqa: E402  B 파트 서버
from feed_api import feed_bp   # noqa: E402  C 파트 API

app = create_app()
app.register_blueprint(feed_bp)

if __name__ == "__main__":
    app.run(debug=True)
