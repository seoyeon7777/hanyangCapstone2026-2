"""판매자 상품 등록 API (Flask Blueprint). app.py가 feed_bp와 함께 등록한다.

GET  /api/seller/form                  등록 화면을 그리는 데 필요한 모든 정의 (종류, 속성 양식, 계절, 사이즈, 색상, 종류별 치수 항목)
POST /api/seller/products/validate     입력 검증 + 태그 미리보기. 저장하지 않는다
POST /api/seller/products              상품 등록. 검증을 통과하면 저장소(DB 또는 메모리)에 넣고 추천 후보가 된다
GET  /api/seller/products?brand=이름   그 브랜드로 등록한 상품 목록
POST /api/seller/images                사진 올리기 (multipart, 필드 이름 file) → {"url"}
GET  /uploads/<파일>                    이 PC에 저장한 사진 보기 (Supabase Storage를 쓰면 쓰이지 않는다)

속성 양식·태그·점수는 B 파트(tagging.py, data/attributes.json)를 그대로 쓴다. 여기서는 판매자가 더 적는 것
(브랜드, 가격, 사이즈, 색상, 사이즈별 치수, 계절, 사진)만 검사하고, 저장은 store.py가 맡는다.
오류는 400 {"ok": false, "error": "한국어 문장"}.

판매자 로그인은 아직 없다. brand(브랜드 이름)로 누구의 상품인지 가른다.
"""
import os

from flask import Blueprint, current_app, jsonify, request, send_from_directory

from matching_b import RULES, matching      # B 파트 (저장소 맨 위 폴더를 경로에 넣어 둠)
import tagging                              # B 파트
from rec_config import (
    SELLER_DEFAULT_POPULARITY, SELLER_SEASONS, SELLER_SIZES, SELLER_COLORS, SELLER_MAX_PRICE, SELLER_MAX_OPTIONS,
    SELLER_MEASUREMENTS, SELLER_MEASURE_MIN, SELLER_MEASURE_MAX, IMAGE_MAX_BYTES, IMAGE_TYPES, UPLOAD_DIR_NAME,
)
from image_storage import make_image_storage, ImageError, LocalImageStorage, absolute_image_url, is_storable_image_url
from store import StoreError

seller_bp = Blueprint("seller", __name__)

IMAGES = make_image_storage()
SEASON_LABELS = {"spring": "봄", "summer": "여름", "fall": "가을", "winter": "겨울"}
CATEGORY_LABELS = {"top": "상의", "outer": "아우터", "pants": "바지", "skirt": "스커트", "dress": "원피스"}
MAX_COLORS = 12


class BadRequest(Exception):
    pass


@seller_bp.errorhandler(BadRequest)
def bad_request(e):
    return jsonify({"ok": False, "error": str(e)}), 400


@seller_bp.errorhandler(ValueError)       # B 파트(tagging)가 던지는 검증 오류
def value_error(e):
    return jsonify({"ok": False, "error": str(e)}), 400


@seller_bp.errorhandler(ImageError)
def image_error(e):
    return jsonify({"ok": False, "error": str(e)}), 400


@seller_bp.errorhandler(StoreError)
def store_error(e):
    current_app.logger.error("판매자 API 저장소 오류: %s", e)
    return jsonify({"ok": False, "error": "DB에 잠시 연결할 수 없어요. 잠시 후 다시 시도해 주세요"}), 503


def _store():
    from feed_api import STORE      # 추천 API와 같은 저장소를 쓴다 (등록한 상품이 바로 추천 후보가 된다)
    return STORE


def _text(body, key, label, min_len=1, max_len=100, required=True):
    value = body.get(key)
    if value is None and not required:
        return ""
    if not isinstance(value, str):
        raise BadRequest(f"{label}을(를) 입력해 주세요")
    value = " ".join(value.split())
    if len(value) < min_len or len(value) > max_len:
        raise BadRequest(f"{label}은(는) {min_len}~{max_len}자로 입력해 주세요" if min_len > 0
                         else f"{label}은(는) {max_len}자 이하로 입력해 주세요")
    return value


def _number(value, label, low, high):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise BadRequest(f"{label}은(는) 숫자로 입력해 주세요")
    if value != value or not (low <= value <= high):        # NaN 포함
        raise BadRequest(f"{label}은(는) {low:g}~{high:g} 사이로 입력해 주세요")
    return value


