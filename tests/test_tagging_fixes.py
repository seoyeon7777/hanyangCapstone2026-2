"""새 상품 48개 시험(2026-10-12)에서 나온 오탐·누락 유형을 고정한다. 현재 data/ 규칙으로 돈다.
상품 원문은 쓰지 않고, 문제가 된 문장 형태만 짧게 다시 썼다."""
import pytest

import matching


@pytest.fixture(scope="module")
def rules():
    return matching.load_rules()


def tags(rules, name, desc=""):
    return matching.tag_item_full(name, desc, rules)


def test_coordination_list_is_not_material(rules):
    r = tags(rules, "브이넥 니트", "깔끔한 핏으로 데일리하게 착용하기 좋아요 스커트부터 슬랙스, 데님 detail")
    assert "mat:knit" in r["tags"] and "mat:denim" not in r["tags"]


def test_coordination_pants_is_not_silhouette(rules):
    r = tags(rules, "시보리 가디건", "적당히 얇은 원단이라 와이드팬츠,스커트 어디에나 골반 라인을 예쁘게 살려주는 핏")
    assert "sil:wide" not in r["tags"]


def test_partial_looseness_is_not_whole_garment_over(rules):
    r = tags(rules, "경량 패딩", "넉넉한 후드, 하이넥과 밑단, 소매끝 시보리 처리로 바람을 막아요")
    assert "fit:over" not in r["tags"]
    r = tags(rules, "[팔뚝널널핏/허리잘록] 슬림핏 긴팔티셔츠")
    assert "fit:over" not in r["tags"] and "fit:slim" in r["tags"]
    r = tags(rules, "후드 티셔츠", "팔뚝은 여유로워요")
    assert "fit:over" not in r["tags"]


def test_whole_garment_looseness_is_kept(rules):
    assert "fit:over" in tags(rules, "넉넉한 후드집업")["tags"]
    assert "fit:over" in tags(rules, "후드 오버핏 집업")["tags"]


def test_trim_fiber_is_not_main_fabric(rules):
    desc = "주 원단: 100% 리사이클 폴리에스터 플리스\n트림 부분: 100% 포스트컨슈머 리사이클 나일론 파유\n리사이클 나일론 소재의 여밈에는 스냅단추가 달려 있어요"
    assert "mat:nylon" not in tags(rules, "플리스 스냅 티", desc)["tags"]
    assert "mat:nylon" in tags(rules, "경량 패딩", "겉감 - 나일론 / 안감 - 폴리")["tags"]


def test_looks_slim_is_not_slim_fit(rules):
    assert "fit:slim" not in tags(rules, "가디건", "상체를 자연스럽게 커버하면서 슬림해보이는 핏의 가디건")["tags"]
    assert "fit:slim" not in tags(rules, "원피스", "여유 있는 핏이지만 핀턱 디테일이 슬림한 효과를 줘요")["tags"]
    assert "fit:slim" in tags(rules, "슬림핏 티셔츠")["tags"]


def test_slim_and_loose_together_holds_judgement(rules):
    r = tags(rules, "단추 유넥 슬림 루즈핏 긴팔 티셔츠")
    assert "fit:slim" not in r["tags"] and "fit:over" not in r["tags"]
    assert r["fit_conflict"] is True and r["fit_stated"] == "unknown"
    assert tags(rules, "루즈핏 티셔츠")["fit_conflict"] is False


def test_labeled_composition_without_percent(rules):
    r = tags(rules, "나그랑 맨투맨", "핏감 타이트 정사이즈 루즈 모델 피팅 free 소재 면 100 색상 멜란지, 연보라 사이즈 FREE")
    assert "mat:cotton" in r["tags"]
    assert r["materials"] and r["materials"][0]["share"] == 1.0
    r = tags(rules, "맨투맨", "소재 면 50 색상 블랙")      # 합이 100이 아니면 혼용률로 받지 않는다
    assert r["materials"] == []


def test_care_instruction_is_not_material(rules):
    r = tags(rules, "프릴 미니 원피스", "겉감: 폴리에스터 100% 니트 소재 특성상 미지근한 물에 울샴프로 손빨래 하여 주십시오.")
    assert "mat:knit" not in r["tags"]
