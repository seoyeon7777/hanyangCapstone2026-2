# DB (PostgreSQL)

여름방학에 만든 앱 전체 DB를 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)에 맞게 고친 최종본. 파일은 `db.sql` 하나다.

```
psql -d 데이터베이스 -f db/db.sql
```

- 원본 SQL(노션의 `erd최종_수정본.sql`)에 알고리즘용 컬럼과 표를 직접 더한 전체 파일이다. 원본에서 지우거나 바꾼 줄은 없고 더한 줄만 있다.
- 더한 곳에는 `[알고리즘]` 또는 `[알고리즘 추가]` 주석이 붙어 있어 검색으로 바로 찾을 수 있다.
- 범위는 알고리즘이다. 앱 화면·기능에 맞춘 변경(장바구니 사이즈 경고, 반품·교환 기록 등)은 하지 않았다.
- PostgreSQL 16에서 실행해 오류 없이 만들어지는 것과, 잘못된 값이 제약조건에 걸러지는 것을 확인했다.
- 스키마만 정의했다. C의 `recommend/store.py`(SQLite)를 PostgreSQL로 바꾸는 코드는 포함하지 않았다.

## 한눈에 보기

| 구분 | 표 | 한 줄 설명 |
|---|---|---|
| 기존 표에 컬럼 추가 | `categories` | B의 카테고리 분류와 연결 |
| 기존 표에 컬럼 추가 | `body_infos` | A가 받는 치수 중 원본에 없던 것 |
| 기존 표에 컬럼 추가 | `products` | C가 쓰는 계절 목록과 인기도 |
| 새 표 | `body_profiles` | A의 골격진단 결과 |
| 새 표 | `product_tags` | B가 상품에 붙인 태그 |
| 새 표 | `user_events` | 클릭·찜·구매·반품 기록 (C가 사용) |

## 기존 표에 추가한 컬럼

| 컬럼 | 역할 |
|---|---|
| `categories.algo_category` | 이 카테고리가 B의 5개 분류(top / outer / pants / skirt / dress) 중 무엇인지. 비어 있으면 추천 대상에서 빠진다. 원본 `clothing_type`의 '상의'는 B에서 top과 outer로 나뉘므로 카테고리 행마다 지정한다. 점프수트는 B에 대응이 없어 비워 둔다 |
| `body_infos.height` | A가 받는 키(cm). A는 키와 치수를 한 세트로 받으므로 치수와 같은 행에 둔다 |
| `body_infos.upper_body_length` | A가 받는 상체 길이(cm) |
| `body_infos.leg_length` | A가 받는 다리 길이(cm) |
| `products.seasons` | 상품이 해당하는 계절 목록 (spring / summer / fall / winter 중 0개 이상). C의 트렌드 점수가 읽는다. 빈 목록이면 어느 계절에도 해당하지 않는 것으로 계산한다 |
| `products.popularity` | 0~1 인기도. C의 트렌드 점수 중 인기 부분에 쓴다. 기본 0 |

`seasons`를 따로 만든 이유: 원본 `season`은 '봄/여름/가을/겨울/간절기' 중 한 값이라 사계절 입는 옷을 표현하지 못한다. 원본 `season`은 그대로 두었고 알고리즘은 `seasons`만 읽는다. `season`을 보고 `seasons`를 채울 때는 봄→spring, 여름→summer, 가을→fall, 겨울→winter, 간절기→spring+fall로 옮기면 된다 (간절기를 봄·가을로 보는 것은 가정이다).

## `body_profiles` — A의 골격진단 결과

사용자당 현재 결과는 하나이고, 재진단하면 이전 행은 이력으로 남는다. C의 `users.body` JSON을 대체한다.

| 컬럼 | 역할 |
|---|---|
| `profile_id` | 진단 결과 한 건의 번호 |
| `user_id` | 누구의 결과인지 (`users`) |
| `body_id` | 진단에 쓴 치수 기록 (`body_infos`). 설문만으로 진단했으면 비어 있다 |
| `ratio_s`, `ratio_w`, `ratio_n` | 스트레이트·웨이브·내추럴 각각의 비율. B가 태그 점수를 섞을 때 쓰는 핵심 값. 합은 1 |
| `primary_type` | 비율이 가장 큰 주 타입 (S / W / N) |
| `secondary_type` | 두 번째로 큰 타입 |
| `confidence` | 1위와 2위의 격차. 작을수록 애매한 체형 |
| `is_mixed` | 격차가 0.10 미만이라 혼합형으로 본 경우 true |
| `answers` | 설문 답(q1~q7). 재계산·확인용 |
| `is_current` | 현재 유효한 결과인지. 재진단하면 이전 행을 false로 바꾼다 |
| `created_at`, `updated_at` | 진단한 시각, 수정한 시각 |

## `product_tags` — B가 상품에 붙인 태그

상품을 등록할 때 한 번 태깅해서 저장하고, 추천은 여기 저장된 값만 읽는다. 상품당 한 행.

