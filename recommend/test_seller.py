"""판매자 상품 등록 테스트 (Flask 필요). 저장소 맨 위 app.py(통합 서버)를 불러와 확인한다.

항상 임시 폴더·메모리 저장소만 쓴다: database_url.txt가 있어도 팀 DB(Supabase)에 쓰지 않고,
recommend/uploads/·data/registered_items.json도 건드리지 않는다.
PgStore.add_product는 가짜 커서로 SQL 순서·값만 확인한다 (진짜 PostgreSQL이 없어서 SQL 문법은 확인 못 함).

실행: python test_seller.py
"""
import io
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from app import app  # noqa: E402
import feed_api  # noqa: E402
import seller_api  # noqa: E402
from feed import load_items  # noqa: E402
from image_storage import LocalImageStorage, SupabaseImageStorage, make_image_storage, ImageError, check_image  # noqa: E402
from store import MemoryStore, PgStore, reference_size, _load_registered  # noqa: E402

TMP = tempfile.mkdtemp(prefix="seller_test_")
REG_PATH = os.path.join(TMP, "registered.json")
feed_api.STORE = MemoryStore(load_items(), registered_path=REG_PATH)
seller_api.IMAGES = LocalImageStorage(os.path.join(TMP, "uploads"))
client = app.test_client()

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 200
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 200

TOP = {
    "brand": "테스트브랜드", "product_name": "슬림 면 셔츠", "clothing_type": "top", "price": 39000,
    "description": "단정한 슬림핏 셔츠", "seasons": ["spring", "fall"], "sizes": ["M", "S"], "colors": ["black", "WHITE"],
    "measurements": {"S": {"shoulder": 38, "chest": 46, "arm_length": 58, "total_length": 64},
                     "M": {"shoulder": 40, "chest": 48, "arm_length": 59, "total_length": 66}},
    "attrs": {"fit": "슬림", "length_top": "기본"},
    "composition_percent": [{"fiber": "cotton", "percent": 100}],
}


def fresh(**changes):
    body = json.loads(json.dumps(TOP))
    body.update(changes)
    return body


def error_of(body, path="/api/seller/products"):
    res = client.post(path, json=body)
    assert res.status_code == 400, (res.status_code, res.get_json())
    return res.get_json()["error"]


def test_form_has_everything_the_screen_needs():
    data = client.get("/api/seller/form").get_json()
    assert [c["key"] for c in data["categories"]] == ["top", "outer", "pants", "skirt", "dress"]
    assert {"fit", "composition"} <= {a["key"] for a in data["attributes"]}
    assert data["measurements"]["pants"] == [{"key": "waist", "label": "허리단면"}, {"key": "thigh", "label": "허벅지단면"},
                                             {"key": "total_length", "label": "총장"}]
    assert "S" in data["sizes"] and "BLACK" in data["colors"]
    assert [s["key"] for s in data["seasons"]] == ["spring", "summer", "fall", "winter"]


def test_validate_does_not_save():
    before = len(feed_api.STORE.items())
    res = client.post("/api/seller/products/validate", json=fresh())
    assert res.status_code == 200
    data = res.get_json()
    assert "fit:slim" in data["tags"] and data["fit_stated"] == "slim"
    assert len(feed_api.STORE.items()) == before


def test_register_then_it_is_a_recommendation_candidate():
    res = client.post("/api/seller/products", json=fresh())
    assert res.status_code == 201, res.get_json()
    item = res.get_json()["item"]
    assert item["sizes"] == ["S", "M"]                      # 사이즈 순서로 정렬
    assert item["colors"] == ["BLACK", "WHITE"]             # 대문자로 통일
    assert item["measurements"]["M"]["chest"] == 48
    stored = feed_api.STORE.item(item["product_id"])
    assert stored["tags"] and stored["registered"] and stored["popularity"] == 0.5
    # 추천 피드(전체 모드)에 나오고, 앱이 쓸 사이즈·치수도 같이 내려간다
    page = client.post("/api/feed/scroll", json={"mode": "general", "size": 50}).get_json()
    mine = [i for i in page["items"] if i["product_id"] == item["product_id"]]
    assert mine and mine[0]["price"] == 39000 and mine[0]["brand"] == "테스트브랜드"
    assert mine[0]["sizes"] == ["S", "M"] and mine[0]["measurements"]["S"]["shoulder"] == 38


