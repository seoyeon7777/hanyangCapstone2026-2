# CLAUDE.md — 한양대 캡스톤 2026-2 (골격 맞춤 추천)

AI 코딩 도구가 이 파일만 읽고 작업을 이어갈 수 있게 정리한 문서다.
작업을 시작하기 전에 끝까지 읽고, 단계를 끝낼 때마다 **9. 진행 체크리스트**를 갱신한다.

## 1. 프로젝트 한눈에 보기

사용자가 키·몸무게·설문을 입력하면 두 가지를 해 준다.

1. **가상 피팅**: 옷 치수·소재를 받아 Blender로 S/M/L 아바타에 입혀 보고 실루엣 이미지와 총평을 보여 준다. → **다른 저장소**(`seoyeon7777/hanyangCapstone2026`)에 있다.
2. **골격 맞춤 추천**: 사용자의 골격(스트레이트 S / 웨이브 W / 내추럴 N)에 맞는 옷을 점수로 골라 피드로 보여 준다. → **이 저장소**.

| 파트 | 담당 브랜치 | 파일 |
|---|---|---|
| A 체형 분류 | feature/jihyun | `body_profile.py` (classify), `evaluate.py`, `try_classify.py`, `sample.csv` |
| B 옷 매칭 | feature/yeonoh (10/16 최신, 한때 yeonoh) | `matching.py`(점수·이유·코디), `tagging.py`(태그 붙이기, 10/16 분리), `server.py`(B API), `data/*.json`(가중치·문장·코디 규칙), `tests/`(pytest 240개), `tools/`·`reports/`(실험·근거 문서), `README_B.md`(B 설명) |
| C 추천·홈 피드 | feature/seoyeon | `recommend/` |

세 파트는 feature/integration 브랜치에서 처음 통합했다. 통합 서버는 맨 위 `app.py`다.
**화면은 친구들이 안드로이드 스튜디오(안드로이드 앱)로 만든다.** 앱 프로젝트는 `android/`(Java)에 있다. 화면 대부분은 친구들 몫이고, 홈 피드 서버 연결(아래 4-10)만 C(서연)가 맡았다. 앱 연결법은 `recommend/안드로이드_연결가이드.md`.
C 파트는 원래 hanyangCapstone2026 저장소에서 만들었고, 이 저장소로 복사해 왔다.

## 2. 폴더 구조

```
README.md               저장소 소개
android/                안드로이드 앱 (Android Studio로 이 폴더를 연다. 친구들 담당)
requirements.txt        flask (API·서버용), psycopg (DB용. 알고리즘과 테스트 대부분은 표준 라이브러리만)
db/                     DB 담당: db.sql (PostgreSQL 표 32개, Supabase에 그대로 실행), README.md (표 설명)
database_url.txt        (깃에 없음) DB 접속 주소 한 줄. 있으면 서버가 Supabase를 쓴다
app.py                  ★ 통합 서버 (B server.create_app + C feed_bp). python app.py → 0.0.0.0:5000 (에뮬레이터 10.0.2.2, 폰은 PC IP)
                        모든 오류를 JSON {"ok": false, "error"}로. HOST·PORT·FLASK_DEBUG 환경변수
body_profile.py …       A 파트
matching.py, server.py, data/   B 파트 (B의 data/는 recommend/data/와 다른 폴더)
recommend/              ★ C 파트 (추천 피드) — 이 폴더에서 작업
  rec_config.py         비중·태그 표 등 숫자 모음. 숫자는 여기서만 바꾼다
  matching_b.py         B 파트 연결: C 약속 모양(match_score, outfit_bonus)으로 B를 감싼다. from_profile/to_profile로 A↔C 이름 변환
  fake_matching.py      임시 매칭 (match_score, outfit_bonus, classify_kind). 이제 test_feed.py 손계산 테스트에서만 쓴다
  feed.py               C 파트 본체 (final_score, score_all, rank, feed_order, home_feed, outfits, reason, update_pref, need_rediagnosis)
  feed_api.py           7·8단계 API (Flask Blueprint)
  store.py              11단계 저장소: DB 주소가 있으면 PgStore(PostgreSQL), 없으면 MemoryStore(items.json + 메모리)
  seed_db.py            시연 상품 30개를 DB에 넣는 스크립트 (여러 번 실행해도 됨)
  DB_연결가이드.md       Supabase 만들기 → 주소 넣기 → 확인 (팀원용)
  templates/feed_demo.html  8단계 연습용 무한 스크롤 화면 (/feed-demo). 팀 공용 templates/와 분리
  안드로이드_연결가이드.md  앱 담당용: 서버 주소, 요청·응답 모양, Kotlin(Retrofit) 무한 스크롤·토글·찜 예시
  홈화면_연결가이드.md   웹(JavaScript) 버전 사용법 — 연습 화면용
  test_feed.py          C 로직 정답 테스트 (임시 매칭 + items_fake.json, 표준 라이브러리만)
  test_integration.py   A+B+C 연결 테스트 (표준 라이브러리만)
  test_api.py           API 테스트 (Flask 필요, 맨 위 app.py를 불러옴. 항상 메모리 저장소)
  test_db.py            DB 저장소 테스트 (로컬 PostgreSQL에 임시 DB를 만들어 확인. TEST_DATABASE_URL 없으면 건너뜀)
  data/items.json       시연용 가짜 상품 30개 — B 태그 이름(fit:slim, sil:IH …)
  data/items_fake.json  같은 30개의 옛 태그 버전 — test_feed.py 전용
```

