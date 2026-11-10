"""이메일 인증 API: 적은 주소로 인증 메일을 실제로 보내고, 메일 속 링크를 누르면 인증된 것으로 기록한다.

흐름 (앱 마이페이지 > 프로필 수정 > 이메일 변경)
  1) POST /api/email/send    {"email"}       → 인증 메일 발송, {"ok", "request_id"}
  2) 사용자가 메일함에서 "이메일 인증하기" 링크를 누른다 → GET /api/email/confirm?token=…
  3) POST /api/email/status  {"request_id"}  → {"ok", "verified", "email"}  (앱의 "인증확인" 버튼)
  POST /api/email/check {"email"} → 회원가입 이메일(선택)용: @ 뒤 도메인이 실제로 있는지만 본다 (없으면 400)
  메일을 보내기 전에도 같은 도메인 확인을 한다. 인터넷이 안 돼 확인을 못 하면 막지 않는다.

메일 서버 설정 (없으면 발송 요청에 503)
  환경변수 SMTP_HOST, SMTP_PORT(기본 587), SMTP_USER, SMTP_PASSWORD, SMTP_FROM(없으면 SMTP_USER)
  또는 맨 위 email_smtp.txt (깃에 올리지 않음) 에 한 줄씩 KEY=VALUE
  Gmail 예: SMTP_HOST=smtp.gmail.com / SMTP_USER=보내는주소@gmail.com / SMTP_PASSWORD=앱 비밀번호(16자리)
인증 링크 주소: EMAIL_LINK_BASE (없으면 http://<이 PC의 IP>:<PORT> — 같은 와이파이의 휴대폰·PC에서 열린다)

인증 기록은 서버 메모리에만 둔다 (서버를 끄면 사라짐. 시연용).
"""
import html
import os
import re
import secrets
import smtplib
import socket
import struct
import threading
import time
from email.message import EmailMessage

from flask import Blueprint, jsonify, request

ROOT = os.path.dirname(os.path.abspath(__file__))
SMTP_FILE = os.path.join(ROOT, "email_smtp.txt")

EXPIRE_SECONDS = 30 * 60          # 링크 유효 시간
RESEND_SECONDS = 30               # 같은 주소로 다시 보내기까지 기다리는 시간
EMAIL_PATTERN = re.compile(r"[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}")

email_bp = Blueprint("email_verify", __name__)

_LOCK = threading.Lock()
_REQUESTS = {}        # request_id → {"email", "token", "created", "verified"}
_TOKENS = {}          # token → request_id
_LAST_SENT = {}       # email → 보낸 시각


class MailConfigError(Exception):
    """메일 서버 설정이 없다."""


def _error(message, code):
    return jsonify({"ok": False, "error": message}), code


def normalize_email(value):
    if not isinstance(value, str):
        return None
    email = value.strip().lower()
    return email if EMAIL_PATTERN.fullmatch(email) else None