def test_registered_products_survive_restart():
    client.post("/api/seller/products", json=fresh(product_name="재시작 확인"))
    saved = _load_registered(REG_PATH)
    assert any(i["product_name"] == "재시작 확인" for i in saved)
    again = MemoryStore(load_items() + saved, registered_path=REG_PATH)
    assert any(i["product_name"] == "재시작 확인" for i in again.items())
    # 시연용 상품(items.json)은 파일에 안 섞인다
    assert all(i.get("registered") for i in saved)


def test_list_by_brand():
    client.post("/api/seller/products", json=fresh(brand="목록브랜드", product_name="A"))
    client.post("/api/seller/products", json=fresh(brand="다른브랜드", product_name="B"))
    items = client.get("/api/seller/products?brand=목록브랜드").get_json()["items"]
    assert [i["product_name"] for i in items] == ["A"]
    assert client.get("/api/seller/products").status_code == 400


def test_bad_inputs_get_korean_errors():
    assert "브랜드" in error_of(fresh(brand=""))
    assert "상품 이름" in error_of(fresh(product_name="  "))
    assert "가격" in error_of(fresh(price=0))
    assert "가격" in error_of(fresh(price=1.5))
    assert "가격" in error_of(fresh(price=True))
    assert "가격" in error_of(fresh(price=10_000_001))
    assert "계절" in error_of(fresh(seasons=[]))
    assert "계절" in error_of(fresh(seasons=["monsoon"]))
    assert "사이즈" in error_of(fresh(sizes=["XXL"]))
    assert "색상" in error_of(fresh(colors=[]))
    assert "상품 종류" in error_of(fresh(clothing_type="shoes"))
    assert "사진 주소" in error_of(fresh(image_url="javascript:alert(1)"))
    assert "JSON" in error_of([1, 2])
    too_many = fresh(sizes=["XS", "S", "M", "L", "XL", "FREE"], colors=[f"C{i}" for i in range(11)])
    assert "너무 많아요" in error_of(too_many)


def test_measurements_must_be_complete_and_sane():
    body = fresh()
    del body["measurements"]["S"]["chest"]
    assert "S 사이즈의 가슴단면" in error_of(body)
    body = fresh()
    del body["measurements"]["M"]
    assert "M 사이즈의 치수" in error_of(body)
    body = fresh()
    body["measurements"]["L"] = body["measurements"]["M"]
    assert "고르지 않은 사이즈" in error_of(body)
    body = fresh()
    body["measurements"]["S"]["chest"] = 1
    assert "가슴단면" in error_of(body)
    body = fresh()
    body["measurements"]["S"]["chest"] = "48"
    assert "숫자" in error_of(body)
    body = fresh()
    body["measurements"]["S"]["waist"] = 30
    assert "없는 치수 항목" in error_of(body)


def test_b_form_rules_still_apply():
    body = fresh()
    del body["attrs"]["fit"]                    # 핏은 필수
    assert error_of(body)
    assert error_of(fresh(composition_percent=[{"fiber": "cotton", "percent": 50}]))           # 합이 100이 아님
    assert error_of(fresh(attrs={"fit": "슬림", "length_top": "기본", "length_pants": "발목 길이"}))   # 상의에 없는 항목
    assert "달라요" in error_of(fresh(attrs={"category": "pants", "fit": "슬림"}))


def test_pants_and_dress_measurements():
    pants = fresh(clothing_type="pants", attrs={"fit": "레귤러", "length_pants": "발목 길이"}, sizes=["M"],
                  measurements={"M": {"waist": 38, "thigh": 30, "total_length": 98}})
    assert client.post("/api/seller/products", json=pants).status_code == 201
    dress = fresh(clothing_type="dress", attrs={"fit": "슬림", "length_skirt": "미디"}, sizes=["FREE"],
                  measurements={"FREE": {"shoulder": 37, "chest": 45, "waist": 38, "hip": 50, "arm_length": 20, "total_length": 105}})
    assert client.post("/api/seller/products", json=dress).status_code == 201


