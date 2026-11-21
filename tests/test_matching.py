"""수식 구현 검증. PDF 시절 가중치·키워드·문장·설정을 tests/fixtures 에 고정해서 쓴다.
data/ 의 규칙 파일이 바뀌어도 이 테스트는 영향받지 않는다."""
from pathlib import Path

import pytest

import matching

FIXTURE_DIR = Path(__file__).parent / "fixtures"  # PDF 시절 규칙을 고정한 사본 (data/ 와 무관)
FIXTURE = FIXTURE_DIR / "pdf_weights.json"

SINGLE_S = {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False}
MIXED_SN = {"ratios": {"S": 0.45, "W": 0.10, "N": 0.45}, "primary": "S", "is_mixed": True}


@pytest.fixture
def rules():
    return matching.load_rules(data_dir=FIXTURE_DIR, weights_path=FIXTURE)


def test_range_from_matrix(rules):
    assert (rules.w_min, rules.w_max) == (-3, 2)


def test_collar_shirt_is_082(rules):  # PDF 6쪽
    tags = ["fit:slim", "mat:structured", "neck:collar", "det:vertical"]
    assert matching.body_fit(tags, SINGLE_S, rules) == pytest.approx(0.82, abs=0.01)


def test_ruffle_blouse_excluded_for_single_s(rules):  # PDF 6쪽
    assert matching.body_fit(["det:ruffle"], SINGLE_S, rules) is None


def test_mixed_is_not_excluded(rules):
    assert matching.body_fit(["det:ruffle"], MIXED_SN, rules) is not None


def test_penalty_mode_does_not_exclude(rules):
    cfg = {**rules.config, "avoid_mode": "penalty"}
    r = matching.Rules(rules.weights, rules.keywords, rules.reasons, cfg, rules.w_min, rules.w_max)
    assert matching.body_fit(["det:ruffle"], SINGLE_S, r) is not None


def test_unknown_tags_ignored_and_empty_is_neutral(rules):
    assert matching.body_fit(["nope:x"], SINGLE_S, rules) == matching.body_fit([], SINGLE_S, rules)
    assert matching.body_fit([], SINGLE_S, rules) == pytest.approx(0.6)


def test_score_stays_in_0_1(rules):
    for g in rules.weights:
        for main in matching.TYPES:
            prof = {"ratios": {t: float(t == main) for t in matching.TYPES}, "primary": main, "is_mixed": True}
            assert 0.0 <= matching.body_fit([g], prof, rules) <= 1.0


def test_tag_item_from_name(rules):
    assert matching.tag_item("러플 블라우스", "", rules) == ["det:ruffle"]
    assert matching.tag_item("크롭 드롭숄더 니트", "", rules) == ["len:crop", "det:drop"]


def test_reasons(rules):
    # 혼합 점수에 긍정 기여가 큰 순서. 칼라는 S에서 크게, 크롭은 W 비율(17%)만큼 작게 기여한다.
    out = matching.reasons(["neck:collar", "len:crop"], SINGLE_S, rules)
    assert out == ["칼라가 목선을 세워 짧은 목을 보완해요", "짧은 기장이 허리선을 올려 다리가 길어 보여요"]


def test_reasons_skip_tags_with_non_positive_combined_contribution(real_rules):
    # 오버핏: W·S에서는 -1, N에서만 +2. S 90%·N 10%면 합산 기여가 음수라 N 문장을 붙이지 않는다.
    prof = {"ratios": {"S": 0.9, "W": 0.0, "N": 0.1}, "primary": "S", "is_mixed": False}
    assert matching.reasons(["fit:over"], prof, real_rules) == []
    prof2 = {"ratios": {"S": 0.1, "W": 0.0, "N": 0.9}, "primary": "N", "is_mixed": False}
    assert len(matching.reasons(["fit:over"], prof2, real_rules)) == 1


def test_reasons_use_sentence_of_type_with_largest_contribution(real_rules):
    # 데님: S +2, N +2 문장이 서로 다르다. 비율이 큰 유형의 문장을 쓴다.
    s_side = {"ratios": {"S": 0.6, "W": 0.0, "N": 0.4}, "primary": "S", "is_mixed": True}
    n_side = {"ratios": {"S": 0.4, "W": 0.0, "N": 0.6}, "primary": "N", "is_mixed": True}
    a = matching.reasons(["mat:denim"], s_side, real_rules)
    b = matching.reasons(["mat:denim"], n_side, real_rules)
    assert a == [real_rules.reasons["mat:denim"]["S"]] and b == [real_rules.reasons["mat:denim"]["N"]]


def test_reasons_same_for_flag_values(real_rules):
    # 추천 이유는 is_mixed를 보지 않는다: 같은 비율이면 결과가 같다.
    tags = ["fit:slim", "mat:denim", "len:crop"]
    r = {"S": 0.5, "W": 0.1, "N": 0.4}
    a = matching.reasons(tags, {"ratios": r, "primary": "S", "is_mixed": True}, real_rules)
    b = matching.reasons(tags, {"ratios": r, "primary": "S", "is_mixed": False}, real_rules)
    assert a == b and len(a) <= real_rules.config["reason_max"]


def test_match_item_shape(rules):
    r = matching.match_item({"product_id": 2, "product_name": "러플 블라우스"}, SINGLE_S, rules)
    assert r == {"product_id": 2, "tags": ["det:ruffle"], "text_added": [], "review_agreement": 1.0, "fit_stated": "unknown", "body_fit": None, "excluded": True, "no_info": False, "reasons": []}


def test_current_rules_are_valid():
    assert matching.validate_rules(matching.load_rules()) == []