| 컬럼 | 역할 |
|---|---|
| `product_id` | 어떤 상품의 태그인지 (`products`) |
| `tags` | 점수에 쓰는 태그 전체. 예: `["fit:slim","mat:wool"]` |
| `text_added` | `tags` 중 판매자 양식이 아니라 상품명·설명 글에서 추가로 읽은 태그 |
| `fit_stated` | 표기된 핏 (slim / regular / over / unknown). 태그가 없어도 핏을 읽었으면 추천에 남고, 태그도 없고 unknown이면 추천에서 뺀다 |
| `material_basis` | 소재 태그의 근거. `main` 겉감·몸판 표기, `unlabeled` 부위 표기 없는 혼용률, `unclear` 안감·배색만 있어 겉감을 알 수 없어 소재 태그를 붙이지 않음. 소재를 못 읽었으면 비어 있다 |
| `attrs` | 판매자 등록 양식 입력값 전체. 레귤러처럼 태그가 없는 값도 저장해서, 규칙이 바뀌면 다시 태깅할 수 있게 한다 |
| `source` | 태그를 만든 경로. `direct` 판매자 직접 등록, `manual` 팀이 같은 양식으로 입력, `text` 양식 없이 상품명·설명 글만으로 태깅 |
| `rules_version` | 태그를 만들 때 쓴 규칙(가중치·키워드 JSON) 버전. 규칙이 바뀌었을 때 다시 태깅할 상품을 가려내는 용도. **B 코드는 이 값을 만들지 않으므로** 쓰려면 서버에서 직접 채우고, 안 쓰면 비워 둔다 |
| `tagged_at` | 태깅한 시각 |
| `created_at`, `updated_at` | 행이 만들어진 시각, 수정된 시각 |

## `user_events` — 반응 기록

C가 취향 점수와 재진단 안내를 계산할 때 쓴다. 취향 점수는 이 기록으로 매번 다시 계산하며, 반응별 가중치는 C 코드(`rec_config.py`)에 있고 DB에는 없다.

| 컬럼 | 역할 |
|---|---|
| `event_id` | 반응 한 건의 번호. 순서대로 커지므로 시간순 정렬에도 쓴다 |
| `user_id` | 누가 반응했는지 (`users`) |
| `product_id` | 어떤 상품에 반응했는지 (`products`) |
| `event` | 반응 종류 (click / like / purchase / return) |
| `created_at` | 반응한 시각 |

**`wishlists` · `orders`와 다른 점**

| | `wishlists` | `orders` | `user_events` |
|---|---|---|---|
| 담는 것 | 지금 찜한 상품 (찜을 풀면 행이 사라짐) | 주문 내역과 상태 | 사용자의 모든 반응을 시간순으로 쌓은 기록 (지우지 않음) |
| 클릭 | 없음 | 없음 | 있음 |
| 반품 | 없음 | 없음 (취소완료 상태만 있음) | 있음 |
| 쓰임 | 찜 목록 | 결제·배송 | C의 취향 점수 계산, "최근 반응 20개" 기준 재진단 안내 |

C는 반응의 순서와, 찜을 풀어도 기록이 남는지가 필요하다. 찜 목록과 주문 내역만으로는 클릭과 반품, 반응 순서를 알 수 없어 따로 두었다. 찜·구매가 생길 때 `wishlists`·`orders`와 `user_events`에 함께 기록해야 서로 어긋나지 않는다.

## 연결 정보

A의 입력이 들어가는 곳

| A 입력 | DB 위치 |
|---|---|
| height | `body_infos.height` |
| shoulder_width | `body_infos.shoulder` |
| arm_length | `body_infos.arm_length` |
| upper_body_length | `body_infos.upper_body_length` |
| leg_length | `body_infos.leg_length` |
| 설문 답 q1~q7 | `body_profiles.answers` |

C의 SQLite(`recommend/store.py`)와의 대응

| store.py | PostgreSQL |
|---|---|
| `save_body(user_id, body)` | `body_profiles`에 INSERT (이전 현재 행은 `is_current=false`로) |
| `get_user()`의 body | `body_profiles`에서 `is_current=true`인 행 |
| `add_event(user_id, item_id, event)` | `user_events`에 INSERT |
| `get_user()`의 events | `user_events`를 `event_id` 순으로 조회 |

## 직접 채워야 하는 것

- `categories.algo_category`: 카테고리 데이터를 넣은 뒤 행마다 지정한다. 지정하지 않으면 그 카테고리 상품은 모두 추천에서 빠진다. 하의→pants, 치마→skirt, 원피스→dress는 `clothing_type`을 보고 정할 수 있고, 상의는 이름을 보고 top과 outer로 나눠야 한다.
- `products.popularity`: 컬럼만 만들었다. 주문·찜·클릭 수 등으로 0~1을 만드는 산식은 C가 정해 주기적으로 갱신해야 한다. 갱신하지 않으면 모든 상품이 0이라 인기 점수가 의미가 없다.
- `products.seasons`: 새 상품을 등록할 때 `season`과 함께 채운다 (자동으로 맞춰 주지 않는다).
- `product_tags`: 상품에 태그 행이 없으면 B가 점수를 내지 못해 추천에서 빠진다. 상품을 등록할 때 B로 태깅해서 저장한다.

## 주의

- id 형식: `users.user_id`, `products.product_id`는 정수다. B는 문자열·정수를 모두 받고, C의 API는 문자열만 받는다(`item_id`, `user_id`가 문자열이 아니면 오류). DB의 id 형식은 바꾸지 않았다 (모든 외래키가 걸려 있다). C로 넘길 때 `str(product_id)`로 바꾸면 된다.
- C의 `items.json`(문자열 id 30개, 시험용)과 B의 임시 저장소는 DB 조회로 바뀌어야 상품이 서로 연결된다.
- 여름 DB의 `fabrics`(소재명 하나)로는 B 태그를 만들 수 없어서 판매자 양식 입력값을 `product_tags.attrs`에 따로 저장한다.
