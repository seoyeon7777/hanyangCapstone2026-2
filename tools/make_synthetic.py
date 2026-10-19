"""가상 판매자 등록 샘플 100개를 만든다.  python tools/make_synthetic.py  ->  samples/synthetic_sellers.json
- 실제 쇼핑몰의 상품·문구를 가져오지 않았다. 브랜드와 상품은 모두 가상이고, 설명은 쇼핑몰식 문체를 흉내 내 새로 썼다.
- 각 상품에 '시나리오'와 '기대 결과'(expect)가 붙어 있다. 기대 결과는 설계 문서의 규칙에서 내가 정한 것이라, 이 샘플은 규칙대로 동작하는지(로직)를 보는 용도이고
  실제 상품에서의 정확도를 재는 데이터가 아니다.
expect 키: error(등록이 이 문구를 포함한 오류로 거절돼야 함) / fit_stated / has(tags에 있어야 함) / lacks(없어야 함) / added(text_added가 정확히 이 집합) / fibers(materials 섬유 집합)
"""
import json
from pathlib import Path

CARE = "세탁방법 | 드라이클리닝 | 일반세탁 | 단독세탁 | 손세탁\n핏 | 슬림핏 | 기본핏 | 루즈핏/오버핏 | 와이드핏\n신축성 | 없음 | 약간 | 좋음 | -\n두께감 | 얇음 | 보통 | 두꺼움 | -"
RETURN = "교환 및 반품 안내\n단순 변심에 의한 교환·환불은 수령 후 7일 이내 가능합니다.\n천연가죽 제품의 경우 미세한 스크레치, 주름, 반점 등은 불량으로 보지 않습니다."
MODEL = "MODEL\n여성 168cm / 착용 사이즈 M"

LEN = {"top": "length_top", "outer": "length_top", "pants": "length_pants", "skirt": "length_skirt", "dress": "length_skirt"}
items: list[dict] = []


def add(group, cat, name, desc, fit, length, comp, expect, source="direct", scenario="", text_fill=None, review=None, **extra):
    """comp: [(섬유, 퍼센트), ...] 또는 상태 문자열('확인 불가'|'해당 없음'). extra: silhouette, waist, details, fabrics, item_type 등 양식 값."""
    attrs = {"category": cat}
    if fit is not None:
        attrs["fit"] = fit
    if length is not None:
        attrs[LEN[cat]] = length
    attrs.update({k: v for k, v in extra.items() if k != "raw"})
    body = {"clothing_type": cat, "product_name": name, "description": desc, "source": source, "attrs": attrs}
    if isinstance(comp, str):
        attrs["composition_status"] = comp
    elif comp:
        body["composition_percent"] = [{"fiber": f, "percent": p} for f, p in comp]
    if text_fill is False:
        body["text_fill"] = False
    if review is not None:
        body["review"] = review
    body.update(extra.get("raw", {}))
    items.append({"product_id": len(items) + 1, "group": group, "scenario": scenario, "body": {**body, "product_id": len(items) + 1}, "expect": expect})


# ---- A. 양식을 모두 채운 등록 (1-12)
G = "A 양식을 모두 채움"
add(G, "top", "[모노브릭] 슬림 케이블 니트 풀오버", "차분한 톤의 케이블 짜임 니트. 몸에 붙는 슬림한 실루엣으로 이너로도 단독으로도 좋아요.\n" + MODEL, "슬림", "기본", [("cotton", 70), ("acrylic", 30)],
    {"has": ["fit:slim", "mat:knit", "mat:cotton"], "lacks": ["len:crop", "len:long"]}, fabrics=["니트"], details=["해당 없음"], scenario="전부 입력, 소재 니트")
add(G, "top", "[데일리온] 박시 크롭 반팔 티셔츠 (3color)", "여유 있는 박시 핏에 기장은 짧은 크롭. 두께감 있는 20수 원단.", "오버(루즈)", "크롭", [("cotton", 100)],
    {"has": ["fit:over", "len:crop", "mat:cotton"], "lacks": ["det:ruffle"]}, details=["해당 없음"], fabrics=["해당 없음"], scenario="오버핏 크롭 코튼")
