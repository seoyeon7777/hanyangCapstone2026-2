# DB (PostgreSQL)

여름방학에 만든 앱 전체 DB에, 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)이 쓰는 컬럼과 표를 더한 것.

| 파일 | 내용 |
|---|---|
| `schema_summer.sql` | 여름방학 DB 원본 (수정 없음) |
| `migration_algo.sql` | 알고리즘용 추가분. 원본을 실행한 뒤 이어서 실행 |

```
psql -d 데이터베이스 -f db/schema_summer.sql
psql -d 데이터베이스 -f db/migration_algo.sql
```

- 원본의 컬럼·제약조건·트리거는 지우거나 바꾸지 않았다. 기존 표 2개에 빈 값을 허용하는 컬럼을 더했고, 새 표 3개를 만들었다.
- PostgreSQL 16에서 두 파일을 차례로 실행해 오류 없이 만들어지는 것과, 잘못된 값이 제약조건에 걸러지는 것을 확인했다.
- 스키마만 정의했다. C의 `recommend/store.py`(SQLite)를 PostgreSQL로 바꾸는 코드는 포함하지 않았다.

## 한눈에 보기

| 구분 | 표 | 한 줄 설명 |
|---|---|---|
| 기존 표에 컬럼 추가 | `categories` | B의 카테고리와 연결 |
| 기존 표에 컬럼 추가 | `body_infos` | A가 받는 치수 중 원본에 없던 것 |
| 새 표 | `body_profiles` | A의 골격진단 결과 |
| 새 표 | `product_tags` | B가 상품에 붙인 태그 |
| 새 표 | `user_events` | 클릭·찜·구매·반품 기록 (C가 사용) |

## 기존 표에 추가한 컬럼

| 컬럼 | 역할 |
|---|---|
| `categories.algo_category` | 이 카테고리가 B의 5개 분류(top / outer / pants / skirt / dress) 중 무엇인지. 비어 있으면 추천 대상에서 빠진다. 원본 `clothing_type`의 '상의'는 B에서 top과 outer로 나뉘므로 카테고리 행마다 지정한다. 점프수트는 B에 대응이 없어 비워 둔다 |
| `body_infos.upper_body_length` | A가 받는 상체길이(cm) |
| `body_infos.leg_length` | A가 받는 다리길이(cm) |

> 카테고리 데이터를 넣은 뒤 `algo_category`를 직접 채워야 한다 (`migration_algo.sql` 주석에 예시). 채우지 않으면 모든 상품이 추천에서 빠진다.

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
| `source` | 태그를 만든 경로. `direct` 판매자 직접 등록, `manual` 팀이 같은 양식으로 입력 |
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

찜(`wishlists`)·주문(`orders`)과 겹치는 정보지만, 클릭과 반품까지 한곳에 시간순으로 모으려고 따로 두었다.

## 연결 정보

A의 입력 치수가 들어가는 곳

| A 입력 | DB 위치 |
|---|---|
| height | `users.height` |
| shoulder_width | `body_infos.shoulder` |
| arm_length | `body_infos.arm_length` |
| upper_body_length | `body_infos.upper_body_length` |
| leg_length | `body_infos.leg_length` |

C의 SQLite(`recommend/store.py`)와의 대응

| store.py | PostgreSQL |
|---|---|
| `save_body(user_id, body)` | `body_profiles`에 INSERT (이전 현재 행은 `is_current=false`로) |
| `get_user()`의 body | `body_profiles`에서 `is_current=true`인 행 |
| `add_event(user_id, item_id, event)` | `user_events`에 INSERT |
| `get_user()`의 events | `user_events`를 `event_id` 순으로 조회 |

## 주의

- 사용자 id와 상품 id가 SQLite·B·C에서는 문자열이었지만 여기서는 정수(`users.user_id`, `products.product_id`)다. C의 `items.json`과 B의 임시 저장소가 이 표로 바뀌어야 상품이 서로 연결된다.
- 여름 DB의 `fabrics`(소재명 하나)로는 B 태그를 만들 수 없어서 판매자 양식 입력값을 `product_tags.attrs`에 따로 저장한다.
