"""이메일 인증 API 테스트. 메일은 실제로 보내지 않고 SENDER를 가짜로 바꿔 링크만 받아 본다."""
import re

import pytest
from flask import Flask

import email_verify


@pytest.fixture
def client(monkeypatch):
    sent = []
    monkeypatch.setattr(email_verify, "SENDER", lambda to, subject, text, html_body: sent.append((to, text)))
    monkeypatch.setattr(email_verify, "link_base", lambda: "http://test")
    # 인터넷 없이도 돌게: "nowhere"가 들어간 도메인만 없는 것으로
    monkeypatch.setattr(email_verify, "domain_exists", lambda domain: "nowhere" not in domain)
    for table in (email_verify._REQUESTS, email_verify._TOKENS, email_verify._LAST_SENT):
        table.clear()
    app = Flask(__name__)
    app.register_blueprint(email_verify.email_bp)
    c = app.test_client()
    c.sent = sent
    return c


def _token(text):
    return re.search(r"token=([\w-]+)", text).group(1)


def test_verified_only_after_link_clicked(client):
    r = client.post("/api/email/send", json={"email": " Me@Naver.com "})
    assert r.status_code == 200
    request_id = r.get_json()["request_id"]
    assert client.sent[0][0] == "me@naver.com"

    assert client.post("/api/email/status", json={"request_id": request_id}).get_json()["verified"] is False

    assert client.get("/api/email/confirm?token=" + _token(client.sent[0][1])).status_code == 200
    body = client.post("/api/email/status", json={"request_id": request_id}).get_json()
    assert body["verified"] is True and body["email"] == "me@naver.com"


def test_bad_email_and_bad_token(client):
    assert client.post("/api/email/send", json={"email": "abc@gmail"}).status_code == 400
    assert client.post("/api/email/send", json=["x"]).status_code == 400
    assert client.get("/api/email/confirm?token=nope").status_code == 400
    assert client.post("/api/email/status", json={"request_id": "nope"}).status_code == 404
    assert client.sent == []


def test_resend_waits(client):
    assert client.post("/api/email/send", json={"email": "a1@gmail.com"}).status_code == 200
    assert client.post("/api/email/send", json={"email": "a1@gmail.com"}).status_code == 429


def test_expired_request_is_gone(client, monkeypatch):
    request_id = client.post("/api/email/send", json={"email": "a2@gmail.com"}).get_json()["request_id"]
    token = _token(client.sent[0][1])
    now = email_verify.time.time()
    monkeypatch.setattr(email_verify.time, "time", lambda: now + email_verify.EXPIRE_SECONDS + 1)
    assert client.get("/api/email/confirm?token=" + token).status_code == 400
    assert client.post("/api/email/status", json={"request_id": request_id}).status_code == 404


def test_no_mail_config_is_503(client, monkeypatch, tmp_path):
    monkeypatch.setattr(email_verify, "SENDER", email_verify.smtp_send)
    monkeypatch.setattr(email_verify, "SMTP_FILE", str(tmp_path / "none.txt"))
    for key in ("SMTP_HOST", "SMTP_USER", "SMTP_PASSWORD"):
        monkeypatch.delenv(key, raising=False)
    r = client.post("/api/email/send", json={"email": "a3@gmail.com"})
    assert r.status_code == 503
    assert "메일 서버 설정" in r.get_json()["error"]


def test_check_rejects_domain_that_does_not_exist(client):
    assert client.post("/api/email/check", json={"email": "a@naver.com"}).get_json()["exists"] is True
    r = client.post("/api/email/check", json={"email": "34343434@dsdsds.nowhere"})
    assert r.status_code == 400 and "존재하지 않는" in r.get_json()["error"]
    assert client.post("/api/email/check", json={"email": "a@dsdsds.fdf."}).status_code == 400


def test_send_refuses_domain_that_does_not_exist(client):
    assert client.post("/api/email/send", json={"email": "me@ieooo.nowhere"}).status_code == 400
    assert client.sent == []


def test_check_domain_reads_dns_answer(monkeypatch):
    answers = {"has-mx.com": (0, 1), "no-such.fdf": (3, 0), "a-only.com": (0, 0), "broken.com": (2, 0)}
    monkeypatch.setattr(email_verify, "_dns_query", lambda domain, server, **kw: answers[domain])
    monkeypatch.setattr(email_verify.socket, "getaddrinfo", lambda domain, port: [("ok",)])
    assert email_verify.check_domain("has-mx.com") is True
    assert email_verify.check_domain("no-such.fdf") is False
    assert email_verify.check_domain("a-only.com") is True       # MX 없어도 주소(A)가 있으면 받을 수 있다
    assert email_verify.check_domain("broken.com") is None       # DNS 서버 오류 → 확인 못 함 (막지 않음)


def test_check_domain_offline_is_unknown(monkeypatch):
    def offline(domain, server, **kw):
        raise OSError("인터넷 없음")
    monkeypatch.setattr(email_verify, "_dns_query", offline)
    assert email_verify.check_domain("naver.com") is None
