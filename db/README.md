# DB (PostgreSQL)

여름방학 앱 전체 DB(노션의 `erd최종_수정본.sql`)에 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)용 컬럼과 표를 더한 전체 파일이다. 원본에서 바뀐 줄은 없고 더한 줄만 있으며, 더한 곳에는 `[알고리즘]` / `[알고리즘 추가]` 주석이 붙어 있다.

```
psql -d 데이터베이스 -f db/db.sql
```

## 기존 표에 더한 컬럼

| 컬럼 | 역할 |
|---|---|
| `categories.algo_category` | B의 5개 분류(top / outer / pants / skirt / dress) 중 무엇인지. 비어 있으면 추천에서 빠진다. '상의'는 B에서 top과 outer로 나뉘므로 카테고리 행마다 지정한다 |
| `body_infos.height` | A가 받는 키(cm) |
| `body_infos.upper_body_length` | A가 받는 상체 길이(cm) |
| `body_infos.leg_length` | A가 받는 다리 길이(cm) |
| `products.seasons` | 해당하는 계절 목록 (spring / summer / fall / winter 중 0개 이상). C의 트렌드 점수가 읽는다 |
| `products.popularity` | 0~1 인기도. C의 트렌드 점수 중 인기 부분. 기본 0 |

A의 나머지 입력(어깨너비, 팔 길이)은 원본 `body_infos.shoulder`, `arm_length`를 그대로 쓴다. 원본 `season`은 한 값이라 사계절 옷을 표현하지 못해서 `seasons`를 따로 두었다. `season`을 보고 채울 때는 봄→spring, 여름→summer, 가을→fall, 겨울→winter, 간절기→spring+fall로 옮긴다.

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

- `categories.algo_category`: 카테고리 데이터를 넣은 뒤 행마다 지정한다. 지정하지 않으면 그 카테고리 상품은 모두 추천에서 빠진다.
- `products.popularity`: 컬럼만 있다. 0~1을 만드는 산식은 C가 정해 갱신해야 하고, 안 하면 전부 0이다.
- `products.seasons`: 새 상품을 등록할 때 `season`과 함께 채운다.
- `product_tags`: 행이 없는 상품은 B가 점수를 내지 못해 추천에서 빠진다.
