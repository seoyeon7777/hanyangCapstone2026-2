"""tagger_llm: API 없이 응답 파싱·검증·캐시만 확인한다 (가짜 호출 함수 사용)."""
import json

import tagger_llm as T

VOCAB = {"fit:slim": ["슬림"], "sil:wide": ["와이드"], "mat:wool": ["울"]}


def test_parse_plain_json():
    raw = '{"tags": [{"tag": "fit:slim", "evidence": "슬림핏"}]}'
    assert T.parse_response(raw) == [{"tag": "fit:slim", "evidence": "슬림핏"}]


def test_parse_code_fence_and_extra_text():
    raw = '```json\n{"tags": [{"tag": "sil:wide", "evidence": "와이드"}]}\n```'
    assert T.parse_response(raw)[0]["tag"] == "sil:wide"
    assert T.parse_response('결과입니다 {"tags": []} 끝') == []


def test_parse_garbage_returns_empty():
    assert T.parse_response("모르겠습니다") == []
    assert T.parse_response("") == []


def test_parse_string_tags():
    assert T.parse_response('{"tags": ["fit:slim"]}') == [{"tag": "fit:slim", "evidence": ""}]


def test_validate_drops_unknown_and_duplicates():
    entries = [{"tag": "fit:slim", "evidence": "슬림"}, {"tag": "fit:slim", "evidence": "슬림"},
               {"tag": "made:up", "evidence": "x"}]
    r = T.validate(entries, VOCAB, "슬림 팬츠")
    assert r["tags"] == ["fit:slim"]
    assert r["dropped"] == ["made:up"]


def test_validate_grounding():
    entries = [{"tag": "fit:slim", "evidence": "슬림 팬츠"}, {"tag": "sil:wide", "evidence": "와이드 실루엣"}]
    r = T.validate(entries, VOCAB, "슬림 팬츠")
    assert r["tags"] == ["fit:slim", "sil:wide"]
    assert r["grounded"] == ["fit:slim"]  # 와이드 근거는 입력에 없으므로 근거 없음


def test_prompt_contains_vocab_and_item():
    p = T.build_prompt("슬림 팬츠", "", "bottom", VOCAB)
    assert "fit:slim" in p and "슬림 팬츠" in p and "(없음)" in p


def test_cache_avoids_second_call():
    calls = []

    def fake(prompt):
        calls.append(prompt)
        return json.dumps({"tags": [{"tag": "fit:slim", "evidence": "슬림"}]})

    cache = {}
    a = T.tag_item_llm("슬림 팬츠", "", "bottom", VOCAB, fake, cache, "k")
    b = T.tag_item_llm("슬림 팬츠", "", "bottom", VOCAB, fake, cache, "k")
    assert a["tags"] == b["tags"] == ["fit:slim"]
    assert len(calls) == 1
