"""추천 피드만 따로 켜는 서버.

실행: python server.py  →  http://127.0.0.1:5000/feed-demo

가상 피팅 서버(hanyangCapstone2026 저장소의 app.py)에 붙일 때는 이 파일 대신
그쪽 app.py에서 `app.register_blueprint(feed_bp)`만 하면 된다 (CLAUDE.md 4-7 참고).
"""
from flask import Flask

from feed_api import feed_bp


def create_app():
    app = Flask(__name__)
    app.register_blueprint(feed_bp)
    return app


app = create_app()

if __name__ == "__main__":
    app.run(debug=True)
