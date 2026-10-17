-- ============================================
-- 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)용 DB 변경
-- 대상: PostgreSQL / 먼저 여름방학 앱 전체 DB(노션의 erd최종_수정본.sql)를 실행한 뒤 이 파일을 실행한다.
-- 원본 테이블은 지우거나 이름을 바꾸지 않고, 컬럼 추가와 새 테이블만 더한다.
-- ============================================

BEGIN;

-- --------------------------------------------
-- 1. categories : B의 카테고리와 연결
--    B는 top / outer / pants / skirt / dress 다섯 가지만 안다.
--    원본의 clothing_type '상의'는 B에서 top과 outer로 갈리므로 clothing_type이 아니라 카테고리 행마다 정한다.
--    algo_category가 NULL이면 추천에서 제외한다 (예: 점프수트는 B가 지원하지 않는다).
-- --------------------------------------------
ALTER TABLE categories ADD COLUMN algo_category character varying(10) NULL;
ALTER TABLE categories ADD CONSTRAINT CK_categories_algo
	CHECK (algo_category IN ('top','outer','pants','skirt','dress'));
-- 카테고리 데이터를 넣은 뒤 행마다 지정한다. 예:
--   UPDATE categories SET algo_category = 'top'   WHERE category_name IN ('티셔츠','셔츠','니트');
--   UPDATE categories SET algo_category = 'outer' WHERE category_name IN ('자켓','코트','가디건');
--   UPDATE categories SET algo_category = 'pants' WHERE clothing_type = '하의';
--   UPDATE categories SET algo_category = 'skirt' WHERE clothing_type = '치마';
--   UPDATE categories SET algo_category = 'dress' WHERE clothing_type = '원피스';

-- --------------------------------------------
-- 2. body_infos : A의 입력 치수와 맞춘다
--    A 입력      → 이 테이블
--    height      → users.height
--    shoulder_width → shoulder
--    arm_length  → arm_length
--    upper_body_length → upper_body_length (새 컬럼)
--    leg_length  → leg_length (새 컬럼)
--    chest, waist, total_length는 A가 쓰지 않는다 (원본 유지, 옷 치수 비교용으로 남겨 둔다).
-- --------------------------------------------
ALTER TABLE body_infos ADD COLUMN upper_body_length numeric(5, 2) NULL;
ALTER TABLE body_infos ADD COLUMN leg_length numeric(5, 2) NULL;
ALTER TABLE body_infos ADD CONSTRAINT CK_bodyinfo_algo_positive
	CHECK ((upper_body_length IS NULL OR upper_body_length > 0) AND (leg_length IS NULL OR leg_length > 0));

-- --------------------------------------------
-- 3. body_profiles : A의 골격진단 결과 (classify의 반환값)
--    ratios {S, W, N}, primary, secondary, confidence, is_mixed
--    사용자당 현재 결과는 하나(is_current)이고, 재진단하면 이전 행은 false로 바꿔 이력으로 남긴다.
--    (C의 store.py users.body JSON을 대체한다)
-- --------------------------------------------
CREATE TABLE "body_profiles" (
	"profile_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"body_id"	integer		NULL,
	"ratio_s"	numeric(5, 4)		NOT NULL,
	"ratio_w"	numeric(5, 4)		NOT NULL,
	"ratio_n"	numeric(5, 4)		NOT NULL,
	"primary_type"	character(1)		NOT NULL,
	"secondary_type"	character(1)		NULL,
	"confidence"	numeric(5, 4)		NOT NULL,
	"is_mixed"	boolean		NOT NULL,
	"answers"	jsonb		NULL,
	"is_current"	boolean	DEFAULT true	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);
ALTER TABLE "body_profiles" ADD CONSTRAINT "PK_BODY_PROFILES" PRIMARY KEY ("profile_id");
ALTER TABLE body_profiles ADD CONSTRAINT CK_body_profiles_ratio
	CHECK (ratio_s >= 0 AND ratio_w >= 0 AND ratio_n >= 0
		AND abs(ratio_s + ratio_w + ratio_n - 1) <= 0.01);
ALTER TABLE body_profiles ADD CONSTRAINT CK_body_profiles_primary CHECK (primary_type IN ('S','W','N'));
ALTER TABLE body_profiles ADD CONSTRAINT CK_body_profiles_secondary
	CHECK (secondary_type IS NULL OR secondary_type IN ('S','W','N'));
