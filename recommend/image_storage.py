"""상품 사진 저장소.

사진 파일은 DB가 아니라 파일 저장소에 두고, DB(products.main_image_url)에는 주소(URL)만 적는다.

    LocalImageStorage     이 PC의 recommend/uploads/ 폴더에 저장하고 서버가 /uploads/<파일>로 보여 준다 (키 없이 시연·테스트)
                          DB·파일에는 "/uploads/<파일>" 경로만 적는다. 서버 주소(10.0.2.2, PC의 IP …)는 보는 기기마다 다르므로
                          앱에 보낼 때 absolute_image_url()이 그 요청의 서버 주소를 붙인다.
    SupabaseImageStorage  Supabase Storage 버킷에 올리고 공개 주소를 돌려준다 (아래 환경 변수가 모두 있을 때)

    SUPABASE_URL          예: https://abcdxyz.supabase.co  (프로젝트 주소. 공개 값)
    SUPABASE_SERVICE_KEY  서버 전용 키(service_role). 비밀 — 깃에 올리거나 앱에 넣지 말 것
    SUPABASE_BUCKET       사진을 담을 버킷 이름 (공개 버킷으로 만든다)

환경 변수나 저장소 맨 위 supabase.env 파일(KEY=값 한 줄씩, 깃에 없음)에서 읽는다.
세 값 중 하나라도 없으면 LocalImageStorage를 쓴다.
"""
import os
import secrets
import urllib.error
import urllib.request

from rec_config import IMAGE_MAX_BYTES, IMAGE_TYPES, UPLOAD_DIR_NAME, UPLOAD_URL_PREFIX

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ENV_FILE = os.path.join(ROOT, "supabase.env")


class ImageError(Exception):
    """올릴 수 없는 사진(종류·크기) 또는 저장소 오류. 메시지는 사용자에게 그대로 보여 준다."""


def sniff_type(data):
    """파일 내용으로 사진 종류를 알아낸다 (이름·헤더의 content-type은 믿지 않는다). 모르면 None."""
    if data[:3] == b"\xff\xd8\xff":
        return "image/jpeg"
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return "image/png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return None


def check_image(data):
    """사진 바이트를 검사하고 (content_type, 확장자)를 돌려준다. 잘못되면 ImageError."""
    if not data:
        raise ImageError("사진 파일이 비어 있어요")
    if len(data) > IMAGE_MAX_BYTES:
        raise ImageError(f"사진은 {IMAGE_MAX_BYTES // (1024 * 1024)}MB 이하만 올릴 수 있어요")
    kind = sniff_type(data)
    if kind not in IMAGE_TYPES:
        raise ImageError("JPG, PNG, WEBP 사진만 올릴 수 있어요")
    return kind, IMAGE_TYPES[kind]


def absolute_image_url(url, host_url):
    """저장된 사진 주소를 앱이 불러올 수 있는 주소로. "/uploads/…" 경로면 요청한 서버 주소를 붙이고, 이미 http(s)면 그대로."""
    if isinstance(url, str) and url.startswith(UPLOAD_URL_PREFIX):
        return host_url.rstrip("/") + url
    return url


def is_storable_image_url(url):
    """상품에 적어도 되는 사진 주소인지: 우리 서버의 업로드 경로 또는 http(s) 주소."""
    if not isinstance(url, str) or len(url) > 500:
        return False
    if url.startswith(UPLOAD_URL_PREFIX):
        name = url[len(UPLOAD_URL_PREFIX):]
        return bool(name) and "/" not in name and ".." not in name
    return url.startswith(("http://", "https://"))


def _new_name(ext):
    return secrets.token_hex(12) + ext      # 추측할 수 없는 이름 (남의 사진 주소를 짐작해 열지 못하게)


class LocalImageStorage:
    kind = "local"

    def __init__(self, folder=None):
        self.folder = folder or os.path.join(HERE, UPLOAD_DIR_NAME)

    def save(self, data):
        """사진을 저장하고 "/uploads/<파일>" 경로를 돌려준다."""
        _, ext = check_image(data)
        os.makedirs(self.folder, exist_ok=True)
        name = _new_name(ext)
        with open(os.path.join(self.folder, name), "wb") as f:
            f.write(data)
        return UPLOAD_URL_PREFIX + name


class SupabaseImageStorage:
    kind = "supabase"

    def __init__(self, url, service_key, bucket):
        self.url = url.rstrip("/")
        self.service_key = service_key
        self.bucket = bucket

    def save(self, data):
        kind, ext = check_image(data)
        name = _new_name(ext)
        request = urllib.request.Request(
            f"{self.url}/storage/v1/object/{self.bucket}/{name}",
            data=data, method="POST",
            headers={"Authorization": f"Bearer {self.service_key}", "apikey": self.service_key,
                     "Content-Type": kind, "x-upsert": "false"},
        )
        try:
            with urllib.request.urlopen(request, timeout=20):
                pass
        except urllib.error.HTTPError as e:
            raise ImageError(f"사진 저장소에 올리지 못했어요 (HTTP {e.code})")
        except (urllib.error.URLError, OSError):
            raise ImageError("사진 저장소에 연결하지 못했어요")
        return f"{self.url}/storage/v1/object/public/{self.bucket}/{name}"


def _read_env_file():
    values = {}
    try:
        with open(ENV_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, _, value = line.partition("=")
                    values[key.strip()] = value.strip()
    except OSError:
        pass
    return values


def make_image_storage():
    file_values = _read_env_file()

    def get(key):
        return os.environ.get(key) or file_values.get(key) or ""

    url, key, bucket = get("SUPABASE_URL"), get("SUPABASE_SERVICE_KEY"), get("SUPABASE_BUCKET")
    if url and key and bucket:
        return SupabaseImageStorage(url, key, bucket)
    return LocalImageStorage()