def test_image_upload_and_serve():
    res = client.post("/api/seller/images", data={"file": (io.BytesIO(JPEG), "a.jpg")}, content_type="multipart/form-data")
    assert res.status_code == 201
    data = res.get_json()
    url = data["url"]
    assert url.startswith("/uploads/") and url.endswith(".jpg")            # 저장하는 값은 서버 주소 없는 경로
    assert data["full_url"] == "http://localhost" + url                     # 바로 열어 볼 때 쓰는 값
    served = client.get(url)
    assert served.status_code == 200 and served.data == JPEG
    assert client.get("/uploads/../registered.json").status_code in (400, 404)    # 폴더 밖은 못 연다
    # 등록 때 그 경로를 넣으면 상품 사진이 된다. 응답에는 서버 주소가 붙는다
    ok = client.post("/api/seller/products", json=fresh(image_url=url))
    assert ok.status_code == 201 and ok.get_json()["item"]["image_url"] == "http://localhost" + url
    assert feed_api.STORE.item(ok.get_json()["item"]["product_id"])["image_url"] == url      # 저장소에는 경로 그대로


def test_local_image_url_follows_the_request_host():
    """에뮬레이터(10.0.2.2)로 올린 사진도 폰(PC의 IP)이 받는 응답에서는 그 폰이 접속한 주소로 나온다."""
    url = client.post("/api/seller/images", data={"file": (io.BytesIO(JPEG), "a.jpg")},
                      content_type="multipart/form-data").get_json()["url"]
    item = client.post("/api/seller/products", json=fresh(product_name="주소 확인", image_url=url)).get_json()["item"]
    for host in ("http://10.0.2.2:5000", "http://192.168.0.7:5000"):
        page = client.post("/api/feed/scroll", json={"mode": "general", "size": 50}, base_url=host).get_json()
        mine = [i for i in page["items"] if i["product_id"] == item["product_id"]][0]
        assert mine["image_url"] == host + url
        listed = client.get("/api/seller/products?brand=" + TOP["brand"], base_url=host).get_json()["items"]
        assert any(i["image_url"] == host + url for i in listed)
    # 이미 http(s) 주소(예: Supabase)는 그대로 둔다
    full = "https://x.supabase.co/storage/v1/object/public/shots/a.jpg"
    other = client.post("/api/seller/products", json=fresh(product_name="외부 주소", image_url=full)).get_json()["item"]
    assert other["image_url"] == full


def test_image_url_must_be_ours_or_http():
    for bad in ("/uploads/../x.jpg", "/uploads/a/b.jpg", "/uploads/", "/etc/passwd", "javascript:alert(1)", "ftp://x/a.jpg"):
        assert "사진 주소" in error_of(fresh(image_url=bad)), bad


def test_feed_does_not_carry_description():
    client.post("/api/seller/products", json=fresh(product_name="설명 확인", description="긴 설명"))
    page = client.post("/api/feed/scroll", json={"mode": "general", "size": 50}).get_json()
    assert all("description" not in i for i in page["items"])


def test_image_rules():
    def post(data, name="a.jpg"):
        return client.post("/api/seller/images", data={"file": (io.BytesIO(data), name)}, content_type="multipart/form-data")
    assert post(b"GIF89a" + b"\x00" * 50).status_code == 400                    # 사진이 아님
    assert post(b"<html>hi</html>", "a.jpg").status_code == 400                  # 이름만 jpg
    assert post(b"").status_code == 400
    assert post(PNG, "x.exe").status_code == 201                                 # 내용이 사진이면 이름은 상관없음 (저장 이름은 새로 만든다)
    big = post(JPEG + b"\x00" * (5 * 1024 * 1024))
    assert big.status_code == 400 and "MB" in big.get_json()["error"]
    assert client.post("/api/seller/images", data={}, content_type="multipart/form-data").status_code == 400


def test_other_apis_keep_the_1mb_limit():
    res = client.post("/api/seller/products", data=b"x" * 1_100_000, content_type="application/json")
    assert res.status_code == 413 and res.get_json()["ok"] is False