ALTER TABLE body_profiles ADD CONSTRAINT CK_body_profiles_confidence CHECK (confidence >= 0 AND confidence <= 1);
ALTER TABLE "body_profiles" ADD CONSTRAINT "FK_body_profiles_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "body_profiles" ADD CONSTRAINT "FK_body_profiles_body" FOREIGN KEY ("body_id") REFERENCES "body_infos" ("body_id") ON DELETE SET NULL;
CREATE UNIQUE INDEX UQ_body_profiles_current ON body_profiles (user_id) WHERE is_current = true;
CREATE INDEX idx_body_profiles_user_id ON body_profiles (user_id);
CREATE INDEX idx_body_profiles_body_id ON body_profiles (body_id);

-- --------------------------------------------
-- 4. product_tags : B가 상품에 붙인 태그 (등록할 때 한 번 계산해 저장, 추천은 이것만 읽는다)
--    tags            점수에 쓰는 태그 전체 (예: ["fit:slim","mat:wool"])
--    text_added      그중 상품명·설명 글에서 추가로 읽은 태그
--    fit_stated      slim | regular | over | unknown (unknown + 태그 없음이면 추천에서 제외)
--    material_basis  main | unlabeled | unclear | NULL
--    attrs           판매자 등록 양식 입력값 전체 (data/attributes.json의 항목)
--    source          direct(판매자 직접 등록) | manual(팀이 같은 양식으로 입력)
--    rules_version   태그를 만들 때의 규칙 버전 (가중치·키워드 JSON을 바꾸면 다시 태깅할 상품을 가려낼 때 쓴다)
-- --------------------------------------------
CREATE TABLE "product_tags" (
	"product_id"	integer		NOT NULL,
	"tags"	jsonb	DEFAULT '[]'::jsonb	NOT NULL,
	"text_added"	jsonb	DEFAULT '[]'::jsonb	NOT NULL,
	"fit_stated"	character varying(10)	DEFAULT 'unknown'	NOT NULL,
	"material_basis"	character varying(10)		NULL,
	"attrs"	jsonb		NULL,
	"source"	character varying(10)		NOT NULL,
	"rules_version"	character varying(40)		NULL,
	"tagged_at"	timestamp	DEFAULT now()	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);
ALTER TABLE "product_tags" ADD CONSTRAINT "PK_PRODUCT_TAGS" PRIMARY KEY ("product_id");
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_fit CHECK (fit_stated IN ('slim','regular','over','unknown'));
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_basis
	CHECK (material_basis IS NULL OR material_basis IN ('main','unlabeled','unclear'));
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_source CHECK (source IN ('direct','manual'));
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_tags_array
	CHECK (jsonb_typeof(tags) = 'array' AND jsonb_typeof(text_added) = 'array');
ALTER TABLE "product_tags" ADD CONSTRAINT "FK_product_tags_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id") ON DELETE CASCADE;
CREATE INDEX idx_product_tags_tags ON product_tags USING gin (tags);

-- --------------------------------------------
-- 5. user_events : 클릭·찜·구매·반품 기록 (C의 취향 학습과 재진단 안내에 쓴다)
--    click +0.05 / like +0.10 / purchase +0.20 / return -0.20 (가중치는 C의 rec_config.py에 있고 DB에는 저장하지 않는다.
--    취향 점수는 이 기록으로 매번 다시 계산한다)
--    찜(wishlists)·주문(orders)과 겹치는 정보지만, 클릭·반품까지 한곳에 시간 순으로 모으려고 따로 둔다.
-- --------------------------------------------
CREATE TABLE "user_events" (
	"event_id"	BIGSERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"product_id"	integer		NOT NULL,
	"event"	character varying(10)		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL
);
ALTER TABLE "user_events" ADD CONSTRAINT "PK_USER_EVENTS" PRIMARY KEY ("event_id");
ALTER TABLE user_events ADD CONSTRAINT CK_user_events_event CHECK (event IN ('click','like','purchase','return'));
ALTER TABLE "user_events" ADD CONSTRAINT "FK_user_events_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "user_events" ADD CONSTRAINT "FK_user_events_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");
CREATE INDEX idx_user_events_user ON user_events (user_id, event_id);
CREATE INDEX idx_user_events_product_id ON user_events (product_id);

-- --------------------------------------------
-- 6. updated_at 자동 갱신 (원본의 set_updated_at() 함수를 그대로 쓴다)
-- --------------------------------------------
CREATE TRIGGER trg_body_profiles_updated_at BEFORE UPDATE ON body_profiles
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_tags_updated_at BEFORE UPDATE ON product_tags
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMIT;
