# hanyangCapstone2026-2

골격(스트레이트/웨이브/내추럴) 진단 기반 의류 추천 알고리즘

## 개요
- 사진 선택형 설문과 아바타 신체 비율로 골격 유형 분류
- 분류 결과로 맞춤 홈 피드 추천

## 구성
| 파트 | 내용 | 파일 |
|---|---|---|
| A (1) | 체형 분류 엔진 | `body_profile.py` (`classify`), `try_classify.py`, `evaluate.py` |
| B (2) | 옷 매칭: 태그·체형 적합도·추천 이유·코디 보정 | `matching.py`, `server.py`, `data/` |
| C (3) | 추천·홈 피드: 최종 점수·순위·홈 화면·무한 스크롤·학습 | `recommend/` |

흐름: 설문·치수 → **A** 골격 비율 → **B** 옷마다 적합도 → **C** 홈 피드 순서·코디·이유

## 실행
- Python 3.12, `pip install -r requirements.txt`
- 통합 서버: `python app.py` → http://127.0.0.1:5000/feed-demo (연습 화면)
- 안드로이드 앱 연결: 에뮬레이터 `http://10.0.2.2:5000`, 실제 폰 `http://<PC IP>:5000` — `recommend/안드로이드_연결가이드.md`
- 설문으로 바로 추천: `POST /api/feed` `{"user_id": "u1", "answers": {"q1": 1, ..., "q7": 3}}`
- B만 따로: `python server.py` / A만 따로: `python try_classify.py`

## 파트 3: 추천·홈 피드 (`recommend/`)
체형 분류 결과(스트레이트·웨이브·내추럴 비율)를 받아 홈 화면에 보여 줄 상품 순서, 코디, 추천 이유를 만들어요.

| 기능 | 설명 |
|---|---|
| 최종 점수 | 체형 적합도 60% + 취향 25% + 시즌·인기 15% |
| 순위·다양성 | 같은 종류 연속 2개, 같은 스타일 10개 중 3개까지 |
| 홈 화면 | 오늘의 추천 + 상의·하의·아우터·원피스 + 추천 코디 |
| 무한 스크롤 | `GET /api/feed/scroll?page=0&size=10` |
| 홈 화면 토글 | ON `mode=body`: 골격 맞춤 / OFF `mode=general`: 인기·자주 본 스타일 (체형 없이도 됨) |
| 학습 | 클릭·찜·구매·반품으로 취향 반영, 진단과 어긋나면 재진단 안내 |

- 테스트: `cd recommend && python test_feed.py` / `python test_integration.py` / `python test_api.py`
- 자세한 명세: `CLAUDE.md`, 쉬운 설명: `recommend/C파트_쉬운설명.md`, 화면 연결: `recommend/홈화면_연결가이드.md`
- 체형 적합도·추천 이유·코디 보정은 B(`matching.py`)를 `recommend/matching_b.py`로 연결해 써요