주의: 설정 파일 이름은 `rec_config.py`다. 원래 저장소에서는 `.gitignore`가 `config.py`를 막고 Blender 쪽 `config.py`와 import가 겹쳐서 이렇게 정했다. 나중에 가상 피팅 서버와 합칠 수 있으니 `config.py`로 바꾸지 말 것.

## 3. 팀 약속 (함수 모양 — 바꾸지 말 것)

전체 흐름: 입력 → ① 체형 분류(A) → ② 옷 속성 태그 → ③ 체형 적합도(B) → ④ 최종 순위(C) → ⑤ 코디 조합(C) → ⑥ 추천 이유·학습(C)

### A 파트 출력 = body (체형 분류 결과)
```python
body = {
    "ratio":   {"S": 0.62, "W": 0.25, "N": 0.13},  # 합 1.0
    "kind":    "single",                            # 1·2위 격차 ≥ 0.10 → single, 아니면 mixed
    "primary": "S",                                 # 1위 타입
}
```
A 파트 계산 규칙 (참고): 아바타 점수표는 타입마다 최대점이 다르다(S 7 / W 9 / N 6).
원점수를 그대로 합치면 W가 항상 유리하므로, **타입별 최대점으로 나눈 뒤 세 타입 합이 1이 되게 비율로 바꾸고**,
그다음 `설문 비율 0.6 + 아바타 비율 0.4`로 합친다. 그래야 격차 10%p 규칙이 맞게 계산된다.

### B 파트 함수 = match_score(body, item)
```python
match_score(body, item) -> {
    "score": 0.0~1.0,        # 체형 적합도
    "excluded": bool,        # 단일형 + 1위 타입 피해야 할 태그 있음 → True
    "good_tags": [...],      # 1위 타입 기준 어울림 태그
    "avoid_tags": [...],     # 1위 타입 기준 피해야 함 태그
}
```
**실제 연결 (통합 후)**: B의 `matching.py`는 이름이 다르다(`match_item(item, profile, rules)` → `body_fit`, `reasons` …).
그래서 `recommend/matching_b.py`가 위 모양으로 바꿔 준다. `feed.py`는 `from matching_b import match_score, outfit_bonus` 한 줄로 B를 쓴다.
- score = B의 body_fit, excluded = B가 None을 주면 True
- good_tags/avoid_tags = 1위 타입 가중치(B의 data/tag_weight.json)가 양수/음수인 태그. good_tags는 가중치 큰 순
- 추가 키 `reasons` = B가 만든 추천 이유 문장 목록
- `outfit_bonus(body, top_tags, bottom_tags)` = B의 balance_adjustment (data/balance.json)
- A의 classify() 결과(ratios, is_mixed)는 B의 profile과 같은 모양이다. C의 body(ratio, kind)와는 `from_profile`/`to_profile`로 바꾼다
- B 설정 `exclude_no_info: true` (10/16 추가): 가중치표 태그가 하나도 없고 핏 표기도 못 읽은 상품은 추천에서 빠진다 (C에서는 excluded). 시연 상품 30개는 모두 태그가 있어 영향 없음
- B 설정(data/config.json)이 지금 `avoid_mode: penalty`라서 **제외되는 옷이 없다**(피해야 할 옷은 점수만 낮아짐).
  제외하려면 `avoid_mode: "exclude"`와 함께 **`avoid_threshold`를 -1로** 바꿔야 한다 (가중치 최솟값이 -1이라 지금 기준 -3으로는 아무것도 제외 안 됨 — `matching.validate_rules`가 경고함). 그러면 C도 자동으로 따른다

### item (상품) 형식
```json
{"product_id": "t01", "product_name": "칼라 셔츠", "clothing_type": "top|outer|pants|skirt|dress",
 "tags": ["fit:slim", "mat:cotton"],
 "seasons": ["spring", "fall"], "popularity": 0.8}
```

## 4. C 파트 알고리즘 명세

### 4-1. 체형 적합도 — B 파트가 계산 (아래는 test_feed.py용 임시 매칭 규칙)
- 실제 계산: B의 body_fit = Σ 태그 Σ 타입 비율 × 가중치 / 태그 수 → 0~1로 변환, 태그가 적으면 중립 쪽으로 당김 (matching.py 참고)
- 임시 매칭 규칙:
- 태그 점수: 골격별로 어울림 태그 +2, 피해야 함 태그 −3 (`rec_config.STYLE_TAGS`)
- 가중합 = Σ ratio[t] × 태그점수[t]
- body = clamp(0.5 + 가중합 / 16, 0, 1)
- 단일형이면 1위 타입의 피해야 함 태그가 하나라도 있는 옷은 **제외**. 혼합형이면 제외하지 않고 가중합으로 점수만 깎인다.