add(G, "top", "[루멘] 롱 카디건", "무릎 위까지 내려오는 롱 카디건. 앞은 오픈형으로 가볍게 걸치기 좋아요.", "레귤러", "롱", [("wool", 50), ("acrylic", 50)],
    {"has": ["len:long", "mat:knit", "mat:wool"], "fit_stated": "regular"}, item_type="롱 카디건", fabrics=["니트"], scenario="롱 카디건 종류 선택")
add(G, "outer", "[아틀리에 하나] 싱글 테일러드 재킷", "어깨선이 정리되는 테일러드 재킷. 안감이 있어 구김이 적어요.", "슬림", "기본", [("wool", 60), ("polyester", 40)],
    {"has": ["fit:slim", "item:tailored_jacket", "mat:wool"]}, item_type="테일러드 재킷", scenario="테일러드 재킷")
add(G, "outer", "[소이] 무스탕 롱 자켓", "부드러운 인조 무스탕 소재의 롱 기장 자켓. 보온성이 좋습니다.", "오버(루즈)", "롱", "확인 불가",
    {"has": ["fit:over", "len:long", "mat:mustang"], "fibers": []}, fabrics=["무스탕"], scenario="혼용률 확인 불가 + 무스탕")
add(G, "pants", "[모노브릭] 하이웨이스트 스트레이트 팬츠", "허리선이 높은 일자 핏. 다리 라인이 곧게 떨어져요.", "슬림", "풀 길이", [("cotton", 98), ("spandex", 2)],
    {"has": ["fit:slim", "sil:IH", "waist:high", "mat:cotton"]}, silhouette="일자(스트레이트)", waist="하이웨이스트", fabrics=["해당 없음"], scenario="일자 하이웨이스트")
add(G, "pants", "[데일리온] 와이드 데님 팬츠", "넉넉한 통 와이드 데님. 워싱이 자연스러워요.", "오버(루즈)", "풀 길이", [("cotton", 100)],
    {"has": ["fit:over", "sil:wide", "mat:denim", "mat:cotton"], "lacks": ["waist:high"]}, silhouette="와이드", waist="보통", fabrics=["데님"], scenario="와이드 데님, 허리 보통")
add(G, "pants", "[루멘] 스키니 앵클 팬츠", "발목이 드러나는 기장의 스키니 팬츠. 신축성이 좋아요.", "레귤러", "발목 길이", [("cotton", 95), ("spandex", 5)],
    {"has": ["sil:skinny", "mat:cotton"], "fit_stated": "regular"}, silhouette="스키니", scenario="스키니 레귤러")
add(G, "skirt", "[아틀리에 하나] 하이웨이스트 타이트 스커트", "몸선을 따라 떨어지는 H라인 미디 스커트.", "슬림", "미디", [("polyester", 70), ("rayon", 30)],
    {"has": ["fit:slim", "item:tight_skirt", "waist:high"]}, item_type="타이트 스커트", waist="하이웨이스트", scenario="타이트 스커트, 혼방")
add(G, "skirt", "[소이] 시폰 러플 맥시 스커트", "A라인으로 퍼지는 맥시 기장. 시폰 소재에 러플 단이 포인트.", "레귤러", "맥시", [("polyester", 100)],
    {"has": ["len:long", "sil:A", "det:ruffle", "mat:chiffon"]}, silhouette="A라인", details=["러플·프릴"], fabrics=["시폰"], scenario="시폰 러플 맥시")
add(G, "dress", "[루멘] 실크 리본 미니 원피스", "목 뒤 리본 장식이 있는 슬림 미니 원피스.", "슬림", "미니", [("silk", 100)],
    {"has": ["fit:slim", "det:ribbon", "mat:silk"]}, details=["리본"], scenario="실크 리본 원피스")
add(G, "dress", "[모노브릭] 벨벳 맥시 원피스", "광택 있는 벨벳 맥시 원피스. 넉넉한 실루엣.", "오버(루즈)", "맥시", [("polyester", 100)],
    {"has": ["fit:over", "len:long", "mat:velvet"]}, fabrics=["벨벳"], scenario="벨벳 맥시")