def test_storage_choice_and_supabase_request(monkeypatch=None):
    # 환경 변수가 없으면 로컬
    for k in ("SUPABASE_URL", "SUPABASE_SERVICE_KEY", "SUPABASE_BUCKET"):
        os.environ.pop(k, None)
    import image_storage
    image_storage.ENV_FILE = os.path.join(TMP, "none.env")
    assert make_image_storage().kind == "local"
    os.environ.update(SUPABASE_URL="https://x.supabase.co/", SUPABASE_SERVICE_KEY="k", SUPABASE_BUCKET="shots")
    try:
        storage = make_image_storage()
        assert storage.kind == "supabase" and storage.url == "https://x.supabase.co"
        sent = {}

        class Resp:
            def __enter__(self): return self
            def __exit__(self, *a): return False

        def fake_urlopen(req, timeout=0):
            sent["url"], sent["headers"], sent["method"], sent["len"] = req.full_url, dict(req.header_items()), req.get_method(), len(req.data)
            return Resp()
        image_storage.urllib.request.urlopen = fake_urlopen
        url = storage.save(JPEG)
        assert sent["method"] == "POST" and sent["url"].startswith("https://x.supabase.co/storage/v1/object/shots/")
        assert sent["headers"]["Authorization"] == "Bearer k" and sent["headers"]["Content-type"] == "image/jpeg"
        assert url.startswith("https://x.supabase.co/storage/v1/object/public/shots/") and url.endswith(".jpg")
    finally:
        for k in ("SUPABASE_URL", "SUPABASE_SERVICE_KEY", "SUPABASE_BUCKET"):
            os.environ.pop(k, None)


class FakeCursor:
    """PgStore.add_product가 보내는 SQL을 기록한다. brands·fabrics·categories는 '이미 있음'으로 답한다."""

    def __init__(self):
        self.sql = []
        self._row = None

    def execute(self, sql, args=()):
        self.sql.append((" ".join(sql.split()), args))
        text = sql.upper()
        if text.startswith("SELECT"):
            self._row = (7,)
        elif "RETURNING PRODUCT_ID" in text:
            self._row = ("42",)
        else:
            self._row = None

    def fetchone(self):
        return self._row


def test_pg_add_product_statements():
    store = PgStore.__new__(PgStore)            # 연결 없이 add_product만 확인
    cursor = FakeCursor()
    store._run = lambda work, write=False: work(cursor)
    store._loaded_at = 99.0
    product = seller_api.parse_product(fresh())
    stored = store.add_product(product)
    assert stored["product_id"] == "42" and store._loaded_at == 0.0
    kinds = [s.split()[0] + " " + (s.split()[2] if s.startswith("INSERT") else "") for s, _ in cursor.sql]
    inserts = [s for s, _ in cursor.sql if s.startswith("INSERT")]
    assert sum("INTO product_options" in s for s in inserts) == 4          # 사이즈 2 × 색상 2
    assert sum("INTO top_measurements" in s for s in inserts) == 1
    assert sum("INTO product_tags" in s for s in inserts) == 1
    tags_args = [a for s, a in cursor.sql if s.startswith("INSERT INTO product_tags")][0]
    attrs = json.loads(tags_args[5])
    assert attrs["seller_extra"]["measurements"]["S"]["chest"] == 46        # 사이즈별 치수는 attrs에 모두 남는다
    meas_args = [a for s, a in cursor.sql if "INTO top_measurements" in s][0]
    assert meas_args == ("42", 38, 46, 58, 64)                              # 대표(가운데) 사이즈 = S (사이즈 2개면 앞쪽)
    assert reference_size(["S", "M", "L"]) == "M" and reference_size([]) is None


def test_pg_loaded_items_get_seller_details():
    store = PgStore.__new__(PgStore)
    rows = [("a1", {"seller_extra": {"sizes": ["S"], "colors": ["BLACK"], "measurements": {"S": {"chest": 40}}, "seller": "브"}}, "direct"),
            ("t01", None, "manual")]

    class Cur:
        def execute(self, *a): pass
        def fetchall(self): return rows
    store._run = lambda work, write=False: work(Cur())
    items = [{"product_id": "a1"}, {"product_id": "t01"}]
    store._attach_seller_details(items)
    assert items[0]["registered"] and items[0]["sizes"] == ["S"] and items[0]["measurements"]["S"]["chest"] == 40
    assert "registered" not in items[1]


def test_check_image_helper():
    assert check_image(JPEG)[1] == ".jpg" and check_image(PNG)[1] == ".png"
    try:
        check_image(b"nope")
        assert False
    except ImageError:
        pass


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_")]
    failed = 0
    for name, fn in tests:
        try:
            fn()
            print(f"  PASS  {name}")
        except AssertionError as e:
            failed += 1
            print(f"  FAIL  {name} {e}")
        except Exception as e:
            failed += 1
            print(f"  ERROR {name}: {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} 통과")
    raise SystemExit(1 if failed else 0)
