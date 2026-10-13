"""A + B + C 연결 테스트 (표준 라이브러리만).

실행: python test_integration.py
- C가 B(matching.py)의 계산을 그대로 쓰는지: 같은 입력이면 C의 체형 점수 = B의 body_fit
- A(body_profile.classify) 결과가 C의 body로 맞게 바뀌는지
- 코디 보정·추천 이유·재진단이 B의 표(data/*.json)를 따르는지
"""
import matching_b
from matching_b import from_profile, to_profile, match_score, outfit_bonus, RULES
import matching                       # B (matching_b가 경로를 맞춰 둠)
from body_profile import classify     # A
from feed import load_items, final_score, score_all, rank, outfits, reason, need_rediagnosis

ITEMS = {item["id"]: item for item in load_items()}
STRAIGHT = {"ratio": {"S": 1.0, "W": 0.0, "N": 0.0}, "kind": "single", "primary": "S"}
WAVE = {"ratio": {"S": 0.0, "W": 1.0, "N": 0.0}, "kind": "single", "primary": "W"}
MIXED = {"ratio": {"S": 0.45, "W": 0.15, "N": 0.40}, "kind": "mixed", "primary": "S"}
ANSWERS = {"q1": 1, "q2": 3, "q3": 1, "q4": 2, "q5": 1, "q6": 1, "q7": 3}


def close(a, b, tol=1e-3):
    return abs(a - b) < tol


def test_a_to_c_body():
    profile = classify(ANSWERS)
    body = from_profile(profile)
    assert body["primary"] == profile["primary"]
    assert body["kind"] == ("mixed" if profile["is_mixed"] else "single")
    assert body["ratio"] == profile["ratios"]
    assert to_profile(body) == {"ratios": profile["ratios"], "primary": profile["primary"],
                                "is_mixed": profile["is_mixed"]}


def test_c_body_score_equals_b():
    for body in (STRAIGHT, WAVE, MIXED):
        for item in ITEMS.values():
            b = matching.body_fit(item["tags"], to_profile(body), RULES)
            c = match_score(body, item)
            assert c["excluded"] == (b is None)
            if b is not None:
                assert close(c["score"], b, 1e-4)
                assert close(final_score(body, item)["body"], b, 1e-4)


def test_good_and_avoid_tags_follow_b_weights():
    # 칼라 셔츠 fit:slim(S +2), mat:cotton(S +1) → 스트레이트 어울림, 큰 순서
    assert match_score(STRAIGHT, ITEMS["t01"])["good_tags"] == ["fit:slim", "mat:cotton"]
    # 러플 블라우스 det:ruffle(S -1) → 스트레이트가 피해야 함
    assert match_score(STRAIGHT, ITEMS["t06"])["avoid_tags"] == ["det:ruffle"]
    # 웨이브에게 러플(W +2)과 시폰(W +1)은 어울림
    assert match_score(WAVE, ITEMS["t06"])["good_tags"] == ["det:ruffle", "mat:chiffon"]


def test_reason_uses_b_sentences():
    r = final_score(STRAIGHT, ITEMS["t01"])
    b = matching.reasons(ITEMS["t01"]["tags"], to_profile(STRAIGHT), RULES)
    assert b and r["reasons"] == b
    assert reason(r).startswith(b[0].rstrip(". "))


def test_outfit_bonus_follows_b_balance():
    # 웨이브: 크롭 상의(t04 len:crop) + 하이웨이스트 하의(b04 waist:high) → +0.10
    assert close(outfit_bonus(WAVE, ITEMS["t04"]["tags"], ITEMS["b04"]["tags"]), 0.10)
    # 웨이브: 오버핏 상의 + 오버핏 하의 규칙은 없음(하의에 fit:over 없음) → 크롭 아닌 상의는 0
    assert close(outfit_bonus(WAVE, ITEMS["t08"]["tags"], ITEMS["b04"]["tags"]), 0.0)
    # 스트레이트: 상·하 중 I/H 라인 → +0.10, 오버핏 상의 + 와이드 하의 → -0.15
    assert close(outfit_bonus(STRAIGHT, ITEMS["t01"]["tags"], ITEMS["b02"]["tags"]), 0.10)
    assert close(outfit_bonus(STRAIGHT, ITEMS["t08"]["tags"], ITEMS["b06"]["tags"]), -0.15)
    # C의 코디 결과에도 같은 보정이 들어감
    first = outfits(WAVE, list(ITEMS.values()))[0]
    assert (first["top"]["id"], first["bottom"]["id"]) == ("t04", "b04") and close(first["bonus"], 0.10)


def test_straight_ranking_makes_sense():
    top = rank(score_all(STRAIGHT, ITEMS.values()))
    tags = [t for r in top[:5] for t in r["tags"]]
    assert "sil:IH" in tags and "fit:slim" in tags
    order = [r["id"] for r in sorted(score_all(STRAIGHT, ITEMS.values()), key=lambda r: -r["score"])]
    assert order.index("t06") > len(order) // 2   # 러플 블라우스는 아래쪽 (B는 지금 제외 대신 감점 모드)


def test_rediagnosis_with_b():
    likes = lambda item, n: [{"item": item, "event": "like"}] * n
    assert need_rediagnosis(likes(ITEMS["t06"], 10), STRAIGHT) is True    # 러플만 찜
    assert need_rediagnosis(likes(ITEMS["t01"], 10), STRAIGHT) is False   # 칼라 셔츠만 찜


def test_b_config_is_penalty_mode():
    # B 설정이 바뀌면(제외 모드) C의 동작도 바뀌므로 여기서 알 수 있게 둔다
    assert RULES.config["avoid_mode"] == "penalty"
    assert not any(match_score(STRAIGHT, i)["excluded"] for i in ITEMS.values())


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_")]
    failed = 0
    for name, fn in tests:
        try:
            fn()
            print(f"  PASS  {name}")
        except AssertionError:
            failed += 1
            print(f"  FAIL  {name}")
        except Exception as e:
            failed += 1
            print(f"  ERROR {name}: {type(e).__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} 통과")
    raise SystemExit(1 if failed else 0)