# ---- B. 필수만 입력하고 설명에서 보충 (13-24)
G = "B 필수만 입력, 글 보충"
add(G, "pants", "와이드 데님 팬츠 [연청]", "하이웨이스트 와이드 핏의 데님 팬츠입니다. 편안한 통을 살렸어요.", "레귤러", "풀 길이", [("cotton", 100)],
    {"added": ["sil:wide", "mat:denim", "waist:high"], "has": ["mat:cotton"]}, scenario="바지: 실루엣·소재·허리 보충")
add(G, "skirt", "플리츠 A라인 미디 스커트", "시폰 소재로 가볍게 흔들리고 러플 디테일이 포인트예요.", "레귤러", "미디", [("polyester", 100)],
    {"added": ["sil:A", "mat:chiffon", "det:ruffle"]}, scenario="치마: A라인·시폰·러플 보충")
add(G, "top", "러플 칼라 블라우스", "소매 끝 프릴 장식과 리본 타이가 있는 블라우스.", "슬림", "기본", [("polyester", 100)],
    {"added": ["det:ruffle", "det:ribbon"]}, scenario="상의: 프릴·리본 보충")
add(G, "top", "벨벳 리본 홀터 탑", "벨벳 소재에 리본 장식이 있는 홀터넥 탑.", "슬림", "크롭", [("polyester", 100)],
    {"added": ["mat:velvet", "det:ribbon"], "has": ["len:crop"]}, scenario="벨벳·리본 보충")
add(G, "outer", "코듀로이 오버 자켓", "골덴 소재로 부드럽게 떨어지는 오버 자켓입니다.", "오버(루즈)", "기본", [("cotton", 100)],
    {"added": ["mat:corduroy"], "has": ["fit:over", "mat:cotton"]}, scenario="골덴 보충")
add(G, "outer", "무스탕 숏 자켓", "부드러운 무스탕 안감과 겉감. 따뜻하게 입어요.", "레귤러", "기본", "확인 불가",
    {"added": ["mat:mustang"]}, scenario="무스탕 보충, 혼용률 확인 불가")
add(G, "outer", "페이크 퍼 롱코트", "포근한 페이크 퍼 소재의 롱코트. 카라까지 퍼로 마감했습니다.", "레귤러", "롱", [("polyester", 100)],
    {"added": ["mat:fur"], "has": ["len:long"]}, scenario="페이크 퍼 보충")
add(G, "pants", "스트레이트 슬랙스", "일자로 떨어지는 스트레이트 핏으로 단정하게 입어요.", "레귤러", "풀 길이", [("polyester", 65), ("rayon", 30), ("spandex", 5)],
    {"added": ["sil:IH"]}, scenario="바지: 일자 보충")
add(G, "skirt", "클래식 타이트 스커트", "타이트 스커트 라인으로 단정한 오피스 룩을 완성합니다.", "레귤러", "미디", [("polyester", 100)],
    {"added": ["item:tight_skirt"]}, scenario="치마: 종류 보충")
add(G, "outer", "오피스 룩 재킷", "단정한 테일러드 재킷 디자인. 안감 포함.", "레귤러", "기본", [("wool", 40), ("polyester", 60)],
    {"added": ["item:tailored_jacket"], "has": ["mat:wool"]}, scenario="재킷: 종류 보충, 울 혼방")
add(G, "top", "모헤어 혼방 니트 풀오버", "모헤어가 섞여 보송하게 올라온 니트. 가볍고 따뜻해요.", "레귤러", "기본", [("acrylic", 60), ("nylon", 30), ("wool", 10)],
    {"added": ["mat:mohair", "mat:knit"], "has": ["mat:nylon"], "lacks": ["mat:wool"]}, scenario="모헤어 보충, 울 10%는 태그 없음")
add(G, "pants", "하이웨이스트 스키니 진", "스키니 핏 데님. 하이웨이스트라 다리가 길어 보여요.", "슬림", "풀 길이", [("cotton", 90), ("spandex", 10)],
    {"added": ["sil:skinny", "mat:denim", "waist:high"]}, scenario="바지: 스키니·데님·허리 보충")