# ---- 점수 보정(shrink_k) / 현재 규칙(data/) 점검 ----

def _with_cfg(rules, **cfg):
    return matching.Rules(rules.weights, rules.keywords, rules.reasons, {**rules.config, **cfg}, rules.w_min, rules.w_max)


def test_shrink_pulls_few_tag_items_toward_neutral(rules):
    neutral = matching.body_fit([], SINGLE_S, rules)
    raw = matching.body_fit(["fit:slim"], SINGLE_S, _with_cfg(rules, shrink_k=0))
    shrunk = matching.body_fit(["fit:slim"], SINGLE_S, _with_cfg(rules, shrink_k=1))
    assert abs(shrunk - neutral) == pytest.approx(abs(raw - neutral) * 1 / 2)  # n=1, k=1 -> 절반


def test_shrink_fades_with_more_tags(rules):
    tags = ["fit:slim", "mat:structured", "neck:collar", "det:vertical"]
    raw = matching.body_fit(tags, SINGLE_S, _with_cfg(rules, shrink_k=0))
    shrunk = matching.body_fit(tags, SINGLE_S, _with_cfg(rules, shrink_k=1))
    neutral = matching.body_fit([], SINGLE_S, rules)
    assert abs(shrunk - neutral) == pytest.approx(abs(raw - neutral) * 4 / 5)


def test_live_rules_have_reasons_for_each_positive_cell():
    live = matching.load_rules()
    for g, row in live.weights.items():
        for t in matching.TYPES:
            if row[t] > 0:
                assert t in live.reasons.get(g, {}), f"{g}/{t}: 추천 이유 문장 없음"


def test_live_rules_every_tag_has_a_keyword():
    live = matching.load_rules()
    covered = {g for tags in live.keywords.values() for g in tags}
    assert set(live.weights) <= covered


def test_winter_is_not_wool():
    """'겨울'의 '울 ' 이 울(wool) 소재로 잡히지 않는다. 진짜 울은 그대로 잡는다."""
    rules = matching.load_rules()
    assert "mat:wool" not in matching.tag_item("가을겨울 팬츠", "", rules)
    assert "mat:wool" not in matching.tag_item("겨울 후리스맨투맨", "", rules)
    assert "mat:wool" in matching.tag_item("울 브이넥 니트", "", rules)


def test_new_paper_tags():
    r = matching.load_rules()
    assert "mat:leather" in matching.tag_item("가죽 자켓", "", r)
    assert "mat:nylon" in matching.tag_item("나일론 윈드브레이커", "", r)
    assert "det:ribbon" in matching.tag_item("리본 블라우스", "", r)
    assert "mat:fur" in matching.tag_item("페이크퍼 코트", "", r)
    assert "mat:fur" not in matching.tag_item("스트릿 점퍼", "", r)   # '퍼' 단독 키워드 금지(점퍼 오탐)


def test_ably_synonyms_and_leather_patch():
    r = matching.load_rules()
    assert "fit:over" in matching.tag_item("체형커버 여유핏 긴팔티", "", r)
    assert "mat:wool" in matching.tag_item("[울니트] 루즈핏 브이넥", "", r)
    assert "mat:leather" not in matching.tag_item("아방핏 레더패치 가디건", "", r)


def test_description_noise_is_ignored():
    r = matching.load_rules()
    t = matching.tag_item("코듀로이 쇼츠", "코튼 혼방입니다. 니트나 후드와 매치해 연출하기 좋습니다. 사이즈 064,067,070 WIDE", r)
    assert "mat:knit" not in t and "sil:wide" not in t and "mat:cotton" in t
    assert "len:long" in matching.tag_item("CUT-OUT LONG SKIRT", "", r)


def test_care_instructions_are_ignored():
    rules = matching.load_rules()
    desc = "혼용률\nCotton 56% Modal 37%\n관리법\n세탁기 사용 시 '울 코스' 또는 '섬세 코스'로 세탁하십시오.\n기계 건조기는 사용하지 마십시오."
    tags = matching.tag_item("레이스 슬리브리스", desc, rules)
    assert "mat:wool" not in tags
    assert "mat:cotton" in tags


def test_styling_info_of_other_products_is_ignored():
    rules = matching.load_rules()
    desc = ("FIT 슬림\nSTYLE INFO\n아우터 | 우먼즈 에센셜 가디건\n상의 | 멀티 스트라이프 칼라드 튜닉 니트 [버건디]\n"
            "액세서리 | 신세틱 레더 슬림 벨트\n교환 및 반품이 가능한 경우")
    tags = matching.tag_item("슬랙스", desc, rules)
    assert "mat:knit" not in tags and "mat:leather" not in tags
    assert "fit:slim" in tags


def test_vision_text_noise_cases():
    rules = matching.load_rules()
    # 주소의 '서울'이 울(wool)로, 색상명 DENIM이 데님 소재로, 네크라인의 '와이드'가 와이드 실루엣으로 잡히면 안 된다
    assert "mat:wool" not in matching.tag_item("쇼츠", "반품주소지\n[04793] 서울 성동구 아차산로5길", rules)
    assert "mat:denim" not in matching.tag_item("후드집업 (DENIM)", "색상 DENIM\n소재 면 69%, 폴리에스터 31%\nDenim\nDyeing Charcoal", rules)
    assert "sil:wide" not in matching.tag_item("티셔츠", "와이드한 폭의 보트 네크라인이 돋보이는 티셔츠입니다", rules)
    assert "sil:wide" not in matching.tag_item("집업", "데님부터 스커트와 와이드 팬츠까지 다양한 하의와 자연스럽게 어우러져", rules)
    assert "mat:denim" in matching.tag_item("데님 팬츠", "", rules)
    assert "sil:wide" in matching.tag_item("와이드 팬츠", "", rules)