### 4-2. 최종 점수 `final_score` (2단계, 완료)
- 최종 = 0.60 × 체형 + 0.25 × 취향 + 0.15 × 트렌드
- 취향 = 아이템 태그 중 pref(태그→−1~1)에 있는 값들의 평균을 0.5 + 평균/2로 변환. 기록이 없으면 0.5
- 트렌드 = 0.5 × (지금 계절이면 1 아니면 0) + 0.5 × popularity
- 제외된 옷은 score 0.0, excluded True

### 4-3. 정렬 + 다양성 `rank(scored, top_n=10)` (3단계, 완료)
- 입력은 score_all() 결과, 반환은 상위 top_n개 목록
- 제외된 옷을 빼고 score 내림차순
- 같은 종류(clothing_type을 `rec_config.OUTFIT_GROUP`으로 묶은 값: 바지·치마는 하의 하나)가 3개 연속으로 나오지 않게 한다 (연속 최대 2개). 바꿀 후보가 없으면 그대로 둔다
- 상위 top_n 안에서 같은 대표 태그(good_tags 첫 번째)는 최대 3개. 넘는 옷은 상위권에서 빠진다. good_tags가 비면 제한 없음
- 방법: 점수순 후보에서 두 규칙을 지키는 가장 높은 옷을 하나씩 고른다. 둘 다 지키는 옷이 없으면 종류 연속 규칙만 포기
- 점수가 같으면 product_id 오름차순 (결과가 항상 같아야 테스트할 수 있다)

### 4-4. 홈 섹션 `home_feed(body, items, pref=None, season="fall")` (4단계, 완료)
- 섹션 순서: "오늘의 추천" → "상의" → "하의" → "아우터" → "원피스" → "추천 코디"
- "오늘의 추천": rank 상위 10개
- 카테고리별 상위 4개: 제외 빼고 점수순(다양성 규칙 없음)
- "추천 코디": outfits 상위 3세트. 이 섹션의 items는 옷이 아니라 코디 세트 `{"top", "bottom", "score", "bonus"}`
- 반환: `{"sections": [{"title": str, "items": [...]}, ...]}`

### 4-5. 코디 조합 `outfits(body, items, pref=None, season="fall", top_n=3)` (5단계, 완료)
- 제외되지 않은 상의 × 하의 모든 조합. 점수 = (상의 + 하의) / 2 + 보너스
- 보너스 +0.10 (1위 타입 기준, 둘 다 만족할 때):
  - S: 상의 collar 또는 slim_fit + 하의 straight_leg·center_press·h_line 중 하나
  - W: 상의 cropped + 하의 high_waist
  - N: 상의 oversized·boxy·loose_fit 중 하나 + 하의 wide
- 규칙 판단은 각 옷의 good_tags(1위 타입 기준)로 한다
- 같은 상의는 결과에 최대 1번만 (하의는 겹칠 수 있음). 동점이면 (상의 id, 하의 id) 순
- 반환: `[{"top": 결과, "bottom": 결과, "score": float, "bonus": float}, ...]`

### 4-6. 추천 이유 + 피드백 학습 (6단계, 완료)
- `reason(r)`: good_tags 앞 2개를 한국어 문장으로. 예: "칼라가 목선을 세워 주고, 몸에 맞는 핏이 라인을 정돈해 줘요." (태그→문장 표는 rec_config.REASON_TEXT, 어울림 태그 전부 있음). good_tags가 없으면 기본 문장, 제외된 옷은 제외 문장
- `update_pref(pref, item, event)`: 아이템 태그마다 click +0.05, like +0.10, purchase +0.20, return −0.20 더하고 −1~1로 자른다. 새 dict를 돌려주고 원본은 안 바꾼다. 모르는 event는 ValueError
- `need_rediagnosis(history, body)`: 최근 긍정 반응(like·purchase) 20개 중 1위 타입 피해야 함 태그가 붙은 옷이 50% 이상이면 True → "체형을 다시 진단해 볼까요?" 안내
  - history 형식: `[{"item": 상품, "event": "like"}, ...]` (오래된 것 → 최근 순)
  - 추가 규칙: 긍정 반응이 10개(REDIAG_MIN_EVENTS) 미만이면 판단하지 않고 False (찜 1~2개로 안내가 뜨는 것 방지)

### 4-7. API (7단계, 완료) — `recommend/feed_api.py`
- `GET /api/feed?user_id=u1&S=0.6&W=0.3&N=0.1&season=fall` 또는 `POST /api/feed` `{"user_id", "body": {"ratio": {...}}, "season"}`
  - 비율 합이 1이 아니면 1로 맞추고, kind·primary는 classify_kind로 계산
  - season을 안 보내면 오늘 날짜의 계절. user_id가 있으면 저장된 pref를 쓰고, 비율을 안 보내면 저장된 체형을 재사용
  - 응답: `{"body", "season", "sections"}`. 모든 옷(코디의 top·bottom 포함)에 `reason` 추가
