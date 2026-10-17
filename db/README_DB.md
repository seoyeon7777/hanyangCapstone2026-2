# DB (PostgreSQL)

여름방학에 만든 앱 전체 DB에 추천 알고리즘이 쓰는 표를 더한 것.

| 파일 | 내용 |
|---|---|
| `schema_summer.sql` | 여름방학 DB 원본 (수정 없음) |
| `migration_algo.sql` | 알고리즘용 변경. 원본을 실행한 뒤 이어서 실행 |

```
psql -d 데이터베이스 -f db/schema_summer.sql
psql -d 데이터베이스 -f db/migration_algo.sql
```

원본 표는 지우거나 이름을 바꾸지 않았고, 컬럼 추가와 새 표만 있다. PostgreSQL 16에서 두 파일을 차례로 실행해 오류 없이 만들어지는 것과 제약조건이 동작하는 것을 확인했다.

## 변경 내용

| 대상 | 변경 | 이유 |
|---|---|---|
| `categories` | `algo_category` 추가 (top/outer/pants/skirt/dress, NULL이면 추천 제외) | B는 상의를 top과 outer로 나눈다. 원본 `clothing_type`은 '상의' 하나라 카테고리 행마다 따로 지정해야 한다. 점프수트는 B가 지원하지 않는다 |
| `body_infos` | `upper_body_length`, `leg_length` 추가 | A의 입력 치수(상체길이·다리길이)가 원본에 없었다 |
| `body_profiles` (신규) | A의 `classify()` 결과: S/W/N 비율, primary, secondary, confidence, is_mixed, 설문 답 | C의 `users.body` JSON을 대체. 사용자당 현재 결과 하나, 재진단하면 이전 행은 이력으로 남음 |
| `product_tags` (신규) | B의 태그 결과: tags, text_added, fit_stated, material_basis, 등록 양식 입력값(attrs), source | 등록할 때 한 번 태깅해 저장하고 추천은 이것만 읽는다 |
| `user_events` (신규) | click / like / purchase / return 기록 | C의 `events`를 대체. 취향 점수는 이 기록으로 매번 다시 계산 |

## A 입력 → DB 컬럼

| A 입력 | 위치 |
|---|---|
| height | `users.height` |
| shoulder_width | `body_infos.shoulder` |
| arm_length | `body_infos.arm_length` |
| upper_body_length | `body_infos.upper_body_length` |
| leg_length | `body_infos.leg_length` |

## C의 기존 SQLite(`recommend/store.py`)와 대응

| store.py | PostgreSQL |
|---|---|
| `save_body(user_id, body)` | `body_profiles`에 INSERT (이전 현재 행은 `is_current=false`로) |
| `get_user()`의 body | `body_profiles`에서 `is_current=true` 행 |
| `add_event(user_id, item_id, event)` | `user_events` INSERT |
| `get_user()`의 events | `user_events`를 `event_id` 순으로 조회 |

주의할 점
- 사용자 id는 SQLite에서 문자열이고 여기서는 정수(`users.user_id`)다.
- 상품 id도 `products.product_id`(정수)다. C의 `items.json`(문자열 id)과 B의 `store` dict는 이 표로 바뀌어야 연결된다.
- 이 파일들은 스키마만 정의한다. `store.py`를 PostgreSQL로 바꾸는 코드는 포함하지 않았다.
- 상품 정보에는 소재 혼용률·핏 같은 B용 입력이 필요하다. 여름 DB의 `fabrics`(소재명 하나)만으로는 B 태그를 만들 수 없어서 `product_tags.attrs`에 양식 입력값을 따로 저장한다.