def test_semi_over_is_not_over():
    rules = matching.load_rules()
    assert "fit:over" not in matching.tag_item("풀오버", "세미 오버핏 패턴으로 편안하게 떨어지는 실루엣 / (SEMI-OVER FIT)", rules)
    assert "fit:over" in matching.tag_item("오버핏 후드", "", rules)


def test_negated_fit_words_are_ignored():
    rules = matching.load_rules()
    assert "fit:slim" not in matching.tag_item("티셔츠", "몸에 붙는 슬림하지 않은 여유로운 실루엣", rules)
    assert "fit:over" not in matching.tag_item("티셔츠", "오버핏이 아닌 레귤러 핏", rules)
    assert "fit:slim" in matching.tag_item("슬림 티셔츠", "", rules)


def test_korean_cotton_notation():
    rules = matching.load_rules()
    for desc in ("FABRIC 면 60%, 레이온 40%", "소재 면 69%, 폴리에스터 31%", "면과 폴리에스터를 혼방한 원단", "면/폴리 혼방"):
        assert "mat:cotton" in matching.tag_item("티셔츠", desc, rules), desc
    for desc in ("표면 100% 재활용 포장", "이면 처리가 깔끔한 안감", "면접룩으로 좋은 셔츠"):
        assert "mat:cotton" not in matching.tag_item("셔츠", desc, rules), desc


# ---------------------------------------------------------------- 소재 태그 분리·혼용률

def test_material_tags_are_split_per_material():
    r = matching.load_rules()
    assert matching.tag_item("캐시미어 니트", "", r) == ["mat:cashmere", "mat:knit"] or set(matching.tag_item("캐시미어 니트", "", r)) == {"mat:cashmere", "mat:knit"}
    assert "mat:silk" not in matching.tag_item("캐시미어 코트", "", r)
    assert matching.tag_item("벨벳 원피스", "", r) == ["mat:velvet"]
    assert matching.tag_item("코듀로이 팬츠", "", r) == ["mat:corduroy"]
    assert matching.tag_item("무스탕 자켓", "", r) == ["mat:mustang"]
    assert matching.tag_item("린넨 셔츠", "", r) == ["mat:linen"]


def test_parse_composition_both_formats():
    r = matching.load_rules()
    got = {m["fiber"]: m["share"] for m in matching.parse_composition("소재: 면 60% 폴리에스터 40%", r)}
    assert got == {"cotton": 0.6, "polyester": 0.4}
    got = {m["fiber"]: m["share"] for m in matching.parse_composition("60% 면 40% 폴리에스터", r)}
    assert got == {"cotton": 0.6, "polyester": 0.4}
    assert matching.parse_composition("표면 100% 처리", r) == []


def test_composition_threshold_drops_small_fibers():
    r = matching.load_rules()
    full = matching.tag_item_full("케이블 니트", "울 6% 나일론 13% 아크릴 81%", r)
    assert "mat:wool" not in full["tags"] and "mat:nylon" not in full["tags"]
    assert {m["fiber"] for m in full["materials"]} == {"wool", "nylon", "acrylic"}     # 점수에는 안 써도 읽은 값은 남는다
    assert "mat:wool" in matching.tag_item("울 코트", "울 80% 폴리 20%", r)
    assert "mat:wool" in matching.tag_item("울 코트", "", r)                            # 혼용률이 없으면 키워드로 붙인다


def test_velvet_tape_trim_is_not_velvet_fabric():
    r = matching.load_rules()
    assert "mat:velvet" not in matching.tag_item("A라인 쉬폰 원피스", "네크 라인에 벨벳 테이프 디테일을 더한 원피스", r)


def test_lining_does_not_decide_material_tags():
    r = matching.load_rules()
    got = matching.tag_item_full("롱 원피스", "소재 겉감 폴리에스터100%, 안감 나일론100%", r)
    assert "mat:nylon" not in got["tags"] and got["material_basis"] == "main"
    assert {(m["fiber"], m["part"]) for m in got["materials"]} == {("polyester", "main"), ("nylon", "sub")}
    got = matching.tag_item_full("점퍼", "FABRIC -OUTER FABRIC 1 : COTTON 70% NYLON 30% -LINING: NYLON 100%", r)
    assert set(got["tags"]) == {"mat:cotton", "mat:nylon"}
    got = matching.tag_item_full("티셔츠", "MATERIAL 몸판 : 면100% 배색1 : 울 80% 폴리 20%", r)
    assert "mat:wool" not in got["tags"] and "mat:cotton" in got["tags"]


def test_only_lining_known_means_unclear():
    r = matching.load_rules()
    got = matching.tag_item_full("코트", "안감 나일론 100%", r)
    assert got["tags"] == [] and got["material_basis"] == "unclear"


def test_composition_in_unpunctuated_blob_survives_styling_filter():
    r = matching.load_rules()
    blob = "크롭 탑 및 슬림핏 상의와 매치해 스타일을 강조하기 좋은 아이템COMPOSITION 혼용률POLY 62% / COTTON 34% / SPAN 9%COLORBLACK"
    got = matching.tag_item_full("데님 쇼츠", blob, r)
    assert {m["fiber"] for m in got["materials"]} == {"polyester", "cotton", "spandex"}
    assert "mat:cotton" in got["tags"] and "fit:slim" not in got["tags"]      # 코디 문장의 슬림핏은 여전히 무시한다


# ---------------------------------------------------------------- 판매자 등록 양식

def test_attributes_form_is_consistent_with_rules():
    assert matching.validate_rules(matching.load_rules()) == []