- `POST /api/feed/event` `{"user_id", "product_id", "event"}` → `{"pref", "need_rediagnosis", "message"}`
- 잘못된 입력은 400 + `{"ok": false, "error": 한국어 메시지}` (B와 같은 모양). 본문이 JSON 객체가 아니거나, body·answers·measurements·user_id·product_id 모양이 틀려도 400 (500이 나지 않게 막음)
- app.py가 404·405·예상 못 한 500도 JSON으로 돌려준다 (안드로이드 앱이 HTML 오류 화면을 받지 않게)
- GET에서 S·W·N 중 빠진 값은 0 (POST와 같음)
- 사용자 데이터(USERS)는 서버 메모리에만 저장 → 서버를 끄면 사라짐 (시연용. 실제 서비스는 DB 필요)
- 맨 위 `app.py`가 B의 create_app()에 feed_bp를 등록한다 (B와 C의 API가 한 서버에서 돈다)
- `POST /api/feed`는 체형을 세 가지로 받는다: `answers`(+`measurements`) → A의 classify로 진단 / `profile`(A 결과) / `body.ratio`
  - A의 InputError(설문 빠짐 등)는 400과 A의 오류 문장으로 돌려준다
- 가상 피팅 서버(hanyangCapstone2026의 app.py)에 붙일 때: app.py에서 recommend 폴더를 sys.path에 넣고 `from feed_api import feed_bp` → `app.register_blueprint(feed_bp)`. 원래 저장소의 작업 브랜치 `claude/skeleton-type-style-guide-q1kpfc`에 이렇게 붙인 예가 있다

### 4-8. 홈 화면 무한 스크롤 (8단계, 완료)
- 요구사항: 홈 화면(친구들이 만드는 중, 아직 저장소에 없음)에서 스크롤하면 알고리즘 순서대로 상품이 계속 나와야 한다
- `feed_order(scored, page_size=10)`: 제외 안 된 옷 전체를 순서대로. rank와 같은 규칙을 10개 묶음마다 적용
  - 같은 종류 연속 최대 2개는 전체에 걸쳐 적용, 대표 태그 최대 3개는 묶음마다 다시 셈
  - 태그 제한에 걸린 옷은 버리지 않고 다음 묶음으로 미룸. 미룰 곳이 없으면 그냥 보여 줌
  - 첫 묶음 = rank(scored) = "오늘의 추천"
  - rank와 feed_order는 내부 함수 `_pick_in_order`를 같이 쓴다
- `GET/POST /api/feed/scroll` (`/api/feed`와 같은 체형·user_id 입력 + `page`(0~), `size`(1~50, 기본 10))
  - 다양성 묶음은 size와 상관없이 항상 10개(SCROLL_PAGE_SIZE). size는 한 번에 보낼 개수만 정한다 → 몇 개씩 받든 이어 붙인 순서가 같다
  → `{"body", "season", "page", "size", "total", "has_more", "items"}` (items에 reason 포함)
- `/feed-demo`: 연습 화면. 친구들 홈 화면이 오면 그 화면에서 `/api/feed/scroll`을 부르게 연결하고, 연습 화면은 지워도 된다

### 4-9. 홈 화면 토글 ON/OFF (9단계, 완료)
- 요구사항: 홈 화면 토글을 켜면 골격 맞춤 추천, 끄면 일반 쇼핑몰 방식(인기·사용자가 자주 누른 것) 추천
- `mode` = `"body"`(ON, 기본) | `"general"`(OFF) — `rec_config.MODES`
- `general_score(item, pref, season)`: 체형을 안 본다. 점수 = 인기 0.5 + 취향(pref_score) 0.3 + 계절 0.2
  - final_score와 같은 모양(body는 None, excluded False, good_tags 빈 목록)이라 rank·feed_order를 그대로 쓴다
  - 이유 문장: 취향 ≥ 0.55 → "자주 본 스타일과 비슷해요", 인기 ≥ 0.75 → "요즘 많이 찾는 인기 상품이에요", 제철 → "지금 계절에 입기 좋아요" (앞에서 2개)
- `score_by_mode(mode, body, items, pref, season)`: 토글에 맞는 점수 목록. home_feed·outfits에 `mode` 인자 추가 (OFF면 코디 보정 0)
- 취향(pref)과 반응 기록은 두 모드가 같이 쓴다. need_rediagnosis는 body가 None이면 False
- API: `/api/feed`, `/api/feed/scroll`에 `mode` 추가, 응답에 `"mode"`. `mode=general`이면 체형 없이도 된다 (`mode=body`는 여전히 체형 필요 → 400)
- `/feed-demo`에 토글 스위치

### 4-10. 안드로이드 앱 홈 연결 (10단계)
- `android/app/src/main/java/com/example/capstone/data/RecommendApi.java`: `BASE_URL`(에뮬레이터 `http://10.0.2.2:5000`, 폰은 PC IP), `loadFeed(context, mode, page, callback)` → `POST /api/feed/scroll`, `sendEvent(context, product, event)` → `POST /api/feed/event`
  - 외부 라이브러리 없이 `HttpURLConnection` + `org.json`. 백그라운드 스레드에서 보내고 결과는 메인 스레드
  - user_id = 기기마다 만든 `app-<UUID>` (SharedPreferences "recommend")
  - 설문 답(`BodyTypeActivity.lastSurveyAnswers`)을 매 요청에 `answers`로 같이 보냄 → 서버를 다시 켜도 진단이 바로 됨
