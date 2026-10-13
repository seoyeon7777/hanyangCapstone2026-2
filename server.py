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

FORM_SOURCES = matching.FORM_SOURCES


def create_app(rules: matching.Rules | None = None) -> Flask:
    app = Flask(__name__)
    app.json.ensure_ascii = False
    rules = rules or matching.load_rules()
    store: dict = {}

    def fail(msg: str, code: int = 400):
        return jsonify({"ok": False, "error": msg}), code

    @app.errorhandler(ValueError)
    def on_value_error(e):
        return fail(str(e))

    def read_attrs(body: dict) -> dict:
        attrs = dict(body.get("attrs") or {})
        if body.get("composition_percent") is not None:
            if attrs.get("composition"):
                raise ValueError("composition과 composition_percent를 함께 보낼 수 없다")
            attrs["composition"] = matching.percent_to_share(body["composition_percent"])
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
        body = request.get_json(silent=True) or {}
        r = matching.tag_from_attributes(read_attrs(body), rules)
        text_added = []
        if body.get("text_fill") is not False and (body.get("name") or body.get("desc")):
            text_added = [g for g in matching._text_fill_tags(r["attributes"], body.get("name", ""), body.get("desc", ""), rules) if g not in r["tags"]]
        return jsonify({"ok": True, "tags": r["tags"] + text_added, "text_added": text_added, "fit_stated": r["fit_stated"],
                        "material_basis": r["material_basis"], "attributes": r["attributes"]})

    @app.post("/api/items")
    def add_item():
        body = request.get_json(silent=True) or {}
        source, category = body.get("source"), body.get("category")
        if source not in FORM_SOURCES:
            return fail(f'source는 {list(FORM_SOURCES)} 중 하나여야 한다')
        if not body.get("name"):
            return fail("name이 필요하다")
        attrs = read_attrs(body)
        if attrs.get("category") != category:
            return fail(f'category={category!r}와 attrs.category={attrs.get("category")!r}가 다르다')
        item_id = body.get("item_id", len(store) + 1)
        if item_id in store:
            return fail(f"item_id {item_id!r}가 이미 있다", 409)
        item = {"item_id": item_id, "category": category, "name": body["name"], "desc": body.get("desc", ""),
                "source": source, "attrs": attrs}
        if body.get("text_fill") is False:
            item["text_fill"] = False
        if body.get("review") is not None:
            matching.review_agreement(body["review"], rules)      # 형식 검증 (n, mismatch)
            item["review"] = body["review"]
        matching.match_item(item, {"ratios": {"S": 1, "W": 0, "N": 0}, "primary": "S", "is_mixed": False}, rules)   # 검증만 한다 (실패하면 ValueError -> 400)
        store[item_id] = item
        return jsonify({"ok": True, "item": item}), 201

    @app.get("/api/items")
    def list_items():
        return jsonify({"ok": True, "items": list(store.values())})

    @app.post("/api/recommend")
    def recommend():
        body = request.get_json(silent=True) or {}
        if "profile" not in body:
            return fail("profile이 필요하다")
        items = body["items"] if body.get("items") is not None else list(store.values())
        # 서비스는 양식으로 등록한 상품(source=direct|manual)이나 이미 태그가 붙은 상품(tags)만 받는다.
        # 글만 있는 상품(다른 쇼핑몰에서 가져온 것)을 글에서 읽어 추천하는 경로는 평가용(matching.py 직접 호출)이다.
        for it in items:
            if it.get("source") not in FORM_SOURCES and it.get("tags") is None:
                return fail(f'상품 {it.get("item_id")}: source가 {list(FORM_SOURCES)} 중 하나이거나 tags가 있어야 한다 (글만 있는 상품은 서비스에서 받지 않는다)')
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