def test_item_type_tags_from_paper_items():
    r = matching.load_rules()
    assert matching.tag_item("테일러드 재킷 블랙", "", r) == ["item:tailored_jacket"]
    assert "item:tailored_jacket" not in matching.tag_item("테일러드 팬츠", "", r)          # 팬츠는 재킷이 아니다
    assert set(matching.tag_item("모헤어 니트", "", r)) == {"mat:mohair", "mat:knit"}


def test_new_item_tags_favor_their_type():
    r = matching.load_rules()
    def p(t):
        return {"ratios": {k: 1.0 if k == t else 0.0 for k in "SWN"}, "primary": t, "is_mixed": False}
    assert matching.body_fit(["item:tailored_jacket"], p("S"), r) > matching.body_fit(["item:tailored_jacket"], p("W"), r)
    assert matching.body_fit(["mat:mohair"], p("W"), r) > matching.body_fit(["mat:mohair"], p("N"), r)


# ---------------------------------------------------------------- 판매자 등록 양식 검증 (직접 등록 상품 전용)

import pytest  # noqa: E402

BASE = {"category": "top", "fit": "슬림", "length_top": "크롭",
        "composition": [{"fiber": "cotton", "share": 0.6}, {"fiber": "polyester", "share": 0.4}]}


def test_form_tags_and_stored_values():
    r = matching.load_rules()
    got = matching.tag_from_attributes({**BASE, "details": ["리본"], "fabrics": ["니트"]}, r)
    assert set(got["tags"]) == {"fit:slim", "len:crop", "det:ribbon", "mat:knit", "mat:cotton"}
    assert got["attributes"]["fit"] == "슬림" and got["attributes"]["composition"]


def test_regular_is_stored_but_not_scored():
    r = matching.load_rules()
    got = matching.tag_from_attributes({"category": "top", "fit": "레귤러", "length_top": "기본",
                                        "composition": [{"fiber": "polyester", "share": 1.0}]}, r)
    assert got["tags"] == []                                  # 점수용 태그 없음: 필수 입력만으로 태그가 붙는다는 보장은 없다
    assert got["attributes"]["fit"] == "레귤러"               # 값은 저장된다 (모름과 다르다)


def test_length_options_depend_on_category():
    r = matching.load_rules()
    comp = [{"fiber": "cotton", "share": 1.0}]
    ok = matching.tag_from_attributes({"category": "skirt", "fit": "레귤러", "length_skirt": "맥시", "composition": comp}, r)
    assert ok["tags"] == ["len:long", "mat:cotton"] or set(ok["tags"]) == {"len:long", "mat:cotton"}
    with pytest.raises(ValueError):                           # 상의에 스커트 기장 항목
        matching.tag_from_attributes({"category": "top", "fit": "레귤러", "length_top": "기본", "length_skirt": "맥시", "composition": comp}, r)
    pants = matching.tag_from_attributes({"category": "pants", "fit": "레귤러", "length_pants": "발목 길이", "composition": comp}, r)
    assert "len:crop" not in pants["tags"] and pants["attributes"]["length_pants"] == "발목 길이"   # 팬츠 길이는 근거가 없어 저장만


def test_item_type_limited_by_category():
    r = matching.load_rules()
    comp = [{"fiber": "cotton", "share": 1.0}]
    with pytest.raises(ValueError):
        matching.tag_from_attributes({"category": "top", "fit": "레귤러", "length_top": "기본", "item_type": "크롭 팬츠", "composition": comp}, r)
    got = matching.tag_from_attributes({"category": "pants", "fit": "레귤러", "length_pants": "발목 길이", "item_type": "크롭 팬츠", "composition": comp}, r)
    assert "len:crop" in got["tags"]


@pytest.mark.parametrize("bad", [
    {"category": "top", "fit": "슬림", "composition": [{"fiber": "cotton", "share": 1.0}]},                       # 기장 누락
    {**BASE, "fit": "타이트"},                                                                                    # 없는 선택지
    {**BASE, "composition": [{"fiber": "cotton", "share": 0.6}, {"fiber": "cotton", "share": 0.4}]},              # 섬유 중복
    {**BASE, "composition": [{"fiber": "cotton", "share": 0.6}, {"fiber": "polyester", "share": 0.3}]},           # 합계 0.9
    {**BASE, "composition": [{"fiber": "cotton", "share": 60}, {"fiber": "polyester", "share": 40}]},             # 0~1 범위 밖
    {**BASE, "composition": [{"fiber": "leather", "share": 1.0}]},                                                # 모르는 섬유
    {"category": "top", "fit": "슬림", "length_top": "크롭"},                                                      # 혼용률도 상태도 없음
    {**BASE, "composition_status": "확인 불가"},                                                                   # 수치와 상태 동시
    {**BASE, "category": "bag"},
])
def test_form_rejects_invalid_input(bad):
    with pytest.raises(ValueError):
        matching.tag_from_attributes(bad, matching.load_rules())


def test_composition_status_allows_leather_without_fiber_numbers():
    r = matching.load_rules()
    got = matching.tag_from_attributes({"category": "outer", "fit": "오버(루즈)", "length_top": "기본",
                                        "fabrics": ["가죽"], "composition_status": "해당 없음"}, r)
    assert set(got["tags"]) == {"fit:over", "mat:leather"} and got["materials"] == []
    assert got["attributes"]["composition_status"] == "해당 없음"


