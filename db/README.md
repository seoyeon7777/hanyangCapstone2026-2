# DB (PostgreSQL)

여름방학 앱 전체 DB(노션의 `erd최종_수정본.sql`)에 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)용 컬럼과 표를 더한 전체 파일이다. 원본에서 바꾼 곳은 아래에 따로 적었고, 더하거나 바꾼 곳에는 `[알고리즘]` / `[알고리즘 추가]` 주석이 붙어 있다.

```
psql -d 데이터베이스 -f db/db.sql
```

## 기존 표에 더한 컬럼

| 컬럼 | 역할 |
|---|---|
| `body_infos.height` | A가 받는 키(cm) |
| `body_infos.upper_body_length` | A가 받는 상체 길이(cm) |
| `body_infos.leg_length` | A가 받는 다리 길이(cm) |
| `products.seasons` | 해당하는 계절 목록 (spring / summer / fall / winter 중 0개 이상). C의 트렌드 점수가 읽는다 |
| `products.popularity` | 0~1 인기도. C의 트렌드 점수 중 인기 부분. 기본 0 |

A의 나머지 입력(어깨너비, 팔 길이)은 원본 `body_infos.shoulder`, `arm_length`를 그대로 쓴다. 원본 `season`은 한 값이라 사계절 옷을 표현하지 못해서 `seasons`를 따로 두었다. `season`을 보고 채울 때는 봄→spring, 여름→summer, 가을→fall, 겨울→winter, 간절기→spring+fall로 옮긴다.

## 원본에서 바꾼 것

| 바꾼 곳 | 내용 |
|---|---|
| `categories.clothing_type` 값 | 한글(상의·하의·치마·원피스·점프수트)을 영어 `top` / `outer` / `pants` / `skirt` / `dress` / `jumpsuit`로 바꾸고 아우터(`outer`)를 더했다. B와 C가 이 값을 그대로 쓴다(한글 표시는 앱에서 바꾼다). `jumpsuit`는 B에 대응이 없어 추천 조회에서 빼야 한다. B는 모르는 카테고리를 받으면 추천 요청 전체가 오류가 난다 |
| `body_infos.shoulder`, `arm_length` | numeric(5,2) → double precision. A가 float으로 계산하는데 numeric은 Python에서 Decimal로 읽혀 계산 오류가 난다 |
| `users.user_id`, `products.product_id`와 이를 참조하는 열 24개 | integer → text. C의 API가 `user_id`·`product_id`를 문자열로만 받는다. 번호는 자동으로 "1", "2", …로 붙고 `t01`처럼 문자열을 직접 넣어도 된다. 쿼리에서는 `user_id = '1'`처럼 문자열로 비교해야 한다 |

더한 컬럼 중 `popularity`, `ratio_s/w/n`, `confidence`, `height`, `upper_body_length`, `leg_length`도 같은 이유로 double precision이다.

## 더한 제약

| 제약 | 이유 |
|---|---|
| `products.description`은 5000자 이하 | B가 더 긴 설명을 거부해서 추천 요청 전체가 오류가 난다 |
| `products.season`이 있으면 `seasons`에 대응하는 계절이 들어 있어야 한다 (간절기는 spring + fall) | 두 값이 어긋나지 않게 한다. `season`만 넣으면 `seasons`는 자동으로 채워진다 |
| `body_profiles.is_mixed`는 `confidence < 0.10`과 같아야 한다 | A의 혼합형 기준과 같은 값만 저장한다. A의 기준(`MIX_GAP`)을 바꾸면 이 제약도 고쳐야 한다 |
| `product_tags.tags`, `text_added` 안의 값은 모두 문자열 | B는 문자열 목록만 받는다 |

## 새 표

### `body_profiles` — A의 골격진단 결과

사용자당 현재 결과는 하나이고, 재진단하면 이전 행은 이력으로 남는다.

| 컬럼 | 역할 |
|---|---|
| `profile_id` | 진단 결과 한 건의 번호 |
| `user_id` | 누구의 결과인지 (`users`) |
| `body_id` | 진단에 쓴 치수 기록 (`body_infos`). 설문만으로 진단했으면 비어 있다 |
| `ratio_s`, `ratio_w`, `ratio_n` | 스트레이트·웨이브·내추럴 비율. B가 태그 점수를 섞을 때 쓰는 핵심 값. 합은 1 |
| `primary_type`, `secondary_type` | 비율이 가장 큰 타입, 두 번째로 큰 타입 (S / W / N) |
| `confidence` | 1위와 2위의 격차. 작을수록 애매한 체형 |
| `is_mixed` | 격차가 0.10 미만이라 혼합형으로 본 경우 true |
| `answers` | 설문 답(q1~q7) |
| `is_current` | 현재 유효한 결과인지. 재진단하면 이전 행을 false로 바꾼다 |
| `created_at`, `updated_at` | 진단한 시각, 수정한 시각 |

### `product_tags` — B가 상품에 붙인 태그

상품을 등록할 때 한 번 태깅해서 저장하고, 추천은 여기 저장된 값만 읽는다. 상품당 한 행.

| 컬럼 | 역할 |
|---|---|
| `product_id` | 어떤 상품의 태그인지 (`products`) |
| `tags` | 점수에 쓰는 태그 전체. 예: `["fit:slim","mat:wool"]` |
| `text_added` | `tags` 중 판매자 양식이 아니라 상품명·설명 글에서 추가로 읽은 태그 |
| `fit_stated` | 표기된 핏 (slim / regular / over / unknown). 태그도 없고 unknown이면 추천에서 뺀다 |
| `material_basis` | 소재 태그의 근거. `main` 겉감·몸판 표기, `unlabeled` 부위 표기 없는 혼용률, `unclear` 안감·배색만 있어 소재 태그를 붙이지 않음 |
| `attrs` | 판매자 등록 양식 입력값 전체 |
| `source` | 태그를 만든 경로. `direct` 판매자 직접 등록, `manual` 팀이 같은 양식으로 입력, `text` 양식 없이 상품명·설명 글만으로 태깅 |
| `rules_version` | 태그를 만들 때 쓴 규칙 버전. B 코드는 이 값을 만들지 않으므로 쓰려면 서버에서 채우고, 안 쓰면 비워 둔다 |
| `tagged_at`, `created_at`, `updated_at` | 태깅한 시각, 행이 만들어진 시각, 수정된 시각 |

### `user_events` — 반응 기록

C가 취향 점수와 재진단 안내를 계산할 때 쓴다. `wishlists`는 지금 찜한 목록이라 찜을 풀면 행이 사라지고 `orders`에는 클릭·반품이 없어서, 모든 반응을 시간순으로 지우지 않고 쌓는 표를 따로 두었다.

| 컬럼 | 역할 |
|---|---|
| `event_id` | 반응 한 건의 번호. 순서대로 커진다 |
| `user_id` | 누가 반응했는지 (`users`) |
| `product_id` | 어떤 상품에 반응했는지 (`products`) |
| `event` | 반응 종류 (click / like / purchase / return) |
| `created_at` | 반응한 시각 |

## 직접 채워야 하는 것

- `products.popularity`: 컬럼만 있다. 0~1을 만드는 산식은 C가 정해 갱신해야 하고, 안 하면 전부 0이다.
- `products.seasons`: `season`만 넣으면 자동으로 채워진다. 여러 계절에 해당하는 상품만 직접 넣는다.
- `product_tags`: 행이 없는 상품은 B가 점수를 내지 못해 추천에서 빠진다.