def _choice_list(body, key, label, allowed, min_count, max_count, free_text=False):
    values = body.get(key)
    if not isinstance(values, list):
        raise BadRequest(f"{label}을(를) 하나 이상 골라 주세요")
    out = []
    for v in values:
        if not isinstance(v, str):
            raise BadRequest(f"{label} 값이 잘못됐어요")
        v = v.strip().upper() if key in ("sizes", "colors") else v.strip()
        if not free_text and v not in allowed:
            raise BadRequest(f"{label}에 없는 값이에요: {v}")
        if free_text and not (1 <= len(v) <= 30):
            raise BadRequest(f"{label}은(는) 1~30자로 입력해 주세요")
        if v not in out:
            out.append(v)
    if not (min_count <= len(out) <= max_count):
        raise BadRequest(f"{label}을(를) {min_count}~{max_count}개 골라 주세요" if min_count else f"{label}은(는) 최대 {max_count}개예요")
    return out


def _read_measurements(body, clothing_type, sizes):
    """사이즈마다 그 종류의 치수 항목을 모두 받는다. {사이즈: {항목: cm}}."""
    raw = body.get("measurements")
    _, fields = SELLER_MEASUREMENTS[clothing_type]
    if not isinstance(raw, dict):
        raise BadRequest("사이즈별 치수를 입력해 주세요")
    extra = set(raw) - set(sizes)
    if extra:
        raise BadRequest(f"고르지 않은 사이즈의 치수가 있어요: {', '.join(sorted(extra))}")
    out = {}
    for size in sizes:
        row = raw.get(size)
        if not isinstance(row, dict):
            raise BadRequest(f"{size} 사이즈의 치수를 입력해 주세요")
        out[size] = {}
        for key, label in fields:
            if key not in row:
                raise BadRequest(f"{size} 사이즈의 {label}을(를) 입력해 주세요")
            out[size][key] = _number(row[key], f"{size} 사이즈의 {label}", SELLER_MEASURE_MIN, SELLER_MEASURE_MAX)
        unknown = set(row) - {k for k, _ in fields}
        if unknown:
            raise BadRequest(f"{CATEGORY_LABELS[clothing_type]}에 없는 치수 항목이에요: {', '.join(sorted(unknown))}")
    return out


def _fabric_name(attributes):
    """혼용률 입력을 DB의 소재 이름(fabrics.fabric_name)으로. 예: 'cotton 60% + polyester 40%'."""
    comp = attributes.get("composition")
    if isinstance(comp, list) and comp:
        return " + ".join(f'{c["fiber"]} {round(c["share"] * 100):d}%' for c in comp)[:100]
    status = attributes.get("composition_status")
    return status if isinstance(status, str) and status else "기타"


def parse_product(body):
    """요청 본문 → 저장소에 넣을 상품. 잘못된 입력은 BadRequest/ValueError(한국어 문장)."""
    if not isinstance(body, dict):
        raise BadRequest("요청 본문은 JSON 객체여야 해요")
    clothing_type = body.get("clothing_type")
    if clothing_type not in SELLER_MEASUREMENTS:
        raise BadRequest(f"상품 종류는 {list(CATEGORY_LABELS)} 중 하나여야 해요")
    brand = _text(body, "brand", "브랜드 이름", 1, 100)
    name = _text(body, "product_name", "상품 이름", 1, 200)
    description = _text(body, "description", "상품 설명", 0, 5000, required=False)
    price = body.get("price")
    if isinstance(price, bool) or not isinstance(price, int) or not (0 < price <= SELLER_MAX_PRICE):
        raise BadRequest(f"가격은 1~{SELLER_MAX_PRICE:,}원 사이의 정수로 입력해 주세요")
    seasons = _choice_list(body, "seasons", "계절", SELLER_SEASONS, 1, 4)
    sizes = _choice_list(body, "sizes", "사이즈", SELLER_SIZES, 1, len(SELLER_SIZES))
    sizes.sort(key=SELLER_SIZES.index)
    colors = _choice_list(body, "colors", "색상", SELLER_COLORS, 1, MAX_COLORS, free_text=True)
    if len(sizes) * len(colors) > SELLER_MAX_OPTIONS:
        raise BadRequest(f"사이즈 × 색상 조합이 너무 많아요 (최대 {SELLER_MAX_OPTIONS}개)")
    measurements = _read_measurements(body, clothing_type, sizes)

    image_url = body.get("image_url") or ""
    if image_url and not is_storable_image_url(image_url):
        raise BadRequest("사진 주소가 올바르지 않아요. 사진을 다시 올려 주세요")

    # 속성 양식 검증·태그 만들기는 B 파트가 한다 (필수 항목이 비면 ValueError)
    attrs = body.get("attrs") or {}
    if not isinstance(attrs, dict):
        raise BadRequest("attrs는 객체여야 해요")
    attrs = dict(attrs)
    if attrs.get("category") not in (None, clothing_type):
        raise BadRequest("상품 종류와 속성 양식의 종류가 달라요")
    attrs["category"] = clothing_type
    if body.get("composition_percent") is not None:
        if attrs.get("composition"):
            raise BadRequest("composition과 composition_percent를 함께 보낼 수 없어요")
        attrs["composition"] = tagging.percent_to_share(body["composition_percent"])
    tagged = tagging.tag_from_attributes(attrs, RULES)
    text_added = [g for g in matching._text_fill_tags(tagged["attributes"], name, description, RULES) if g not in tagged["tags"]]

    return {
        "product_name": name, "clothing_type": clothing_type, "brand": brand, "price": price,
        "description": description, "seasons": seasons, "sizes": sizes, "colors": colors,
        "measurements": measurements, "image_url": image_url,
        "tags": tagged["tags"] + text_added, "text_added": text_added,
        "fit_stated": tagged["fit_stated"] if "fit_stated" in tagged else "unknown",
        "material_basis": tagged.get("material_basis"), "attrs": tagged["attributes"],
        "fabric": _fabric_name(tagged["attributes"]),
        "popularity": SELLER_DEFAULT_POPULARITY, "source": "direct", "registered": True, "seller": brand,
    }