def test_match_item_attrs_only_for_direct_items():
    r = matching.load_rules()
    profile = {"ratios": {"S": 0.0, "W": 0.0, "N": 1.0}, "primary": "N", "is_mixed": False}
    attrs = {"category": "top", "fit": "오버(루즈)", "length_top": "기본", "composition": [{"fiber": "cotton", "share": 1.0}]}
    out = matching.match_item({"product_id": 1, "product_name": "슬림 셔츠", "source": "direct", "attrs": attrs}, profile, r)
    assert set(out["tags"]) == {"fit:over", "mat:cotton"}                 # 이름의 '슬림'은 읽지 않는다
    with pytest.raises(ValueError):                                       # 가져온 상품에 attrs가 오면 조용히 쓰지 않고 오류
        matching.match_item({"product_id": 2, "product_name": "슬림 셔츠", "attrs": attrs}, profile, r)
    with pytest.raises(ValueError):                                       # 직접 등록 상품인데 필수 항목이 비면 오류
        matching.match_item({"product_id": 3, "product_name": "x", "source": "direct", "attrs": {"category": "top", "fit": "슬림"}}, profile, r)
    imported = matching.match_item({"product_id": 4, "product_name": "슬림 셔츠 면 100%"}, profile, r)
    assert "fit:slim" in imported["tags"]


def test_contradicting_length_and_item_type_rejected():
    r = matching.load_rules()
    comp = [{"fiber": "cotton", "share": 1.0}]
    with pytest.raises(ValueError):       # 기장 '기본' + 롱 카디건
        matching.tag_from_attributes({"category": "top", "fit": "레귤러", "length_top": "기본", "item_type": "롱 카디건", "composition": comp}, r)
    with pytest.raises(ValueError):       # 팬츠 '풀 길이' + 크롭 팬츠
        matching.tag_from_attributes({"category": "pants", "fit": "레귤러", "length_pants": "풀 길이", "item_type": "크롭 팬츠", "composition": comp}, r)
    ok = matching.tag_from_attributes({"category": "top", "fit": "레귤러", "length_top": "롱", "item_type": "롱 카디건", "composition": comp}, r)
    assert ok["tags"].count("len:long") == 1


def test_percent_input_converted_and_checked():
    r = matching.load_rules()
    comp = matching.percent_to_share([{"fiber": "cotton", "percent": 60}, {"fiber": "polyester", "percent": 40}])
    assert [m["share"] for m in comp] == [0.6, 0.4]
    base = {"category": "top", "fit": "슬림", "length_top": "기본"}
    assert "mat:cotton" in matching.tag_from_attributes({**base, "composition": comp}, r)["tags"]
    with pytest.raises(ValueError):
        matching.percent_to_share([{"fiber": "cotton", "percent": 0.6}, {"fiber": "polyester", "percent": 140}])
    with pytest.raises(ValueError):       # 퍼센트 합계가 100이 아니다 (서버 검증)
        matching.tag_from_attributes({**base, "composition": matching.percent_to_share([{"fiber": "cotton", "percent": 60}, {"fiber": "polyester", "percent": 30}])}, r)


@pytest.fixture
def real_rules():
    return matching.load_rules()      # 실제 data/ (픽스처에는 소재 키워드가 없다)


def test_care_label_legend_is_not_a_fit_cue(real_rules):   # 케어라벨 범례표 '핏 | 슬림핏 | 기본핏 | 루즈핏/오버핏 | 와이드핏'
    legend = "정석 레귤러 핏\n세탁방법 | 드라이클리닝\n핏 | 슬림핏 | 기본핏 | 루즈핏/오버핏 | 와이드핏\n신축성 | 없음"
    assert matching.tag_item("후드 티", legend, real_rules) == []
    vertical = "핏\n슬림핏\n기본핏\n루즈핏/오버핏\n와이드핏\n신축성"
    assert matching.tag_item("후드 티", vertical, real_rules) == []
    assert "fit:slim" in matching.tag_item("티", "슬림핏으로 몸에 붙는 실루엣", real_rules)   # 실제 설명은 그대로 읽는다


def test_return_policy_and_manufacture_date_are_not_tags(real_rules):
    assert "mat:leather" not in matching.tag_item("팬츠", "천연가죽 제품의 경우 미세한 스크레치가 있을 수 있습니다", real_rules)
    assert "sil:IH" not in matching.tag_item("자켓", "제조일자: 2026년 06월", real_rules)
    assert "fit:over" not in matching.tag_item("티", "넉넉한 넥라인", real_rules)


def test_leather_patch_sentence_is_not_leather_material(real_rules):
    assert "mat:leather" not in matching.tag_item("팬츠", "크랙 가공된 소가죽을 사용하여 조화로운 가죽패치 사용", real_rules)
    assert "mat:leather" in matching.tag_item("자켓", "부드러운 소가죽 소재의 라이더 자켓", real_rules)


def test_notice_sentence_narrowed_to_return_context(real_rules):
    # 반품·하자 문맥이 있을 때만 '제품의 경우' 문장을 뺀다. 실제 상품 설명은 그대로 읽는다.
    assert "mat:leather" not in matching.tag_item("팬츠", "천연가죽 제품의 경우 미세한 스크레치, 주름이 있을 수 있습니다", real_rules)
    assert "mat:leather" in matching.tag_item("자켓", "이 제품의 경우 부드러운 가죽 소재를 사용했습니다", real_rules)