def smtp_config():
    config = {}
    if os.path.exists(SMTP_FILE):
        with open(SMTP_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    config[key.strip()] = value.strip()
    for key in ("SMTP_HOST", "SMTP_PORT", "SMTP_USER", "SMTP_PASSWORD", "SMTP_FROM"):
        if os.environ.get(key):
            config[key] = os.environ[key]
    if not (config.get("SMTP_HOST") and config.get("SMTP_USER") and config.get("SMTP_PASSWORD")):
        raise MailConfigError("메일 서버 설정이 없어요 (email_smtp.txt 또는 SMTP_ 환경변수)")
    return config


def smtp_send(to, subject, text, html_body):
    """실제 발송. 테스트에서는 SENDER를 바꿔 끼운다."""
    config = smtp_config()
    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = config.get("SMTP_FROM") or config["SMTP_USER"]
    message["To"] = to
    message.set_content(text)
    message.add_alternative(html_body, subtype="html")
    with smtplib.SMTP(config["SMTP_HOST"], int(config.get("SMTP_PORT") or 587), timeout=15) as smtp:
        smtp.starttls()
        smtp.login(config["SMTP_USER"], config["SMTP_PASSWORD"])
        smtp.send_message(message)


SENDER = smtp_send


def _lan_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def link_base():
    base = os.environ.get("EMAIL_LINK_BASE")
    if base:
        return base.rstrip("/")
    return f"http://{_lan_ip()}:{os.environ.get('PORT', '5000')}"


# ---------- 도메인 존재 확인 (메일을 받을 수 있는 도메인인지) ----------
DNS_SERVERS = ("8.8.8.8", "1.1.1.1")
DOMAIN_CACHE_SECONDS = 60 * 60
_DOMAIN_CACHE = {}    # domain → (결과, 확인한 시각)


def _dns_query(domain, server, qtype=15, timeout=3.0):
    """DNS에 직접 물어본다 (표준 라이브러리만). qtype 15 = MX. (응답 코드, 답 개수)"""
    query_id = secrets.randbits(16)
    header = struct.pack(">HHHHHH", query_id, 0x0100, 1, 0, 0, 0)   # 재귀 질의, 질문 1개
    name = b"".join(bytes([len(label)]) + label.encode("ascii") for label in domain.split(".")) + b"\0"
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.settimeout(timeout)
        s.sendto(header + name + struct.pack(">HH", qtype, 1), (server, 53))
        data, _ = s.recvfrom(4096)
    reply_id, flags, _, answers = struct.unpack(">HHHH", data[:8])
    if reply_id != query_id:
        raise OSError("다른 질의의 응답")
    return flags & 0x000F, answers


def check_domain(domain):
    """메일 도메인이 실제로 있는지. True 있음 / False 없음 / None 확인 못 함(인터넷 안 됨 등)."""
    for server in DNS_SERVERS:
        try:
            rcode, answers = _dns_query(domain, server)
        except OSError:
            continue
        if rcode == 3:          # NXDOMAIN: 그런 도메인 없음
            return False
        if rcode != 0:
            continue
        if answers > 0:         # 메일 서버(MX)가 있다
            return True
        # MX가 없으면 도메인 주소(A)로 메일을 받을 수 있다 (RFC 5321)
        try:
            socket.getaddrinfo(domain, None)
            return True
        except socket.gaierror:
            return False
    return None


def domain_exists(domain):
    now = time.time()
    cached = _DOMAIN_CACHE.get(domain)
    if cached and now - cached[1] < DOMAIN_CACHE_SECONDS:
        return cached[0]
    result = check_domain(domain)
    if result is not None:      # 확인 못 한 결과는 기억하지 않는다
        _DOMAIN_CACHE[domain] = (result, now)
    return result


NO_DOMAIN_MESSAGE = "존재하지 않는 이메일 주소예요. 다시 확인해 주세요."


@email_bp.post("/api/email/check")
def check():
    """회원가입 이메일(선택)용: 형식 + 도메인이 실제로 있는지. 확인을 못 하면 exists는 null"""
    body = _json_body()
    if body is None:
        return _error("요청 본문은 JSON 객체여야 해요", 400)
    email = normalize_email(body.get("email"))
    if email is None:
        return _error("이메일 형식이 올바르지 않아요", 400)
    exists = domain_exists(email.split("@", 1)[1])
    if exists is False:
        return _error(NO_DOMAIN_MESSAGE, 400)
    return jsonify({"ok": True, "email": email, "exists": exists})


def _forget_expired(now):
    for request_id, item in list(_REQUESTS.items()):
        if now - item["created"] > EXPIRE_SECONDS:
            _TOKENS.pop(item["token"], None)
            del _REQUESTS[request_id]


def _json_body():
    body = request.get_json(silent=True)
    return body if isinstance(body, dict) else None


@email_bp.post("/api/email/send")
def send():
    body = _json_body()
    if body is None:
        return _error("요청 본문은 JSON 객체여야 해요", 400)
    email = normalize_email(body.get("email"))
    if email is None:
        return _error("이메일 형식이 올바르지 않아요", 400)
    if domain_exists(email.split("@", 1)[1]) is False:
        return _error(NO_DOMAIN_MESSAGE, 400)

    now = time.time()
    with _LOCK:
        _forget_expired(now)
        if now - _LAST_SENT.get(email, 0) < RESEND_SECONDS:
            return _error(f"인증 메일은 {RESEND_SECONDS}초에 한 번만 보낼 수 있어요. 잠시 후 다시 시도해 주세요", 429)

    request_id = secrets.token_urlsafe(16)
    token = secrets.token_urlsafe(24)
    link = f"{link_base()}/api/email/confirm?token={token}"
    text = (f"아래 링크를 눌러 이메일 인증을 완료해 주세요. (30분 동안 유효)\n\n{link}\n\n"
            "인증을 마친 뒤 앱으로 돌아가 '인증확인'을 눌러 주세요.\n"
            "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.")
    html_body = (
        "<div style='font-family:sans-serif;font-size:15px;line-height:1.6'>"
        "<p>아래 버튼을 눌러 이메일 인증을 완료해 주세요. (30분 동안 유효)</p>"
        f"<p><a href='{html.escape(link)}' style='display:inline-block;padding:12px 20px;"
        "background:#111;color:#fff;text-decoration:none;border-radius:8px'>이메일 인증하기</a></p>"
        "<p>인증을 마친 뒤 앱으로 돌아가 <b>인증확인</b>을 눌러 주세요.</p>"
        "<p style='color:#888;font-size:13px'>본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p></div>"
    )

    try:
        SENDER(email, "[골격 맞춤 쇼핑] 이메일 인증을 완료해 주세요", text, html_body)
    except MailConfigError as e:
        return _error(str(e), 503)
    except (smtplib.SMTPException, OSError):
        # 받는 주소가 없거나 메일 서버에 못 붙은 경우 (자세한 이유는 서버 로그에만)
        return _error("인증 메일을 보내지 못했어요. 주소를 확인하고 다시 시도해 주세요", 502)

    with _LOCK:
        _REQUESTS[request_id] = {"email": email, "token": token, "created": now, "verified": False}
        _TOKENS[token] = request_id
        _LAST_SENT[email] = now
    return jsonify({"ok": True, "request_id": request_id, "email": email})


@email_bp.get("/api/email/confirm")
def confirm():
    token = request.args.get("token", "")
    with _LOCK:
        _forget_expired(time.time())
        request_id = _TOKENS.get(token)
        if request_id:
            _REQUESTS[request_id]["verified"] = True
            email = _REQUESTS[request_id]["email"]
    if not request_id:
        page = ("<h2>인증 링크가 만료되었거나 올바르지 않아요</h2>"
                "<p>앱에서 인증 메일을 다시 받아 주세요.</p>")
        return _page(page), 400
    page = (f"<h2>이메일 인증이 완료되었어요</h2><p>{html.escape(email)}</p>"
            "<p>앱으로 돌아가 <b>인증확인</b>을 눌러 주세요.</p>")
    return _page(page)


def _page(inner):
    return ("<!doctype html><html lang='ko'><head><meta charset='utf-8'>"
            "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            "<title>이메일 인증</title></head>"
            f"<body style='font-family:sans-serif;padding:32px;text-align:center'>{inner}</body></html>")


@email_bp.post("/api/email/status")
def status():
    body = _json_body()
    if body is None:
        return _error("요청 본문은 JSON 객체여야 해요", 400)
    request_id = body.get("request_id")
    if not isinstance(request_id, str):
        return _error("request_id가 필요해요", 400)
    with _LOCK:
        _forget_expired(time.time())
        item = _REQUESTS.get(request_id)
        if item is None:
            return _error("인증 요청이 만료되었어요. 인증 메일을 다시 받아 주세요", 404)
        return jsonify({"ok": True, "verified": item["verified"], "email": item["email"]})