- `MainActivity` 홈: DummyData 대신 서버 순서. 바닥 600dp 전에 다음 묶음, 토글 `switchBodyMode`(ON body / OFF general), 제목 "○○ 체형 맞춤 추천"
  - 토글 ON인데 400(진단 없음) → 안내 후 OFF로 바꿔 다시 부름. 서버 연결 실패 → DummyData를 보여 줌
  - 설문을 새로 하고 돌아오면(`lastSurveyTime` 변화) 다시 부름
- `ProductAdapter`: 서버 상품 클릭 → click, 찜 → like. 카드에 추천 이유(`textReason`), 가격 0이면 숨김
- `Product`: `serverId`·`reason` 추가 (서버 상품은 id −1)
- 찜(서버 상품): `RecommendApi.saveLike` — 찜 상태를 기기(SharedPreferences "recommend"의 liked_products)에 저장 + 찜할 때 like 전송. 서버 상품 Product는 product_id마다 하나만 만들어 다시 불러와도 하트 유지
  - 찜 탭(`WishActivity`)은 DummyData + `RecommendApi.likedServerProducts`를 보여 줌. 상세 화면은 `server_product_id`로 서버 상품을 찾아 하트만 동작 (옵션·장바구니는 아직 DummyData 상품만)
- 확인: 여기서는 Android SDK를 받을 수 없어 androidx 대체 클래스로 javac 컴파일 검사 + 같은 요청을 curl로 서버에 보내 응답 확인까지만 했다. 실제 실행은 Android Studio에서 확인할 것

### 4-11. DB 저장 (Supabase, 11단계)
- 팀 결정: DB는 Supabase(무료 PostgreSQL). 표는 DB 담당의 `db/db.sql` 그대로. 연결 방법은 `recommend/DB_연결가이드.md`
- `store.make_store()`: `DATABASE_URL` 환경 변수 → 맨 위 `database_url.txt` 순으로 주소를 찾고, 없으면 MemoryStore (지금까지와 같음)
- 함수: `items()`, `item(id)`, `user(id)` → `{"body","pref","history"}` 또는 None, `ensure_user`, `save_body(id, body, answers)`, `add_event(id, item, event)`
- PgStore
  - 상품: products + categories + product_tags (+ brands) 조인. 판매중·삭제 안 됨·태그 있음·`clothing_type`이 OUTFIT_GROUP에 있는 것(jumpsuit 제외). `DB_ITEMS_CACHE_SECONDS`(60초) 캐시
  - 앱 user_id(`app-…`)가 처음 오면 users 행을 만든다 (이름 `DB_DEMO_USER_NAME`, 비밀번호 빈칸, role user)
  - 체형: body_profiles. 비율·답이 같으면 새 행 안 만듦(앱이 스크롤마다 같은 답을 보냄), 바뀌면 이전 행 is_current=false. confidence·is_mixed는 A의 MIX_GAP 기준(db.sql 제약과 같음)
  - 반응: user_events에 쌓기만. pref는 저장하지 않고 기록을 update_pref로 다시 계산 → 메모리 저장소와 결과가 같다
  - 연결 실패 → StoreError → API 503 `{"ok": false, "error"}`. psycopg는 `prepare_threshold=None`(Supabase 풀러 대응), 연결 하나를 lock으로 차례로 사용, 끊기면 한 번 다시 연결
- 응답 상품에 DB의 `price`, `brand`, `image_url`을 더해 보낸다 (`feed_api.for_screen`). feed.py 결과 모양은 그대로
- 앱(RecommendApi.toProduct)은 응답의 price·brand를 카드에 쓴다 (없으면 가격 숨김·브랜드 자리에 종류). image_url은 아직 안 씀 (사진 불러오기 라이브러리 필요)

### 4-11. 안드로이드 카메라·치수 변경 (10/21)
- 관절 인식: ML Kit Pose → **MediaPipe Pose Landmarker** (`android/.../camera/MediaPipePoseDetector.java`, 모델 `assets/models/pose_landmarker_full.task` 약 9MB는 저장소에 포함). `camera/Pose`·`PoseLandmark`는 ML Kit와 같은 이름·번호(33개)라 치수 계산 코드는 그대로다. 윤곽 분리(selfie segmentation)는 ML Kit 유지
  - MediaPipe의 회전 옵션은 ML Kit와 방향이 반대라 **비트맵을 직접 돌려서** 넘긴다 (안 그러면 골격이 누워서 나옴)
  - 신뢰도는 `presence`를 쓴다 (`visibility`는 옷·가림으로 낮게 나와 기존 기준 0.4~0.5로 거르면 대부분 탈락)