def test_fit_stated_separates_regular_from_unknown(real_rules):
    full = lambda d: matching.tag_item_full("티", d, real_rules)
    assert full("정석 레귤러 핏")["fit_stated"] == "regular" and full("정석 레귤러 핏")["tags"] == []   # 점수 태그는 만들지 않는다
    assert full("몸에 붙는 슬림핏")["fit_stated"] == "slim"
    assert full("넉넉한 오버핏")["fit_stated"] == "over"
    assert full("면 100%")["fit_stated"] == "unknown"                  # 정보 없음
    assert full("레귤러 핏이 아닌 오버핏")["fit_stated"] == "over"        # 부정된 레귤러는 세지 않는다
    assert full("슬림핏 같은 오버핏")["fit_stated"] == "unknown"            # 모순
    assert full("레귤러 사이즈 안내")["fit_stated"] == "unknown"           # 핏이 아닌 문맥의 레귤러
    assert full("세미 오버핏 느낌의 레귤러 핏")["fit_stated"] == "regular"
    assert full("핏 | 슬림핏 | 기본핏 | 루즈핏/오버핏 | 와이드핏")["fit_stated"] == "unknown"   # 범례표


def test_fit_stated_from_form_and_manual_source(real_rules):
    base = {"category": "top", "length_top": "기본", "composition_status": "확인 불가"}
    for opt, want in (("슬림", "slim"), ("레귤러", "regular"), ("오버(루즈)", "over")):
        assert matching.tag_from_attributes({**base, "fit": opt}, real_rules)["fit_stated"] == want
    item = {"product_id": 9, "product_name": "x", "source": "manual", "attrs": {**base, "fit": "레귤러"}}
    out = matching.match_item(item, SINGLE_S, real_rules)
    assert out["fit_stated"] == "regular" and out["tags"] == []
    with pytest.raises(ValueError):       # 양식 입력이 불완전하면 manual이어도 오류
        matching.match_item({"product_id": 10, "product_name": "x", "source": "manual", "attrs": {"category": "top"}}, SINGLE_S, real_rules)
    with pytest.raises(ValueError):       # 가져온 상품(source 없음)에는 attrs를 쓸 수 없다
        matching.match_item({"product_id": 11, "product_name": "x", "attrs": {**base, "fit": "슬림"}}, SINGLE_S, real_rules)


def test_recommend_wrapper(real_rules):
    base = {"length_top": "기본", "composition_status": "확인 불가"}
    items = [
        {"product_id": 1, "clothing_type": "top", "product_name": "셔츠", "source": "manual", "attrs": {"category": "top", "fit": "슬림", **base}},
        {"product_id": 2, "clothing_type": "top", "product_name": "티셔츠", "description": "넉넉한 오버핏"},
        {"product_id": 3, "clothing_type": "pants", "product_name": "일자 슬랙스", "tags": ["sil:IH"]},
    ]
    out = matching.recommend(SINGLE_S, items, real_rules, top_n=2)
    assert {m["product_id"] for m in out["items"]} == {1, 2, 3}
    assert out["outfits"][0]["top"] == 1 and out["outfits"][0]["bottom"] == 3     # 슬림 + 일자가 상위
    assert out["fit_unknown"] == [3]                                             # 핏 표기를 못 읽은 상품만
    with pytest.raises(ValueError):       # category 불일치
        matching.recommend(SINGLE_S, [{"product_id": 4, "clothing_type": "pants", "product_name": "x", "source": "manual", "attrs": {"category": "top", "fit": "슬림", **base}}], real_rules)


def test_form_item_fills_empty_optional_fields_from_text(real_rules):
    base = {"category": "pants", "fit": "슬림", "length_pants": "풀 길이", "composition": [{"fiber": "cotton", "share": 1.0}]}
    item = {"product_id": 1, "product_name": "와이드 데님 팬츠", "description": "하이웨이스트 핏. 폴리 100%", "source": "direct", "attrs": base}
    out = matching.match_item(item, SINGLE_S, real_rules)
    assert set(out["text_added"]) == {"sil:wide", "waist:high", "mat:denim"}          # 비운 선택 항목은 글에서 보충
    assert "fit:slim" in out["tags"] and "mat:cotton" in out["tags"]                   # 필수 항목은 양식 값
    assert "mat:polyester" not in out["tags"] and not any(g == "mat:cotton" for g in out["text_added"])
    # 양식에서 채운 항목은 글과 충돌해도 양식이 이긴다
    filled = {**base, "silhouette": "일자(스트레이트)", "fabrics": ["니트"]}
    out2 = matching.match_item({**item, "attrs": filled}, SINGLE_S, real_rules)
    assert "sil:wide" not in out2["tags"] and "mat:denim" not in out2["tags"] and "sil:IH" in out2["tags"]
    assert out2["text_added"] == ["waist:high"]
    # 글에 오버핏이라 써 있어도 양식의 핏(레귤러)이 이긴다
    out3 = matching.match_item({**item, "description": "오버핏", "attrs": {**base, "fit": "레귤러"}}, SINGLE_S, real_rules)
    assert "fit:over" not in out3["tags"] and out3["fit_stated"] == "regular"


def test_explicit_none_choice_is_not_overridden_by_text(real_rules):
    base = {"category": "top", "fit": "슬림", "length_top": "기본", "composition_status": "확인 불가"}
    item = {"product_id": 1, "product_name": "러플 데님 셔츠", "description": "러플 디테일, 데님 소재", "source": "direct"}
    blank = matching.match_item({**item, "attrs": base}, SINGLE_S, real_rules)
    assert set(blank["text_added"]) == {"det:ruffle", "mat:denim"}                        # 미입력은 보충
    none = matching.match_item({**item, "attrs": {**base, "details": ["해당 없음"], "fabrics": ["해당 없음"]}}, SINGLE_S, real_rules)
    assert none["text_added"] == [] and "det:ruffle" not in none["tags"]                 # '해당 없음' 선택은 유지
    with pytest.raises(ValueError):
        matching.tag_from_attributes({**base, "details": ["해당 없음", "리본"]}, real_rules)


