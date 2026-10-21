# DB(Supabase) 연결 가이드

추천 서버(`python app.py`)가 상품을 DB에서 읽고, 체형·찜·클릭 기록을 DB에 저장하게 하는 방법이에요.
DB 주소를 넣지 않으면 지금처럼 `recommend/data/items.json` + 서버 메모리로 돌아요 (시연은 그대로 가능).

```
안드로이드 앱 → 추천 서버(python app.py, PC) → Supabase(PostgreSQL, 표 모양은 db/db.sql)
```

## 1. Supabase 프로젝트 만들기 (DB 담당이 한 번)

1. https://supabase.com 가입 → **New project**
2. 이름은 자유, **Database Password**는 영어·숫자만으로 정하기 (특수문자가 있으면 접속 주소에서 오류가 나기 쉬워요). 이 비밀번호는 따로 적어 두기
3. Region: **Northeast Asia (Seoul)**
4. 만들어지면 왼쪽 **SQL Editor** → `db/db.sql` 내용을 전부 붙여 넣고 **Run** → 표 32개가 생겨요
5. 같은 SQL Editor에서 아래도 한 번 실행 (보안: Supabase 웹 API로 아무나 표를 읽고 쓰지 못하게 막기. 우리 서버는 DB 계정으로 바로 붙어서 영향 없음)

```sql
DO $$ DECLARE t text; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);
  END LOOP;
END $$;
```

## 2. 접속 주소 받기

1. Supabase 화면 위쪽 **Connect** 버튼
2. **Session pooler**의 주소(URI)를 복사 — `postgresql://postgres.xxxx:[YOUR-PASSWORD]@aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres` 모양
   - `Direct connection`은 IPv6 전용이라 학교·집 와이파이에서 안 될 수 있어요. Session pooler를 쓰세요
3. `[YOUR-PASSWORD]` 자리를 1번에서 정한 비밀번호로 바꾸기 (대괄호도 지우기)

⚠️ 이 주소에는 DB 비밀번호가 들어 있어요. **GitHub·단톡방에 올리지 말고** 서버를 켜는 사람에게만 개인 메시지로 주세요.

## 3. 서버에 주소 넣기 (서버를 켜는 PC마다)

저장소 맨 위 폴더(`app.py`가 있는 곳)에 **`database_url.txt`** 파일을 만들고 2번 주소 한 줄만 넣어요.
이 파일은 `.gitignore`에 들어 있어서 GitHub에 올라가지 않아요.

(환경 변수 `DATABASE_URL`로 넣어도 돼요. 둘 다 있으면 환경 변수가 먼저예요.)

```
pip install -r requirements.txt
python recommend/seed_db.py      ← 처음 한 번: 시연 상품 30개를 DB에 넣기 (여러 번 해도 괜찮아요)
python app.py
```

서버 화면 첫 줄이 `데이터  DB (...) — 서버를 꺼도 기록이 남아요`면 연결된 거예요.
`메모리 + recommend/data/items.json`이라고 나오면 주소 파일을 못 찾은 거예요 (파일 이름·위치 확인).

## 4. 무엇이 어디에 저장되나

| 표 | 추천 서버가 하는 일 |
|---|---|
| `products` + `categories` + `product_tags` (+ `brands`) | 추천 후보로 읽음. **판매중 · 삭제 안 됨 · 태그 있음**만. `jumpsuit`은 추천이 다루지 못해서 뺌. 60초마다 다시 읽음 |
| `users` | 앱이 처음 보낸 `user_id`(예: `app-…`)로 행을 만듦 (이름 "앱 사용자", 비밀번호 빈칸) |
| `body_profiles` | 설문 진단 결과. 같은 결과면 새 행을 안 만들고, 다시 진단해서 바뀌면 이전 행은 `is_current = false`로 남김 |
| `user_events` | 클릭·찜·구매·반품을 시간 순으로 쌓음. 취향 점수는 이 기록으로 매번 다시 계산 (따로 저장 안 함) |

- 앱에 보내는 상품에는 DB의 `price`, `brand`(브랜드 이름), `image_url`(`main_image_url`)도 같이 들어가요
- 실제 상품을 넣을 때: `products` 행 + 그 상품의 `product_tags` 행(B의 `tagging.py`로 만든 태그)이 둘 다 있어야 추천에 나와요
- `products.popularity`(0~1)는 아직 채우는 기능이 없어서 직접 넣어야 해요. 비우면 0

## 5. 문제가 생기면

| 증상 | 확인할 것 |
|---|---|
| 앱·API가 `503`과 "DB에 연결할 수 없어요" | Supabase 프로젝트가 **일시 정지**됐는지 (일주일 안 쓰면 멈춰요 → 대시보드에서 Restore). 주소·비밀번호가 맞는지 |
| 추천 상품이 0개 | `product_tags`가 비었는지, `status`가 '판매중'인지 |
| 발표 당일 | 발표 전에 Supabase에 한 번 들어가 프로젝트가 켜져 있는지 확인 |

## 6. 테스트 (개발자용)

`recommend/test_db.py`는 **로컬 PostgreSQL**에 테스트용 DB를 새로 만들어 확인하고 지워요. Supabase 주소는 넣지 마세요.

```
TEST_DATABASE_URL=postgresql://postgres@127.0.0.1:5432/postgres python recommend/test_db.py
```