- MediaPipe는 `tasks-vision:0.10.33`. 0.10.21은 x86_64 라이브러리가 없고 16KB 페이지 기기에서 `program alignment (4096) cannot be smaller than system page size` 로 앱이 꺼졌다 (에뮬레이터에서 카메라 화면을 열면 홈으로 튕김). 0.10.33은 둘 다 해결
- **카메라 보조 지표 (논문 기준)**: 박선미·정은영(2024) 「신체 유형별 골격진단 요소의 정량화 연구」(여성 20~60대, 사이즈코리아 8차) Table 6의 두 요소만 쓴다. 가슴두께(cm, 옆면 두께=측정선 조정 화면의 "두께")와 어깨 경사각(도, 정면 윤곽에서 `camera/BodyShapeFeatures`가 추정). 나머지(목길이·목둘레·무릎뼈·쇄골)는 카메라로 못 재서 쓰지 않고, 설문(q2·q3·q4)이 맡는다
  - A: `body_profile.py`의 `chest_depth`·`shoulder_slope`·`age`(선택) 치수 → `score_camera_shape`가 `score_avatar`에 더한다 (`W_CHEST_DEPTH`·`W_SHOULDER_SLOPE` 각 0.5, 논문 구간 중간값 사이를 선형으로 나눔, 나이 없으면 30대). 값이 없거나 범위 밖(가슴두께 10~40cm, 경사각 5~50도)이면 무시 → 키 없을 땐 이전과 결과가 똑같다 (무작위 6000건 비교로 확인). 테스트 `tests/test_a_camera_shape.py`
  - 앱: 측정선 조정에서 가슴두께·어깨 경사각을 같이 구해 프로필 `parts`에 `CHEST_DEPTH`·`SHOULDER_SLOPE`로 저장하고(`MeasurementResultActivity`, 키 이름은 `BodyProfileStore.PART_CHEST_DEPTH`·`PART_SHOULDER_SLOPE`), `RecommendApi`가 대표 체형의 치수 5개(키·어깨너비·팔길이·상체길이·다리길이) + 두 값을 매 요청에 `measurements`로 보낸다. **이전에는 설문 답만 보냈으므로, 대표 체형이 저장된 사용자는 이제 설문 0.6 + 카메라 0.4로 진단된다.** 앱은 아직 나이를 안 보낸다(가입 생년월일 연결 전)
  - 한계: 두 값 모두 옷·머리카락·자세에 민감한 추정이고, 논문 구간이 실제 진단과 맞는지는 검증된 적 없다. 어깨 경사각은 어깨 끝이 아니라 어깨 관절을 쓰므로 실제보다 작게 나올 수 있다. `sample.csv`에 이 두 값이 없어 정확도 변화는 아직 못 쟀다
- 자동 촬영: 3초 카운트다운 없이 가이드에 맞는 프레임이 0.15초 이어지면 바로 촬영. 측면은 안내창을 닫은 뒤 3초 대기
- `CapstoneApp`이 모든 화면에서 하단 내비게이션 바를 숨긴다
- **3D 아바타 삭제** (정확도 낮음): `AvatarPreviewActivity`, `avatar/` 패키지, `avatar.glb`·morph json, Filament 의존성 제거. 체형은 측정 결과 화면(`MeasurementResultActivity`)에서 바로 저장한다. 3D용으로 만들었던 치수 직접 수정 화면(`BodyEditActivity`)과 개발용 데모 체형(`DemoBodySamples`, `DemoSampleActivity`, 피팅 화면의 데모 버튼)은 더 쓰지 않아 삭제했다. `BodyMeasurementContract`에는 키 전달용 `EXTRA_USER_HEIGHT_CM`만 남았다
- **측정 위치 조정** (`MeasurementAdjustActivity` + `camera/MeasureLineView` + `camera/MeasurementSession`): 정면·측면 촬영 뒤, 사진 위의 가슴·허리·골반 선을 위아래로 끌어 맞추면 폭·두께·둘레가 바로 다시 계산된다. 선 높이는 몸 높이 대비 비율(기본 29/43/52%)이고 정면·옆면이 같은 값을 쓴다. 윤곽 마스크는 줄마다 몸의 좌우 끝만 요약해 메모리(`MeasurementSession`)에 둔다. 카메라 촬영(`BodyMeasurementActivity`)과 앨범 선택(`PhotoMeasurementActivity`, 카메라 화면의 "앨범에서 선택" 버튼) 두 흐름 모두 이 화면을 거친다. 두께÷폭 비율이 0.40~0.95를 벗어나면(다른 사람 사진, 같은 사진 두 번 등) 빨간 경고와 확인창을 띄운다. 목·팔·허벅지·종아리 둘레는 "몸 전체 폭"을 읽는 방식이라 틀려서 더 이상 계산하지 않는다 (`camera/BodySilhouetteAnalyzer`는 쓰이지 않는 옛 코드). 윤곽 요약은 `camera/SilhouetteBuilder`
- **윤곽에서 몸 덩어리 고르기·너무 작게 찍히면 촬영 안 함** (앱·웹 공통): 윤곽 마스크에는 배경 물건(선반·침대·옷더미)도 사람처럼 잡혀 폭·두께가 크게 틀리는 일이 있었다 (예: 가슴 폭 63cm / 두께 2.7cm). 이제 정면·옆면 사진 모두 관절로 몸의 중심선(어깨·엉덩이 평균 x, 코~발목으로 짐작한 몸 키)을 구하고(`BodyShapeFeatures.bodyAxis`), `SilhouetteBuilder`가 그 중심선이 지나는 덩어리를 몸으로 고른다. 중심선 좌우 몸 키의 16%(160cm 기준 ±26cm) 밖은 보지 않고, 중심선에서 12%(약 19cm)보다 먼 덩어리만 있는 줄은 몸이 없는 줄로 본다. 위·아래 끝도 고른 덩어리가 있는 줄로 정한다. 관절을 못 찾으면 예전처럼 화면 가운데 기준. 또 전신(코~발목)이 화면 높이의 62% 미만이면 "조금 더 가까이 와서…"를 띄우고 촬영하지 않는다 (전에는 48%). 테스트 `SilhouetteBuilderTest`
- 옷차림 안내: 설문 결과 "예" → 카메라 직전에 안내 창(몸에 붙는 옷·관절이 보이는 옷·팔 벌리기, "다시 보지 않기" 체크 시 `measure_tips/skip_clothes_tip`으로 건너뜀), 카메라 화면 상단에 "몸에 붙는 옷을 입으면 더 정확해요" 한 줄 (`textClothesTip`)
- 사이즈 알림(`SizeFitChecker`)이 쓰는 치수: 가슴·어깨·허리·골반·허벅지
- 사진 확인 대화상자: 세로 사진이 버튼을 화면 밖으로 밀어내던 문제를 사진 높이를 화면 55%로 제한해 해결
- 웹 테스트 버전: `web/` (아이폰 Safari용, 같은 계산식). 사용법은 `web/README.md`
- 알려진 한계(미수정): 길이 환산이 발목 기준이라 약 5% 크게 나올 수 있음, 정면 가슴 폭에 팔이 섞일 수 있음, 측면 사진이 옆모습인지 검사 안 함