@seller_bp.get("/api/seller/form")
def form():
    f = RULES.attributes
    return jsonify({
        "ok": True,
        "categories": [{"key": c, "label": CATEGORY_LABELS[c]} for c in f["categories"]],
        "attributes": [{k: v for k, v in fld.items() if k != "options"} | {"options": list(fld.get("options", {}))}
                       for fld in f["fields"]],
        "composition_statuses": f["composition_statuses"],
        "seasons": [{"key": s, "label": SEASON_LABELS[s]} for s in SELLER_SEASONS],
        "sizes": list(SELLER_SIZES),
        "colors": list(SELLER_COLORS),
        "measurements": {c: [{"key": k, "label": label} for k, label in fields]
                         for c, (_, fields) in SELLER_MEASUREMENTS.items()},
        "image": {"max_bytes": IMAGE_MAX_BYTES, "types": sorted(IMAGE_TYPES)},
    })


@seller_bp.post("/api/seller/products/validate")
def validate():
    product = parse_product(request.get_json(silent=True))
    return jsonify({"ok": True, "tags": product["tags"], "text_added": product["text_added"],
                    "fit_stated": product["fit_stated"], "fabric": product["fabric"]})


@seller_bp.post("/api/seller/products")
def add_product():
    product = parse_product(request.get_json(silent=True))
    stored = _store().add_product(product)
    return jsonify({"ok": True, "item": public(stored)}), 201


@seller_bp.get("/api/seller/products")
def list_products():
    brand = " ".join((request.args.get("brand") or "").split())
    if not brand:
        raise BadRequest("brand를 보내 주세요")
    return jsonify({"ok": True, "items": [public(i) for i in _store().products_by_brand(brand)]})


@seller_bp.post("/api/seller/images")
def upload_image():
    file = request.files.get("file")
    if file is None:
        raise BadRequest("사진 파일(file)을 보내 주세요")
    data = file.read(IMAGE_MAX_BYTES + 1)
    url = IMAGES.save(data)        # 상품에는 이 값(url)을 적는다. full_url은 바로 열어 볼 때 쓴다
    return jsonify({"ok": True, "url": url, "full_url": absolute_image_url(url, request.host_url)}), 201


@seller_bp.get("/uploads/<path:name>")
def uploaded(name):
    if not isinstance(IMAGES, LocalImageStorage):
        return jsonify({"ok": False, "error": "404 Not Found"}), 404
    return send_from_directory(IMAGES.folder, name)


def public(item):
    """앱에 보낼 상품 모양 (저장소 내부 값은 뺀다). 사진 주소는 요청한 서버 주소를 붙여 절대 주소로."""
    keys = ("product_id", "product_name", "clothing_type", "brand", "price", "description", "seasons", "sizes",
            "colors", "measurements", "image_url", "tags", "fit_stated")
    out = {k: item[k] for k in keys if k in item}
    if "image_url" in out:
        out["image_url"] = absolute_image_url(out["image_url"], request.host_url)
    return out