def test_text_fill_cannot_bypass_required_fields(real_rules):
    base = {"category": "top", "fit": "슬림", "length_top": "기본", "composition_status": "확인 불가"}
    item = {"product_id": 1, "product_name": "롱 카디건 모헤어 니트", "description": "소재 면 100%", "source": "direct"}
    out = matching.match_item({**item, "attrs": base}, SINGLE_S, real_rules)
    assert "len:long" not in out["tags"]                      # 기장은 필수 입력(기본)이 이긴다
    assert "mat:cotton" not in out["tags"]                    # 혼용률은 필수 입력(확인 불가)이 이긴다
    none_type = matching.match_item({**item, "attrs": {**base, "item_type": "해당 없음"}}, SINGLE_S, real_rules)
    assert "mat:mohair" not in none_type["tags"]              # 종류에서 '해당 없음'을 골랐으면 모헤어 태그도 붙이지 않는다
    blank_type = matching.match_item({**item, "attrs": base}, SINGLE_S, real_rules)
    assert "mat:mohair" in blank_type["text_added"]           # 종류 미입력이면 보충


def test_review_agreement_is_off_by_default_and_modes(real_rules):
    tags = ["fit:slim", "sil:IH"]
    assert matching.review_agreement(None, real_rules) == 1.0
    assert matching.review_agreement({"n": 10, "mismatch": 0}, real_rules) == 1.0
    assert matching.review_agreement({"n": 1, "mismatch": 1}, real_rules) == pytest.approx(0.75)
    assert matching.review_agreement({"n": 3, "mismatch": 3}, real_rules) == pytest.approx(0.5)
    for bad in ({"n": 3, "mismatch": 4}, {"n": -1, "mismatch": 0}, {"n": 2.5, "mismatch": 1}):
        with pytest.raises(ValueError):
            matching.review_agreement(bad, real_rules)
    item = {"product_id": 1, "product_name": "x", "tags": tags, "review": {"n": 10, "mismatch": 10}}
    plain = matching.match_item({**item, "review": None}, SINGLE_S, real_rules)["body_fit"]
    off = matching.match_item(item, SINGLE_S, real_rules)
    assert off["body_fit"] == plain and off["review_agreement"] == round(3 / 13, 4)       # 기본: 표시만
    neutral = matching.body_fit([], SINGLE_S, real_rules)
    on = lambda mode: matching.Rules(**{**real_rules.__dict__, "config": {**real_rules.config, "review_mode": mode}})
    both = matching.match_item(item, SINGLE_S, on("both"))["body_fit"]
    assert plain > both > neutral
    bad_tags = ["fit:over", "sil:IH"]       # 직선형에게 안 맞는 상품: 낮은 점수
    bad = {"product_id": 2, "product_name": "y", "tags": ["fit:over"], "review": {"n": 10, "mismatch": 10}}
    low = matching.body_fit(["fit:over"], SINGLE_S, real_rules)
    assert low < neutral
    assert matching.match_item(bad, SINGLE_S, on("both"))["body_fit"] > low                # 양방향: 낮은 점수가 올라간다
    assert matching.match_item(bad, SINGLE_S, on("positive_only"))["body_fit"] == low      # 긍정만: 낮은 점수는 그대로


def test_text_fill_can_be_turned_off(real_rules):
    base = {"category": "pants", "fit": "슬림", "length_pants": "풀 길이", "composition_status": "확인 불가"}
    item = {"product_id": 1, "product_name": "와이드 데님 팬츠", "source": "manual", "attrs": base}      # desc는 비어 있어도 상품명에서 읽는다
    assert set(matching.match_item(item, SINGLE_S, real_rules)["text_added"]) == {"sil:wide", "mat:denim"}
    off = matching.match_item({**item, "text_fill": False}, SINGLE_S, real_rules)
    assert off["text_added"] == [] and off["tags"] == ["fit:slim"]


def _matched(real_rules, profile, tag_lists):
    return [matching.match_item({"product_id": i, "product_name": "x", "tags": tags}, profile, real_rules) for i, tags in enumerate(tag_lists, 1)]


def test_rank_items_single_ignores_strategy_and_mixed_blend_is_default(real_rules):
    tags = [["fit:slim"], ["fit:over"], [], ["fit:slim", "sil:IH"]]
    m = _matched(real_rules, SINGLE_S, tags)
    by_blend = matching.rank_items(m, SINGLE_S, real_rules, 4, "blend")
    assert [r["product_id"] for r in by_blend] == [4, 1, 2]                                      # 점수순 (태그도 핏도 없는 3번은 정보 없음이라 빠진다)
    assert matching.rank_items(m, SINGLE_S, real_rules, 4, "quota") == by_blend               # 단일형은 방식과 무관
    mixed = {"ratios": {"S": 0.5, "W": 0.0, "N": 0.5}, "primary": "S", "is_mixed": True}
    mm = _matched(real_rules, mixed, tags)
    assert matching.rank_items(mm, mixed, real_rules, 4) == matching.rank_items(mm, mixed, real_rules, 4, "blend")     # 기본은 blend


