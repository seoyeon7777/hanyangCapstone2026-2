# hanyangCapstone2026-2

골격(스트레이트/웨이브/내추럴) 진단 기반 의류 추천 알고리즘

## 개요
- 사진 선택형 설문과 아바타 신체 비율로 골격 유형 분류
- 분류 결과로 맞춤 홈 피드 추천

## 구성
| 파트 | 내용 |
|---|---|
| 1 | 체형 분류 엔진 |
| 2 | (작성 예정) |
| 3 | 추천·홈 피드 → `recommend/` |

## 개발 환경
- Python 3.12
- 추천 서버: `pip install -r requirements.txt` → `cd recommend` → `python server.py` → http://127.0.0.1:5000/feed-demo

## 파트 3: 추천·홈 피드 (`recommend/`)
체형 분류 결과(스트레이트·웨이브·내추럴 비율)를 받아 홈 화면에 보여 줄 상품 순서, 코디, 추천 이유를 만들어요.

| 기능 | 설명 |
|---|---|
| 최종 점수 | 체형 적합도 60% + 취향 25% + 시즌·인기 15% |
| 순위·다양성 | 같은 종류 연속 2개, 같은 스타일 10개 중 3개까지 |
| 홈 화면 | 오늘의 추천 + 상의·하의·아우터·원피스 + 추천 코디 |
| 무한 스크롤 | `GET /api/feed/scroll?page=0&size=10` |
| 학습 | 클릭·찜·구매·반품으로 취향 반영, 진단과 어긋나면 재진단 안내 |

- 테스트: `cd recommend && python test_feed.py` / `python test_api.py`
- 자세한 명세: `CLAUDE.md`, 쉬운 설명: `recommend/C파트_쉬운설명.md`, 화면 연결: `recommend/홈화면_연결가이드.md`
- 파트 2(체형 적합도 매칭)는 아직 임시 코드(`recommend/fake_matching.py`)를 써요

