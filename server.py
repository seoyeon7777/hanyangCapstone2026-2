"""등록 -> 검증 -> 태그 -> 점수·이유를 잇는 HTTP 모듈 (Flask).  python server.py  (http://127.0.0.1:5000)
앱(팀)이 그대로 붙일 수 있게 만든 얇은 층이다. 상품 저장소는 메모리라서 서버를 끄면 사라진다(저장은 앱 쪽 DB가 맡는다).

GET  /api/form              판매자 등록 양식 정의 (화면을 그리는 데 쓴다)
POST /api/items/validate    양식 입력 검증 + 태그 미리보기. 저장하지 않는다.
POST /api/items             상품 등록(저장). source = direct(판매자) | manual(팀 입력)
GET  /api/items             저장된 상품
POST /api/recommend         (글만 있는 상품은 받지 않는다: source가 direct|manual이거나 tags가 있어야 한다) {"profile": A의 분류 결과, "top_n": 3, "list_size": 10, "mixed_strategy": "blend|quota|quota_safe" (선택), "items": (선택) 저장소 대신 쓸 상품 목록}
오류는 400 {"ok": false, "error": "..."}. 혼용률은 attrs.composition(비율 0~1) 또는 composition_percent(퍼센트 0~100) 중 하나로 보낸다.
"""
from flask import Flask, jsonify, request

import matching
import tagging

FORM_SOURCES = matching.FORM_SOURCES


def create_app(rules: matching.Rules | None = None) -> Flask:
    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = 1_000_000      # 요청 본문 1MB 제한 (넘으면 413)
    app.json.ensure_ascii = False
    rules = rules or matching.load_rules()
    store: dict = {}
    next_id = [1]

    def fail(msg: str, code: int = 400):
        return jsonify({"ok": False, "error": msg}), code

    @app.errorhandler(413)
    def on_too_large(e):
        return fail("요청이 너무 크다 (최대 1MB)", 413)

    def json_body() -> dict:
        body = request.get_json(silent=True)
        if body is None:
            return {}
        if not isinstance(body, dict):
            raise ValueError("요청 본문은 JSON 객체여야 한다")
        return body

    @app.errorhandler(ValueError)
    def on_value_error(e):
        return fail(str(e))

    def read_attrs(body: dict) -> dict:
        attrs = body.get("attrs") or {}
        if not isinstance(attrs, dict):
            raise ValueError("attrs는 객체여야 한다")
        attrs = dict(attrs)
        if body.get("composition_percent") is not None:
            if attrs.get("composition"):
                raise ValueError("composition과 composition_percent를 함께 보낼 수 없다")
            attrs["composition"] = tagging.percent_to_share(body["composition_percent"])
        return attrs

    @app.get("/api/form")
    def form():
        f = rules.attributes
        return jsonify({
            "categories": f["categories"],
            "composition_statuses": f["composition_statuses"],
            "fields": [{k: v for k, v in fld.items() if k != "options"} | {"options": list(fld.get("options", {}))} for fld in f["fields"]],
        })

    @app.post("/api/items/validate")
    def validate():
        body = json_body()
        r = tagging.tag_from_attributes(read_attrs(body), rules)
        text_added = []
        if body.get("text_fill") is not False and (body.get("product_name") or body.get("description")):
            text_added = [g for g in matching._text_fill_tags(r["attributes"], body.get("product_name", ""), body.get("description", ""), rules) if g not in r["tags"]]
        return jsonify({"ok": True, "tags": r["tags"] + text_added, "text_added": text_added, "fit_stated": r["fit_stated"],
                        "material_basis": r["material_basis"], "attributes": r["attributes"]})

    @app.post("/api/items")
    def add_item():
        body = json_body()
        source, clothing_type = body.get("source"), body.get("clothing_type")
        if source not in FORM_SOURCES:
            return fail(f'source는 {list(FORM_SOURCES)} 중 하나여야 한다')
        if not isinstance(body.get("product_name"), str) or not body["product_name"].strip():
            return fail("product_name은 비어 있지 않은 문자열이어야 한다")
        attrs = read_attrs(body)
        if attrs.get("category") != clothing_type:
            return fail(f'clothing_type={clothing_type!r}와 attrs.category={attrs.get("category")!r}가 다르다')
        if body.get("product_id") is None:
            product_id = next_id[0]
            while product_id in store:         # 직접 넣은 숫자 id와 겹치지 않게 건너뛴다
                product_id += 1
            next_id[0] = product_id + 1
        else:
            product_id = body["product_id"]
            if isinstance(product_id, bool) or not isinstance(product_id, (str, int)) or product_id == "":
                return fail("product_id는 비어 있지 않은 문자열 또는 정수여야 한다")
        if product_id in store:
            return fail(f"product_id {product_id!r}가 이미 있다", 409)
        item = {"product_id": product_id, "clothing_type": clothing_type, "product_name": body["product_name"], "description": body.get("description") or "",
                "source": source, "attrs": attrs}
        matching.validate_item(item)        # product_id 형식·clothing_type·description 길이 (잘못되면 ValueError -> 400)
        if body.get("text_fill") is False:
            item["text_fill"] = False
        if body.get("review") is not None:
            matching.review_agreement(body["review"], rules)      # 형식 검증 (n, mismatch)
            item["review"] = body["review"]
        # 태깅은 등록할 때 한 번 한다. 추천은 여기서 저장한 태그만 읽는다 (검증 실패 시 ValueError -> 400).
        r = tagging.tag_from_attributes(attrs, rules)
        text_added = [] if item.get("text_fill") is False else [g for g in matching._text_fill_tags(r["attributes"], item["product_name"], item["description"], rules) if g not in r["tags"]]
        item.update({"tags": r["tags"] + text_added, "text_added": text_added, "fit_stated": r["fit_stated"], "material_basis": r["material_basis"]})
        store[product_id] = item
        return jsonify({"ok": True, "item": item}), 201

    @app.get("/api/items")
    def list_items():
        return jsonify({"ok": True, "items": list(store.values())})

    @app.post("/api/recommend")
    def recommend():
        body = json_body()
        if "profile" not in body:
            return fail("profile이 필요하다")
        items = body["items"] if body.get("items") is not None else list(store.values())
        # 서비스는 양식으로 등록한 상품(source=direct|manual)이나 이미 태그가 붙은 상품(tags)만 받는다.
        # 글만 있는 상품(다른 쇼핑몰에서 가져온 것)을 글에서 읽어 추천하는 경로는 평가용(matching.py 직접 호출)이다.
        if not isinstance(items, list):
            return fail("items는 목록이어야 한다")
        for it in items:
            matching.validate_item(it)
            if it.get("source") not in FORM_SOURCES and it.get("tags") is None:
                return fail(f'상품 {it.get("product_id")}: source가 {list(FORM_SOURCES)} 중 하나이거나 tags가 있어야 한다 (글만 있는 상품은 서비스에서 받지 않는다)')
        top_n = body.get("top_n", 3)
        if isinstance(top_n, bool) or not isinstance(top_n, int) or top_n < 1:
            return fail("top_n은 1 이상의 정수여야 한다")
        list_size = body.get("list_size", 10)
        if isinstance(list_size, bool) or not isinstance(list_size, int) or list_size < 1:
            return fail("list_size는 1 이상의 정수여야 한다")
        out = matching.recommend(body["profile"], items, rules, top_n=top_n, list_size=list_size, mixed_strategy=body.get("mixed_strategy"))
        return jsonify({"ok": True, **out})

    return app


if __name__ == "__main__":
    create_app().run(debug=False)