def test_rank_items_quota_splits_slots_by_ratio_and_safe_avoids_penalized(real_rules):
    mixed = {"ratios": {"S": 0.6, "W": 0.0, "N": 0.4}, "primary": "S", "is_mixed": True}
    s_good = [["fit:slim", "sil:IH"], ["fit:slim"], ["sil:IH"], ["det:ribbon", "fit:slim"], ["fit:slim", "mat:silk"], ["mat:cashmere", "fit:slim"]]
    n_good = [["fit:over", "sil:wide"], ["fit:over"], ["sil:wide"], ["mat:linen"], ["mat:corduroy"]]
    m = _matched(real_rules, mixed, s_good + n_good)
    out = matching.rank_items(m, mixed, real_rules, 10, "quota")
    picked = [r["picked_for"] for r in out]
    assert picked.count("S") == 6 and picked.count("N") == 4                                   # 10자리를 6:4로
    assert len({r["product_id"] for r in out}) == 10                                              # 한 상품은 한 번만
    assert picked[:2] == ["S", "N"] or picked[:2] == ["S", "S"]                                # 많은 쪽부터, 번갈아
    # 한쪽에는 좋고 다른 쪽에는 안 맞는 상품은 quota에는 들어가지만 quota_safe에서는 뒤로 밀린다
    trap = _matched(real_rules, mixed, [["fit:slim", "det:ruffle"], ["fit:slim", "sil:IH"], ["fit:over", "sil:wide"], ["mat:linen"], ["mat:cotton"]])
    plain = [r["product_id"] for r in matching.rank_items(trap, mixed, real_rules, 2, "quota")]
    safe = [r["product_id"] for r in matching.rank_items(trap, mixed, real_rules, 2, "quota_safe")]
    assert len(plain) == len(safe) == 2
    neutral = matching.body_fit([], SINGLE_S, real_rules)
    pure = lambda t, i: matching.body_fit(trap[i - 1]["tags"], matching._pure_profile(t), real_rules)
    assert all(pure("S", i) >= neutral - 1e-9 and pure("N", i) >= neutral - 1e-9 for i in safe)       # quota_safe는 어느 쪽에서도 감점인 상품을 뽑지 않는다
    assert any(pure("N", i) < neutral for i in plain)                                                  # 반면 quota는 N에 안 맞는 상품을 뽑았다
    only_one_safe = matching.rank_items(trap[:4], mixed, real_rules, 2, "quota_safe")                  # 안전한 후보가 하나뿐이면 나머지는 quota 방식으로 채운다
    assert len(only_one_safe) == 2
    with pytest.raises(ValueError):
        matching.rank_items(trap, mixed, real_rules, 2, "nope")


def test_recommend_returns_ranking_per_category(real_rules):
    items = [{"product_id": 1, "clothing_type": "top", "product_name": "a", "tags": ["fit:slim"]},
             {"product_id": 2, "clothing_type": "top", "product_name": "b", "tags": ["fit:over"]},
             {"product_id": 3, "clothing_type": "pants", "product_name": "c", "tags": ["sil:IH"]},
             {"product_id": 4, "clothing_type": "outer", "product_name": "d", "tags": []}]
    out = matching.recommend(SINGLE_S, items, real_rules, list_size=5)
    assert [r["product_id"] for r in out["ranking"]["top"]] == [1, 2]
    assert [r["product_id"] for r in out["ranking"]["bottom"]] == [3] and [r["product_id"] for r in out["ranking"]["outer"]] == [] and out["no_info"] == [4]       # 태그가 없는 4번은 정보 없음으로 제외


# ---- 코디 밸런스 규칙 적용 범위 (config.balance_scope)
def _balance_rules(scope):
    rules = matching.load_rules()
    rules.config["balance_scope"] = scope
    return rules


def _prof(s, w, n, mixed):
    r = {"S": s, "W": w, "N": n}
    order = sorted(r, key=lambda k: -r[k])
    return {"ratios": r, "primary": order[0], "secondary": order[1], "is_mixed": mixed}


def test_balance_scope_default_is_ratio():
    assert matching.load_rules().config["balance_scope"] == "ratio"


def test_balance_scope_flag_keeps_previous_behavior():
    rules = _balance_rules("flag")
    delta, applied = matching.balance_adjustment(["fit:over"], ["sil:wide"], _prof(0.61, 0.17, 0.22, False), rules)
    assert abs(delta - (-0.15)) < 1e-9 and [a["type"] for a in applied] == ["S"]  # 단일형은 1위 규칙만 100%


def test_balance_scope_ratio_uses_all_three_types_weighted():
    rules = _balance_rules("ratio")
    # 오버핏 상의 + 와이드 하의: S는 -0.15, N은 +0.10, W는 해당 규칙 없음
    delta, applied = matching.balance_adjustment(["fit:over"], ["sil:wide"], _prof(0.61, 0.17, 0.22, False), rules)
    assert abs(delta - (0.61 * -0.15 + 0.22 * 0.10)) < 1e-9
    assert {a["type"] for a in applied} == {"S", "N"}


def test_balance_scope_ratio_has_no_jump_at_mixed_boundary():
    """같은 비율이면 is_mixed 값과 상관없이 같은 보정값이 나온다 (flag에서는 달라진다)."""
    a, b = _prof(0.52, 0.10, 0.38, True), _prof(0.52, 0.10, 0.38, False)
    ratio, flag = _balance_rules("ratio"), _balance_rules("flag")
    da, _ = matching.balance_adjustment(["fit:over"], ["sil:wide"], a, ratio)
    db, _ = matching.balance_adjustment(["fit:over"], ["sil:wide"], b, ratio)
    assert abs(da - db) < 1e-9
    fa, _ = matching.balance_adjustment(["fit:over"], ["sil:wide"], a, flag)
    fb, _ = matching.balance_adjustment(["fit:over"], ["sil:wide"], b, flag)
    assert abs(fa - fb) > 0.05  # flag는 경계를 넘으면 값이 끊긴다


def test_balance_scope_rejects_unknown_value():
    import pytest
    rules = _balance_rules("all")
    with pytest.raises(ValueError):
        matching.balance_adjustment(["fit:over"], ["sil:wide"], _prof(0.5, 0.1, 0.4, True), rules)