## 5. 정답 테스트 (반드시 통과)

`python -m pytest` (맨 위에서) — B 테스트 240개 (pytest.ini가 tests/만 모은다. recommend/ 테스트는 아래처럼 따로 실행).
`cd recommend && python test_feed.py` — 현재 38개 통과 (C 로직, 임시 매칭 기준 손계산. 토글 OFF 6개 포함).
`cd recommend && python test_integration.py` — 현재 8개 통과 (A+B+C 연결).
`cd recommend && python test_api.py` — 현재 19개 통과 (Flask 필요: `pip install -r requirements.txt`).
`TEST_DATABASE_URL=postgresql://postgres@127.0.0.1:5432/postgres python recommend/test_db.py` — 현재 8개 통과 (로컬 PostgreSQL. **Supabase 주소 넣지 말 것**).

아래 표의 숫자는 **임시 매칭 기준**이다 (test_feed.py). 실제 B 연결 값은 B의 가중치를 따르므로 test_integration.py가 "C 결과 = B 결과"인지로 확인한다.

| 테스트 | 정답 |
|---|---|
| 스트레이트(S 1.0, 단일형) 칼라 셔츠, 가을 | 체형 0.875, 트렌드 0.9, 최종 **0.785** |
| 스트레이트 단일형 러플 블라우스 | **제외** (avoid_tags = ["ruffle"]) |
| 혼합형(S 0.5/W 0.45/N 0.05) 러플 블라우스, 가을 | 제외 안 됨, 체형 0.51875, 최종 **0.48125** |
| 칼라 셔츠 + pref {"collar": 1.0} | 최종 **0.91** |
| 스트레이트 상위 8개 | 칼라 셔츠·센터프레스 슬랙스·H라인 스커트 포함 |
| rank: 상의 a·b·c + 하의 d | a, b, **d**, c (상의 3연속 방지) |
| rank: collar 대표 태그 5개 + 다른 1개 | collar 3개 + 다른 1개만 |
| 스트레이트(S 1.0) 홈 "상의" 섹션 | t01 0.785, t02 0.77, t03 0.7025, t04 0.63125 |
| 스트레이트(S 1.0) 추천 코디 | t01+b02 0.883125, t02+b02 0.875625, t03+b02 0.841875 |
| 웨이브(W 1.0) 추천 코디 1위 | t04 크롭 니트 + b04 하이웨이스트 슬림 팬츠 0.804375 |
| 칼라 셔츠 추천 이유 | "칼라가 목선을 세워 주고, 몸에 맞는 핏이 라인을 정돈해 줘요." |
| 크롭 니트 구매 5번 | 최종 점수 +0.125 |
| 재진단 | 긍정 20개 중 피해야 할 옷 10개 → True, 9개 → False, 긍정 9개 이하 → False |
| 스크롤 첫 묶음 | rank 결과와 같음 |
| 스크롤 collar 5개 + 다른 1개, 묶음 4 | c1 c2 c3 z / c4 c5 (밀린 옷은 다음 묶음) |
| 스크롤 끝까지 | 제외 안 된 옷이 전부 정확히 한 번씩 |
| 토글 OFF 칼라 셔츠, 가을, 기록 없음 | 0.5×0.8 + 0.3×0.5 + 0.2×1 = **0.75** |
| 토글 OFF + pref {"collar": 1.0} | **0.9** |
| 토글 OFF 순서 (가을, 기록 없음) | b03, t08, b06, t01, t11 … 30개 전부 (제외 없음) |