# ---- C. 설명 없이 상품명만 (25-29)
G = "C 설명 비움"
add(G, "pants", "와이드 데님 팬츠", "", "레귤러", "풀 길이", [("cotton", 100)], {"added": ["sil:wide", "mat:denim"]}, scenario="상품명만: 와이드·데님")
add(G, "top", "러플 블라우스", "", "슬림", "기본", [("polyester", 100)], {"added": ["det:ruffle"]}, scenario="상품명만: 러플")
add(G, "top", "베이직 코튼 티셔츠", "", "레귤러", "기본", [("cotton", 100)], {"added": [], "has": ["mat:cotton"]}, scenario="섬유 키워드는 혼용률이 소유 → 보충 없음")
add(G, "skirt", "플레어 스커트", "", "레귤러", "미디", [("polyester", 100)], {"added": ["sil:A"]}, scenario="상품명만: 플레어")
add(G, "pants", "일자 팬츠", "", "레귤러", "풀 길이", [("polyester", 100)], {"added": ["sil:IH"]}, scenario="상품명만: 일자")

# ---- D. '해당 없음'을 직접 고른 항목은 글로 뒤집지 않는다 (30-39)
G = "D 해당 없음 선택 유지"
add(G, "top", "프릴 장식 블라우스", "소매에 러플 장식이 들어간 블라우스입니다.", "슬림", "기본", [("polyester", 100)], {"lacks": ["det:ruffle"], "added": []}, details=["해당 없음"], scenario="디테일 해당 없음 vs 글의 러플")
add(G, "pants", "데님 팬츠", "와이드 핏의 데님 소재 팬츠.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["sil:wide", "mat:denim"], "added": []}, silhouette="해당 없음", fabrics=["해당 없음"], scenario="실루엣·소재 해당 없음 vs 글의 와이드·데님")
add(G, "top", "니트 풀오버", "모헤어 혼방으로 보송한 느낌의 풀오버.", "레귤러", "기본", [("acrylic", 100)], {"lacks": ["mat:mohair"], "added": ["mat:knit"]}, item_type="해당 없음", scenario="종류 해당 없음 vs 글의 모헤어 (상품명의 니트는 보충됨)")
add(G, "outer", "클래식 자켓", "테일러드 재킷 스타일로 단정하게 떨어집니다.", "레귤러", "기본", [("polyester", 100)], {"lacks": ["item:tailored_jacket"], "added": []}, item_type="해당 없음", scenario="종류 해당 없음 vs 글의 테일러드")
add(G, "skirt", "미디 스커트", "하이웨이스트로 올려 입는 미디 스커트.", "레귤러", "미디", [("polyester", 100)], {"lacks": ["waist:high"], "added": []}, waist="보통", scenario="허리 보통 vs 글의 하이웨이스트")
add(G, "skirt", "플레어 스커트", "A라인 플레어로 퍼지는 실루엣.", "레귤러", "미디", [("polyester", 100)], {"lacks": ["sil:A"], "added": []}, silhouette="해당 없음", scenario="실루엣 해당 없음 vs 글의 A라인")
add(G, "top", "베이직 긴팔 티셔츠", "부드러운 니트 소재의 긴팔 티셔츠.", "레귤러", "기본", [("cotton", 100)], {"lacks": ["mat:knit"], "added": []}, fabrics=["해당 없음"], scenario="소재 해당 없음 vs 글의 니트")
add(G, "dress", "셔링 원피스", "리본과 러플로 포인트를 준 원피스.", "레귤러", "미디", [("polyester", 100)], {"lacks": ["det:ribbon", "det:ruffle"], "added": []}, details=["해당 없음"], scenario="디테일 해당 없음 vs 글의 리본·러플")
add(G, "pants", "코튼 팬츠", "로우라이즈가 아닌 하이웨이스트 핏.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["waist:high"], "added": []}, waist="로우라이즈", scenario="허리 로우라이즈 vs 글의 하이웨이스트")
add(G, "skirt", "슬림 스커트", "타이트 스커트 실루엣의 미디 기장.", "슬림", "미디", [("polyester", 100)], {"lacks": ["item:tight_skirt"], "added": []}, item_type="해당 없음", scenario="종류 해당 없음 vs 글의 타이트 스커트")

# ---- E. 양식과 글이 충돌하면 양식이 이긴다 (40-49)
G = "E 양식과 글 충돌"
add(G, "top", "오버핏 후드 티셔츠", "오버핏 느낌의 박시한 실루엣, 넉넉하게 떨어져요.", "레귤러", "기본", [("cotton", 100)], {"lacks": ["fit:over"], "fit_stated": "regular"}, scenario="핏 레귤러 vs 글의 오버핏")
add(G, "top", "오버사이즈 셔츠", "넉넉한 오버사이즈 실루엣. 여유 있게 입으세요.", "슬림", "기본", [("cotton", 100)], {"has": ["fit:slim"], "lacks": ["fit:over"]}, scenario="핏 슬림 vs 글의 오버사이즈")
add(G, "outer", "롱코트 스타일 자켓", "롱코트 기장으로 무릎 위까지 내려옵니다.", "레귤러", "기본", [("wool", 70), ("polyester", 30)], {"lacks": ["len:long"], "has": ["mat:wool"]}, scenario="기장 기본 vs 글의 롱코트")
add(G, "pants", "와이드 일자 팬츠", "와이드 핏으로 통이 넉넉하지만 라인은 곧게.", "레귤러", "풀 길이", [("polyester", 100)], {"has": ["sil:IH"], "lacks": ["sil:wide"]}, silhouette="일자(스트레이트)", scenario="실루엣 일자 vs 글의 와이드")
add(G, "top", "코튼 100% 티셔츠", "코튼 100%의 순면 티셔츠입니다.", "레귤러", "기본", "확인 불가", {"lacks": ["mat:cotton"], "fibers": []}, scenario="혼용률 확인 불가 vs 글의 코튼 100%")
add(G, "pants", "코튼 와이드 팬츠", "코튼 소재로 시원한 와이드 팬츠.", "레귤러", "풀 길이", [("polyester", 100)], {"lacks": ["mat:cotton"]}, scenario="혼용률 폴리 100 vs 글의 코튼")
add(G, "top", "포켓 디테일 티셔츠", "데님 포켓 디테일이 들어간 티셔츠.", "레귤러", "기본", [("cotton", 100)], {"has": ["mat:knit"], "lacks": ["mat:denim"], "added": []}, fabrics=["니트"], scenario="소재 니트 선택 vs 글의 데님")
add(G, "outer", "슬림핏 블루종", "슬림핏으로 몸에 붙게 떨어지는 블루종.", "오버(루즈)", "기본", [("nylon", 100)], {"has": ["fit:over"], "lacks": ["fit:slim"]}, scenario="핏 오버 vs 글의 슬림핏")
add(G, "skirt", "맥시 기장 스커트", "발목까지 오는 맥시 기장입니다.", "레귤러", "미니", [("polyester", 100)], {"lacks": ["len:long"]}, scenario="기장 미니 vs 글의 맥시")
add(G, "dress", "스트레이트 라인 원피스", "스트레이트 라인으로 떨어지는 셔츠 원피스.", "레귤러", "미디", [("cotton", 100)], {"has": ["sil:A"], "lacks": ["sil:IH"]}, silhouette="A라인", scenario="실루엣 A라인 vs 글의 스트레이트")

# ---- F. 설명에 섞인 노이즈 (50-62)
G = "F 설명 노이즈"
add(G, "top", "베이직 후드 집업", "정석 레귤러 핏의 후드 집업입니다.\n" + CARE, "레귤러", "기본", [("cotton", 60), ("polyester", 40)],
    {"lacks": ["fit:slim", "fit:over", "sil:wide", "sil:IH"], "fit_stated": "regular", "added": []}, scenario="케어라벨 핏 범례표")
add(G, "pants", "워커 팬츠", "작업복 느낌의 코튼 팬츠.\n" + RETURN, "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["mat:leather"], "added": []}, scenario="반품 안내문의 '천연가죽 제품의 경우'")
add(G, "pants", "카고 팬츠", "크랙 가공된 소가죽을 사용하여 조화로운 가죽패치 사용. 포켓이 많은 카고 디자인.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["mat:leather"], "added": []}, scenario="가죽패치")
add(G, "outer", "나일론 윈드브레이커", "가벼운 나일론 소재의 윈드브레이커.\n제조일자: 2026년 06월\n제조국: 베트남", "레귤러", "기본", [("nylon", 100)], {"lacks": ["sil:IH"], "added": [], "has": ["mat:nylon"]}, scenario="제조일자의 '일자'")
add(G, "top", "스퀘어넥 티셔츠", "넉넉한 넥라인이 어깨선을 시원하게 보여줍니다.", "슬림", "기본", [("cotton", 100)], {"lacks": ["fit:over"], "added": []}, scenario="'넉넉한 넥라인'")
add(G, "top", "소프트 가디건", "블랙 니트나 와이드 팬츠와 매치해 입으면 좋아요. 데님과도 잘 어울립니다.", "레귤러", "기본", [("acrylic", 100)], {"added": []}, scenario="코디 추천 문장")
add(G, "pants", "코튼 팬츠", "면 소재 팬츠.\n세탁 시 단독 세탁을 권장하며 울 코스로 세탁해 주세요.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["mat:wool"], "added": []}, scenario="세탁 문구의 '울 코스'")
add(G, "pants", "스트레이트 진", "COLOR : DENIM\n컬러는 연한 청색입니다.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["mat:denim"], "added": ["sil:IH"]}, scenario="색상명 DENIM (상품명의 스트레이트는 보충됨)")
add(G, "top", "소매 포인트 니트", "소매 끝을 벨벳 테이프로 마감했어요.", "레귤러", "기본", [("acrylic", 100)], {"lacks": ["mat:velvet"], "added": ["mat:knit"]}, scenario="벨벳 테이프(장식)")
add(G, "outer", "겨울 서울 한정 점퍼", "서울 본사 출고, 겨울 시즌 신상입니다.", "레귤러", "기본", [("nylon", 100)], {"lacks": ["mat:wool"], "added": []}, scenario="'서울'·'겨울'의 울")
add(G, "pants", "사이즈 안내 팬츠", "사이즈 064,067,070 WIDE 중 선택해 주세요.", "레귤러", "풀 길이", [("cotton", 100)], {"lacks": ["sil:wide"], "added": []}, scenario="사이즈 목록의 WIDE")
add(G, "outer", "와이드 카라 코트", "와이드 카라 디테일이 포인트인 코트.", "레귤러", "기본", [("wool", 50), ("polyester", 50)], {"lacks": ["sil:wide"], "added": []}, scenario="와이드 카라")
add(G, "top", "와이드 핏 티셔츠", "와이드 핏으로 넉넉하게 떨어지는 티셔츠입니다.", "레귤러", "기본", [("cotton", 100)], {"lacks": ["sil:wide"], "added": []}, scenario="상의에는 실루엣 항목이 없다 → 보충하지 않음")

# ---- G. 혼용률 경계와 변형 (63-72)
G = "G 혼용률"
add(G, "top", "코튼 혼방 티셔츠", "", "레귤러", "기본", [("cotton", 25), ("polyester", 75)], {"lacks": ["mat:cotton"], "fibers": ["cotton", "polyester"]}, scenario="코튼 25% (30% 미만)")
add(G, "top", "울 혼방 니트", "", "레귤러", "기본", [("wool", 30), ("polyester", 70)], {"has": ["mat:wool"]}, scenario="울 정확히 30%")
add(G, "outer", "울 나일론 혼방 코트", "", "레귤러", "기본", [("wool", 45), ("nylon", 35), ("polyester", 20)], {"has": ["mat:wool", "mat:nylon"]}, scenario="3종 혼방")
add(G, "top", "원단 정보 미기재 티셔츠", "", "레귤러", "기본", "확인 불가", {"fibers": []}, scenario="혼용률 확인 불가")
add(G, "outer", "리얼 레더 자켓", "", "슬림", "기본", "해당 없음", {"has": ["mat:leather"], "fibers": []}, fabrics=["가죽"], scenario="가죽 → 혼용률 해당 없음")
add(G, "top", "코튼 100% 티셔츠", "", "레귤러", "기본", [("cotton", 100)], {"has": ["mat:cotton"], "lacks": ["mat:wool", "mat:nylon"]}, scenario="한 가지 섬유")
add(G, "pants", "폴리 트레이닝 팬츠", "", "레귤러", "풀 길이", [("polyester", 100)], {"lacks": ["mat:cotton", "mat:wool", "mat:nylon"], "fibers": ["polyester"]}, scenario="점수 없는 섬유만")
add(G, "top", "실크 코튼 블라우스", "", "레귤러", "기본", [("silk", 60), ("cotton", 40)], {"has": ["mat:silk", "mat:cotton"]}, scenario="실크 혼방")
add(G, "top", "린넨 코튼 셔츠", "", "레귤러", "기본", [("linen", 55), ("cotton", 45)], {"has": ["mat:linen", "mat:cotton"]}, scenario="린넨 혼방")
add(G, "outer", "캐시미어 울 코트", "", "레귤러", "기본", [("cashmere", 20), ("wool", 80)], {"has": ["mat:wool"], "lacks": ["mat:cashmere"]}, scenario="캐시미어 20%는 태그 없음")

# ---- H. 글 보충 끄기 (73-75)
G = "H 글 보충 끔"
add(G, "pants", "와이드 데님 팬츠", "하이웨이스트 와이드 데님.", "레귤러", "풀 길이", [("cotton", 100)], {"added": [], "lacks": ["sil:wide", "mat:denim"]}, text_fill=False, scenario="text_fill=false")
add(G, "top", "러플 블라우스", "", "슬림", "기본", [("polyester", 100)], {"added": [], "lacks": ["det:ruffle"]}, text_fill=False, scenario="상품명만 있어도 끌 수 있음")
add(G, "skirt", "A라인 스커트", "", "레귤러", "미디", [("polyester", 100)], {"added": [], "lacks": ["sil:A"]}, text_fill=False, scenario="치마 text_fill=false")

# ---- I. 잘못된 등록은 거절돼야 한다 (76-89)
G = "I 잘못된 등록"
add(G, "top", "핏 누락 티셔츠", "", None, "기본", [("cotton", 100)], {"error": "핏"}, scenario="핏 누락")
add(G, "top", "기장 누락 티셔츠", "", "레귤러", None, [("cotton", 100)], {"error": "기장"}, scenario="기장 누락")
add(G, "top", "혼용률 누락 티셔츠", "", "레귤러", "기본", None, {"error": "혼용률"}, scenario="혼용률도 확인 불가도 없음")
add(G, "top", "바지 기장을 쓴 티셔츠", "", "레귤러", "기본", [("cotton", 100)], {"error": "없는 항목"}, length_pants="풀 길이", scenario="상의에 바지 기장")
add(G, "top", "없는 핏 선택", "", "타이트", "기본", [("cotton", 100)], {"error": "없는 선택지"}, scenario="핏에 없는 값")
add(G, "top", "롱 카디건인데 기본 기장", "", "레귤러", "기본", [("acrylic", 100)], {"error": "모순"}, item_type="롱 카디건", scenario="롱 카디건 ⇄ 기본")
add(G, "pants", "크롭 팬츠인데 풀 길이", "", "레귤러", "풀 길이", [("cotton", 100)], {"error": "모순"}, item_type="크롭 팬츠", scenario="크롭 팬츠 ⇄ 풀 길이")
add(G, "top", "합계 90% 티셔츠", "", "레귤러", "기본", [("cotton", 60), ("polyester", 30)], {"error": "합계"}, scenario="혼용률 합계 90%")
add(G, "top", "0% 섬유 티셔츠", "", "레귤러", "기본", [("cotton", 100), ("polyester", 0)], {"error": "퍼센트"}, scenario="섬유 0%")
add(G, "top", "섬유 중복 티셔츠", "", "레귤러", "기본", [("cotton", 50), ("cotton", 50)], {"error": "중복"}, scenario="같은 섬유 두 번")
add(G, "top", "수치와 확인 불가 동시", "", "레귤러", "기본", [("cotton", 100)], {"error": "함께"}, composition_status="확인 불가", scenario="혼용률 수치 + 상태")
add(G, "top", "해당 없음과 리본 동시 선택", "", "레귤러", "기본", [("cotton", 100)], {"error": "함께"}, details=["해당 없음", "리본"], scenario="디테일 해당 없음 + 리본")
add(G, "top", "알 수 없는 출처", "", "레귤러", "기본", [("cotton", 100)], {"error": "source"}, source="import", scenario="source가 direct/manual이 아님")
add(G, "top", "카테고리 불일치", "", "레귤러", "기본", [("cotton", 100)], {"error": "다르다"}, raw={"clothing_type": "pants"}, scenario="clothing_type과 attrs.category가 다름")

# ---- J. 팀이 외부 상품을 같은 양식으로 입력 (90-95)
G = "J 팀 입력(manual)"
add(G, "top", "[예시몰] 크롭 슬림 니트 탑", "몸에 붙는 슬림 핏의 짧은 니트 탑. 부드러운 니트 소재.", "슬림", "크롭", [("acrylic", 70), ("nylon", 30)], {"has": ["fit:slim", "len:crop", "mat:nylon"], "added": ["mat:knit"]}, source="manual", scenario="팀 입력: 상의")
add(G, "pants", "[예시몰] 하이웨이스트 와이드 슬랙스", "하이웨이스트에 와이드 핏. 떨어지는 핏이 좋아요.", "오버(루즈)", "풀 길이", [("polyester", 70), ("rayon", 30)], {"has": ["fit:over"], "added": ["sil:wide", "waist:high"]}, source="manual", scenario="팀 입력: 바지")
add(G, "outer", "[예시몰] 오버사이즈 울 코트", "넉넉한 오버사이즈 롱코트.", "오버(루즈)", "롱", [("wool", 70), ("polyester", 30)], {"has": ["fit:over", "len:long", "mat:wool"], "added": []}, source="manual", scenario="팀 입력: 아우터")
add(G, "dress", "[예시몰] 플레어 미디 원피스", "허리선에서 퍼지는 플레어 실루엣의 미디 원피스.", "레귤러", "미디", [("polyester", 100)], {"added": ["sil:A"], "fit_stated": "regular"}, source="manual", scenario="팀 입력: 원피스")
add(G, "skirt", "[예시몰] 데님 미니 스커트", "데님 소재의 미니 스커트.", "슬림", "미니", [("cotton", 100)], {"has": ["fit:slim", "mat:cotton"], "added": ["mat:denim"]}, source="manual", scenario="팀 입력: 치마")
add(G, "top", "[예시몰] 단가라 티셔츠", "", "레귤러", "기본", "확인 불가", {"fit_stated": "regular", "added": [], "fibers": []}, source="manual", scenario="팀 입력: 설명도 혼용률도 없음")

# ---- K. 리뷰 일치도 (96-100)
G = "K 리뷰 일치도"
add(G, "top", "슬림 셔츠 (리뷰 많음, 거의 일치)", "", "슬림", "기본", [("cotton", 100)], {"has": ["fit:slim"], "agreement": 0.93}, review={"n": 12, "mismatch": 1}, scenario="12명 중 1명 다름")
add(G, "top", "슬림 셔츠 (리뷰 다수 불일치)", "", "슬림", "기본", [("cotton", 100)], {"has": ["fit:slim"], "agreement": 0.45}, review={"n": 8, "mismatch": 6}, scenario="8명 중 6명 다름")
add(G, "top", "슬림 셔츠 (리뷰 없음)", "", "슬림", "기본", [("cotton", 100)], {"has": ["fit:slim"], "agreement": 1.0}, scenario="리뷰 없음")
add(G, "top", "슬림 셔츠 (3명 모두 다름)", "", "슬림", "기본", [("cotton", 100)], {"has": ["fit:slim"], "agreement": 0.5}, review={"n": 3, "mismatch": 3}, scenario="3명 중 3명 다름")
add(G, "top", "잘못된 리뷰 수", "", "슬림", "기본", [("cotton", 100)], {"error": "review"}, review={"n": 3, "mismatch": 4}, scenario="다르다는 답이 전체보다 많음")

assert len(items) == 100, len(items)
out = Path(__file__).resolve().parent.parent / "samples" / "synthetic_sellers.json"
json.dump({"_note": "가상 판매자 등록 샘플 100개. 실제 상품이 아니다. 기대 결과(expect)는 설계 문서에서 내가 정한 것이라 로직 점검용이다.",
           "items": items}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("저장:", out, len(items))