새 단계를 만들면 그 단계의 정답 테스트를 손으로 계산해 `test_feed.py`에 추가한다. 기존 정답값은 고치지 않는다(사람이 명시적으로 요청한 경우만 예외).

## 6. 코딩 규칙

- 한 번에 한 단계만 만든다. 끝나면 `python test_feed.py` 통과를 확인한다
- 3장 함수 모양(이름·입력·출력 키)은 바꾸지 않는다
- 숫자(비중·보너스·임계값)는 `rec_config.py`에만 둔다
- 외부 라이브러리 없이 파이썬 표준 라이브러리만 쓴다 (Flask는 7단계 API에서만, psycopg는 store.py PgStore에서만 — DB 주소가 없으면 불러오지 않는다)
- 주석과 출력 메시지는 한국어
- 가상 피팅 코드는 다른 저장소에 있다. 이 저장소에서 그쪽 코드를 복사해 오지 않는다

## 7. 임시값 (나중에 다시 맞출 것)

치수 구간 기준, 비중 0.60/0.25/0.15, 태그 점수 +2/−3, BODY_SCALE 8, 코디 보너스 0.10, 학습 가중치는 모두 임시값이다.
실제 아바타·사용자 데이터를 모은 뒤 조정한다. 스타일 가이드는 통용 이론 기반이라 전문가 감수가 필요하다.

## 8. 발표 대비 핵심 함수

`final_score`(최종 점수), `rank`(정렬·다양성), `home_feed`(홈 구성) 세 개는 사람이 직접 설명할 수 있어야 한다.

## 9. 진행 체크리스트

- [x] 1단계: 가짜 상품 30개, rec_config.py, fake_matching.py
- [x] 2단계: final_score, score_all + 정답 테스트 9개
- [x] 3단계: rank (정렬 + 다양성) + 테스트 7개
- [x] 4단계: home_feed (홈 섹션, "추천 코디"는 5단계에서 채움) + 테스트 3개
- [x] 5단계: outfits (코디 조합) + 테스트 3개, home_feed "추천 코디" 채움
- [x] 6단계: reason, update_pref, need_rediagnosis + 테스트 6개
- [x] 7단계: Flask API (/api/feed, /api/feed/event) + test_api.py 6개
- [x] 8단계: 홈 화면 무한 스크롤 (feed_order, /api/feed/scroll, /feed-demo) + 테스트 7개
- [x] 안드로이드 연결 준비: app.py가 같은 네트워크에서 접속 허용, 오류 전부 JSON, 잘못된 입력 500 → 400, 안드로이드_연결가이드.md
- [x] 앱(안드로이드) 홈을 /api/feed/scroll·/api/feed/event에 연결 (4-10, feature/seoyeon-android-connect)
- [x] Android Studio 에뮬레이터에서 실제 실행 확인 (Pixel 6 API 35: 홈에 서버 상품·추천 이유·토글 표시)
  - API 37 에뮬레이터는 친구들 3D 라이브러리(Filament)가 16KB 페이지를 지원하지 않아 앱이 꺼진다 → API 35(16KB 아닌 이미지)로 실행
- [ ] 서버 상품의 사진(image_url) 표시, 상세 화면의 옵션·장바구니 (찜 탭·상세 하트는 연결됨)
- [x] 11단계: DB 저장 (store.py, Supabase) — 상품을 DB에서, 체형·반응을 body_profiles·user_events에. test_db.py 8개
- [x] Supabase 프로젝트(서연 계정, 조직 hanyang-capstone, 싱가포르) 만들고 db.sql 실행, seed_db.py로 시연 상품 30개 → 서버 "데이터 DB" 연결 확인
  - 접속 주소는 Session pooler(`aws-0-ap-southeast-1.pooler.supabase.com:5432`). 비밀번호는 각자 database_url.txt에만
- [ ] 앱 상단 탭(신상·베스트·카테고리)·카테고리 화면·검색·장바구니는 아직 DummyData → 서버 상품으로 바꿀지 팀 결정
- [ ] 로그인 사용자와 추천 user_id(app-…) 연결 (로그인이 서버·DB와 연결된 뒤)
- [ ] 실제 상품 데이터(products + product_tags) 넣기, popularity 산식 정하기
- [ ] B 파트 server.py의 상품 저장소는 아직 메모리 (B API를 쓸 때만 해당)
- [x] A·B·C 통합 (feature/integration): matching_b.py로 B 연결, app.py 통합 서버, /api/feed에 설문 answers 입력, 상품 태그를 B 이름으로, test_integration.py 8개
- [x] 9단계: 홈 화면 토글 ON(골격 맞춤)/OFF(일반 쇼핑몰) — general_score, mode 인자, 테스트 9개
- [ ] 팀 결정: B의 avoid_mode(penalty/exclude), mixed_strategy — 지금은 penalty·blend
