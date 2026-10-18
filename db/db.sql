-- ============================================
-- 테이블 생성
-- ============================================

CREATE TABLE "users" (
	"user_id"	SERIAL		NOT NULL,
	"password"	character varying(255)		NOT NULL,
	"name"	character varying(50)		NOT NULL,
	"gender"	character varying(10)		NULL,
	"birth"	date		NULL,
	"phone"	character varying(20)		NULL,
	"email"	character varying(100)		NULL,
	"address"	character varying(255)		NULL,
	"height"      numeric(5, 2)			NULL,
	"weight"	numeric(5, 2)			NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL,
	"role"	character varying(20)		NOT NULL,
	"deleted_at"	timestamp		NULL
);

CREATE TABLE "products" (
	"product_id"	SERIAL		NOT NULL,
	"brand_id"	integer		NOT NULL,
	"fabric_id"	integer		NOT NULL,
	"category_id"	integer		NOT NULL,
	"product_name"	character varying(200)		NOT NULL,
	"price"	integer		NOT NULL,
	"description"	text		NULL,
	"main_image_url"	text		NULL,
	"status"	character varying(20)	DEFAULT '판매중'	NOT NULL,
	"season"	character varying(20)		NULL,
	"seasons"	text[]	DEFAULT '{}'	NOT NULL,	-- [알고리즘] C 입력: 계절 목록 (spring/summer/fall/winter)
	"popularity"	numeric(4, 3)	DEFAULT 0	NOT NULL,	-- [알고리즘] C 입력: 인기도 0~1
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL,
	"deleted_at"	timestamp		NULL
);

CREATE TABLE "product_options" (
	"option_id"	SERIAL		NOT NULL,
	"product_id"	integer		NOT NULL,
	"size"	character varying(10)		NULL,
	"color"	character varying(30)		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "orders" (
	"order_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"user_coupon_id"	integer		NULL,
	"receiver_name"	character varying(50)		NOT NULL,
	"receiver_phone"	character varying(20)		NOT NULL,
	"shipping_address"	character varying(255)		NOT NULL,
	"order_status"	character varying(30)	DEFAULT '결제대기'	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp		NULL
);

CREATE TABLE "order_items" (
	"order_item_id"	SERIAL	NOT NULL,
	"order_id"	integer		NOT NULL,
	"option_id"	integer		NOT NULL,
	"option_name"	character varying(30)		NULL,
	"product_name"	character varying(200)		NOT NULL,
	"quantity"	integer		NOT NULL,
	"price"	integer		NOT NULL
);

CREATE TABLE "carts" (
	"cart_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"option_id"	integer		NOT NULL,
	"quantity"	integer		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp		NOT NULL
);

CREATE TABLE "body_infos" (
	"body_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"shoulder"	numeric(5, 2)		NULL,
	"chest"	numeric(5, 2)		NULL,
	"waist"	numeric(5, 2)		NULL,
	"arm_length"	numeric(5, 2)		NULL,
	"total_length"	numeric(5, 2)		NULL,
	"height"	numeric(5, 2)		NULL,	-- [알고리즘] A 입력: 키(cm)
	"upper_body_length"	numeric(5, 2)		NULL,	-- [알고리즘] A 입력: 상체 길이(cm)
	"leg_length"	numeric(5, 2)		NULL,	-- [알고리즘] A 입력: 다리 길이(cm)
	"is_default"	boolean	DEFAULT false	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "fabrics" (
	"fabric_id"	SERIAL					NOT NULL,
	"fabric_name"	character varying(100)		NOT NULL,
	"description"	text		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "fitting_results" (
	"fitting_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"option_id"	integer		NOT NULL,
	"body_id"	integer		NULL,
	"shape_key_json"	jsonb		NULL,
	"image_url"	text		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "delivery_items" (
	"delivery_item_id"	SERIAL	NOT NULL,
	"delivery_id"	integer		NOT NULL,
	"order_item_id"	integer		NOT NULL,
	"order_id"	integer		NOT NULL,
	"quantity"	integer		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "delivery" (
	"delivery_id"	SERIAL		NOT NULL,
	"order_id"	integer		NOT NULL,
	"courier"	character varying(50)		NULL,
	"tracking_number"	character varying(50)		NULL,
	"delivery_status"	character varying(50)		NOT NULL,
	"shipped_at"	timestamp		NULL,
	"delivered_at"	timestamp		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "delivery_status_history" (
	"history_id"	SERIAL		NOT NULL,
	"delivery_id"	integer		NOT NULL,
	"status"	character varying(50)		NOT NULL,
	"admin_id"	integer		NULL,
	"changed_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "payments" (
	"payment_id"	SERIAL	NOT NULL,
	"order_id"	integer		NOT NULL,
	"price"	integer		NOT NULL,
	"payment_method"	character varying(30)		NOT NULL,
	"payment_status"	character varying(30)		NOT NULL,
	"refund_status"	character varying(30)		NOT NULL,
	"paid_at"	timestamp		NULL
);

CREATE TABLE "brands" (
	"brand_id"	SERIAL		NOT NULL,
	"brand_name"	character varying(100)		NOT NULL,
	"created_at"	timestamp		NOT NULL,
	"updated_at"	timestamp		NOT NULL
);

CREATE TABLE "categories" (
	"category_id"	SERIAL				NOT NULL,
	"category_name"	character varying(50)		NOT NULL,
	"clothing_type"	character varying(20)		NOT NULL,
	"algo_category"	character varying(10)		NULL,	-- [알고리즘] B의 카테고리 분류. 비어 있으면 추천 제외
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "top_measurements" (
	"product_id"	integer		NOT NULL,
	"shoulder"	numeric(5,2)		NULL,
	"chest"	            numeric(5,2)		NULL,
	"arm_length"	numeric(5,2)		NULL,
	"total_length"	numeric(5,2)		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "bottom_measurements" (
	"product_id"	integer		NOT NULL,
	"waist"	numeric(5,2)		NULL,
	"thigh"	numeric(5,2)		NULL,
	"total_length"	numeric(5,2)		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "skirt_measurements" (
	"product_id"	integer		NOT NULL,
	"waist"	numeric(5,2)		NULL,
	"hip"	numeric(5,2)		NULL,
	"total_length"	numeric(5,2)		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "onepiece_measurements" (
	"product_id"	integer		NOT NULL,
	"shoulder"	numeric(5,2)		NULL,
	"chest"	numeric(5,2)		NULL,
	"waist"	numeric(5,2)		NULL,
	"hip"	numeric(5,2)		NULL,
	"arm_length"	numeric(5,2)		NULL,
	"total_length"	numeric(5,2)		NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "discounts" (
	"discount_id"	SERIAL	NOT NULL,
	"product_id"	integer		NOT NULL,
	"discount_product_name"	character varying(200)		NOT NULL,
	"discount_rate"	numeric(5,2)		NOT NULL,
	"minimum_price"	integer		NULL,
	"start_date"	date		NOT NULL,
	"end_date"	date		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "inventory" (
	"inventory_id"	SERIAL	NOT NULL,
	"option_id"	integer		NOT NULL,
	"stock"	integer	DEFAULT 0	NOT NULL,
	"restock_available"	boolean	DEFAULT false	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "reviews" (
	"review_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"product_id"	integer		NOT NULL,
	"order_item_id"	integer		NOT NULL,
	"review_title"	character varying(100)		NOT NULL,
	"review_content"	text		NOT NULL,
	"rating"	integer		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "review_replies" (
	"reply_id"	SERIAL		NOT NULL,
	"review_id"	integer		NOT NULL,
	"admin_id"	integer		NOT NULL,
	"reply_content"	text		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);

CREATE TABLE "review_images" (
	"review_image_id"	SERIAL	NOT NULL,
	"review_id"	integer		NOT NULL,
	"review_image"	text		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp		NOT NULL
);

CREATE TABLE "inquiries" (
	"inquiry_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"product_id"	integer		NOT NULL,
	"inquiry_type"	character varying(50)		NOT NULL,
	"inquiry_title"	character varying(100)		NOT NULL,
	"inquiry_content"	text		NOT NULL,
	"is_secret"	boolean	DEFAULT false	NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp		NOT NULL
);

CREATE TABLE "inquiry_replies" (
	"reply_id"	SERIAL		NOT NULL,
	"inquiry_id"	integer		NOT NULL,
	"admin_id"	integer		NOT NULL,
	"reply_title"	character varying(100)		NULL,
	"reply_content"	text		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp		NOT NULL
);

CREATE TABLE "coupons" (
	"coupon_id"	SERIAL					NOT NULL,
	"coupon_name"	character varying(100)		NOT NULL,
	"discount_type"	character varying(20)		NOT NULL,
	"discount_value"	numeric(10,2)		NOT NULL,
	"minimum_price"	integer		NULL,
	"maximum_discount_price"	integer		NULL,
	"start_date"	timestamp		NOT NULL,
	"end_date"	timestamp		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);


CREATE TABLE "user_coupons" (
	"user_coupon_id"	SERIAL	NOT NULL,
	"user_id"	integer		NOT NULL,
	"coupon_id"	integer		NOT NULL,
	"is_used"	boolean	DEFAULT false	NOT NULL,
	"issued_at"	timestamp	DEFAULT now()	NOT NULL,
	"used_at"	timestamp		NULL
);

CREATE TABLE "wishlists" (
	"wishlist_id"	SERIAL		NOT NULL,
	"user_id"	integer		NOT NULL,
	"product_id"	integer		NOT NULL,
	"created_at"	timestamp	DEFAULT now()	NOT NULL,
	"updated_at"	timestamp	DEFAULT now()	NOT NULL
);



-- ============================================
-- 테이블 제약조건
-- ============================================

ALTER TABLE "users" ADD CONSTRAINT "PK_USERS" PRIMARY KEY (
	"user_id"
);
CREATE UNIQUE INDEX UQ_users_email_active ON users (email) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX UQ_users_phone_active ON users (phone) WHERE deleted_at IS NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'user';
ALTER TABLE users ADD CONSTRAINT CK_users_role
	CHECK (role IN ('user','admin','USER','ADMIN'));
ALTER TABLE users ADD CONSTRAINT CK_users_gender CHECK (gender IN ('M','F','기타'));


ALTER TABLE "products" ADD CONSTRAINT "PK_PRODUCTS" PRIMARY KEY (
	"product_id"
);
ALTER TABLE products ADD CONSTRAINT CK_products_price CHECK (price >= 0);
ALTER TABLE products ADD CONSTRAINT CK_products_status
	CHECK (status IN ('판매중','품절','판매종료'));
ALTER TABLE products ADD CONSTRAINT CK_products_season
	CHECK (season IN ('봄','여름','가을','겨울','간절기'));
-- [알고리즘] 계절 목록과 인기도의 허용 값
ALTER TABLE products ADD CONSTRAINT CK_products_seasons
	CHECK (seasons <@ ARRAY['spring','summer','fall','winter']::text[]);
ALTER TABLE products ADD CONSTRAINT CK_products_popularity CHECK (popularity >= 0 AND popularity <= 1);


ALTER TABLE "product_options" ADD CONSTRAINT "PK_PRODUCT_OPTIONS" PRIMARY KEY (
	"option_id"
);
ALTER TABLE product_options ADD CONSTRAINT UQ_options_product_size_color UNIQUE (product_id, size, color);
ALTER TABLE product_options ADD CONSTRAINT CK_options_not_both_null
	CHECK (size IS NOT NULL OR color IS NOT NULL);
CREATE UNIQUE INDEX UQ_options_null_color ON product_options (product_id, size) WHERE color IS NULL;

CREATE UNIQUE INDEX UQ_options_null_size ON product_options (product_id, color) WHERE size IS NULL;


ALTER TABLE "orders" ADD CONSTRAINT "PK_ORDERS" PRIMARY KEY (
	"order_id"
);
ALTER TABLE orders ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE orders ADD CONSTRAINT CK_orders_status
	CHECK (order_status IN ('결제대기','상품준비중','배송중','구매확정','취소완료'));

ALTER TABLE "order_items" ADD CONSTRAINT "PK_ORDER_ITEMS" PRIMARY KEY (
	"order_item_id"
);
ALTER TABLE order_items ADD CONSTRAINT CK_orderitems_qty CHECK (quantity > 0);
ALTER TABLE order_items ADD CONSTRAINT CK_orderitems_price CHECK (price >= 0);
ALTER TABLE order_items ADD CONSTRAINT UQ_orderitem_id_order UNIQUE (order_item_id, order_id);

ALTER TABLE "carts" ADD CONSTRAINT "PK_CARTS" PRIMARY KEY (
	"cart_id"
);
ALTER TABLE carts ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE carts ADD CONSTRAINT CK_carts_qty CHECK (quantity > 0);


ALTER TABLE carts ADD CONSTRAINT UQ_carts_user_option UNIQUE (user_id, option_id);

ALTER TABLE "body_infos" ADD CONSTRAINT "PK_BODY_INFOS" PRIMARY KEY (
	"body_id"
);
ALTER TABLE body_infos ADD CONSTRAINT CK_bodyinfo_positive
	CHECK (shoulder > 0 AND chest > 0 AND waist > 0 AND arm_length > 0 AND total_length > 0);
-- [알고리즘] A 입력 치수는 값이 있으면 0보다 커야 한다
ALTER TABLE body_infos ADD CONSTRAINT CK_bodyinfo_algo_positive
	CHECK ((height IS NULL OR height > 0) AND (upper_body_length IS NULL OR upper_body_length > 0) AND (leg_length IS NULL OR leg_length > 0));

CREATE UNIQUE INDEX UQ_bodyinfos_default ON body_infos (user_id) WHERE is_default = true;



ALTER TABLE "fabrics" ADD CONSTRAINT "PK_FABRICS" PRIMARY KEY (
	"fabric_id"
);
ALTER TABLE fabrics ADD CONSTRAINT UQ_fabrics_name UNIQUE (fabric_name);


ALTER TABLE "fitting_results" ADD CONSTRAINT "PK_FITTING_RESULTS" PRIMARY KEY (
	"fitting_id"
);


ALTER TABLE "delivery" ADD CONSTRAINT "PK_DELIVERY" PRIMARY KEY (
	"delivery_id"
);
ALTER TABLE delivery ADD CONSTRAINT CK_delivery_status
	CHECK (delivery_status IN ('배송준비','배송중','배송완료','배송지연'));
ALTER TABLE delivery ADD CONSTRAINT UQ_delivery_id_order UNIQUE (delivery_id, order_id);

ALTER TABLE "delivery_status_history" ADD CONSTRAINT "PK_DELIVERY_STATUS_HISTORY" PRIMARY KEY (
	"history_id"
);
ALTER TABLE delivery_status_history ADD CONSTRAINT CK_delivery_status_history
	CHECK (status IN ('출고시작','집하장도착','배송지도착','배송중','수령인 받음'));

ALTER TABLE "delivery_items" ADD CONSTRAINT "PK_DELIVERY_ITEMS" PRIMARY KEY (
	"delivery_item_id"
);
ALTER TABLE delivery_items ADD CONSTRAINT CK_delivery_items_qty CHECK (quantity > 0);
ALTER TABLE delivery_items ADD CONSTRAINT FK_deliveryitems_delivery
	FOREIGN KEY (delivery_id, order_id) REFERENCES delivery (delivery_id, order_id);
ALTER TABLE delivery_items ADD CONSTRAINT FK_deliveryitems_orderitem
	FOREIGN KEY (order_item_id, order_id) REFERENCES order_items (order_item_id, order_id);

ALTER TABLE "delivery_status_history" ADD CONSTRAINT "FK_delivery_status_history_admin" FOREIGN KEY ("admin_id") REFERENCES "users" ("user_id");

ALTER TABLE "payments" ADD CONSTRAINT "PK_PAYMENTS" PRIMARY KEY (
	"payment_id"
);
ALTER TABLE payments ADD CONSTRAINT CK_payments_status
	CHECK (payment_status IN ('결제대기','결제완료','결제실패','취소'));
ALTER TABLE payments ADD CONSTRAINT CK_payments_refund
	CHECK (refund_status IN ('해당없음','환불대기','환불완료','환불거절'));
ALTER TABLE payments ADD CONSTRAINT CK_payments_price CHECK (price >= 0);
CREATE UNIQUE INDEX UQ_payments_order_completed ON payments (order_id) WHERE payment_status = '결제완료';
ALTER TABLE payments ADD COLUMN created_at timestamp DEFAULT now() NOT NULL;
ALTER TABLE payments ADD COLUMN updated_at timestamp DEFAULT now() NOT NULL;


ALTER TABLE "brands" ADD CONSTRAINT "PK_BRANDS" PRIMARY KEY (
	"brand_id"
);
ALTER TABLE brands ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE brands ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE brands ADD CONSTRAINT UQ_brands_name UNIQUE (brand_name);

ALTER TABLE "categories" ADD CONSTRAINT "PK_CATEGORIES" PRIMARY KEY (
	"category_id"
);
ALTER TABLE categories ADD CONSTRAINT UQ_categories_name UNIQUE (category_name);
ALTER TABLE categories ADD CONSTRAINT CK_categories_clothing_type
	CHECK (clothing_type IN ('상의','하의','치마','원피스','점프수트'));
-- [알고리즘] B의 5개 분류만 허용 (비어 있으면 추천 제외)
ALTER TABLE categories ADD CONSTRAINT CK_categories_algo
	CHECK (algo_category IN ('top','outer','pants','skirt','dress'));

ALTER TABLE "top_measurements" ADD CONSTRAINT "PK_TOP_MEASUREMENTS" PRIMARY KEY (
	"product_id"
);
ALTER TABLE top_measurements ADD CONSTRAINT CK_top_meas_positive
	CHECK (shoulder > 0 AND chest > 0 AND arm_length > 0 AND total_length > 0);

ALTER TABLE "bottom_measurements" ADD CONSTRAINT "PK_BOTTOM_MEASUREMENTS" PRIMARY KEY (
	"product_id"
);
ALTER TABLE bottom_measurements ADD CONSTRAINT CK_bottom_meas_positive
	CHECK (waist > 0 AND thigh > 0 AND total_length > 0);

ALTER TABLE "skirt_measurements" ADD CONSTRAINT "PK_SKIRT_MEASUREMENTS" PRIMARY KEY (
	"product_id"
);
ALTER TABLE skirt_measurements ADD CONSTRAINT CK_skirt_meas_positive
	CHECK (waist > 0 AND hip > 0 AND total_length > 0);

ALTER TABLE "onepiece_measurements" ADD CONSTRAINT "PK_ONEPIECE_MEASUREMENTS" PRIMARY KEY (
	"product_id"
);
ALTER TABLE onepiece_measurements ADD CONSTRAINT CK_onepiece_meas_positive
	CHECK (
		shoulder > 0 AND chest > 0 AND waist > 0 AND arm_length > 0 AND total_length > 0
		AND (hip IS NULL OR hip > 0)
	);

ALTER TABLE "discounts" ADD CONSTRAINT "PK_DISCOUNTS" PRIMARY KEY (
	"discount_id"
);
ALTER TABLE discounts ADD CONSTRAINT CK_discount_rate CHECK (discount_rate > 0 AND discount_rate <= 100);
ALTER TABLE discounts ADD CONSTRAINT CK_discount_minprice CHECK (minimum_price >= 0);
ALTER TABLE discounts ADD CONSTRAINT CK_discount_dates CHECK (end_date >= start_date);

ALTER TABLE "inventory" ADD CONSTRAINT "PK_INVENTORY" PRIMARY KEY (
	"inventory_id"
);
ALTER TABLE inventory ADD CONSTRAINT UQ_inventory_option UNIQUE (option_id);
ALTER TABLE inventory ADD CONSTRAINT CK_inventory_stock CHECK (stock >= 0);

ALTER TABLE "reviews" ADD CONSTRAINT "PK_REVIEWS" PRIMARY KEY (
	"review_id"
);
ALTER TABLE reviews ADD CONSTRAINT CK_reviews_rating CHECK (rating BETWEEN 1 AND 5);


ALTER TABLE reviews ADD CONSTRAINT UQ_reviews_order_item UNIQUE (order_item_id);

ALTER TABLE "review_replies" ADD CONSTRAINT "PK_REVIEW_REPLIES" PRIMARY KEY (
	"reply_id"
);

ALTER TABLE "review_images" ADD CONSTRAINT "PK_REVIEW_IMAGES" PRIMARY KEY (
	"review_image_id"
);
ALTER TABLE review_images ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE "inquiries" ADD CONSTRAINT "PK_INQUIRIES" PRIMARY KEY (
	"inquiry_id"
);
ALTER TABLE inquiries ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE inquiries ADD CONSTRAINT CK_inquiry_type
	CHECK (inquiry_type IN ('상품문의','배송문의','교환반품','기타'));

ALTER TABLE "inquiry_replies" ADD CONSTRAINT "PK_INQUIRY_REPLIES" PRIMARY KEY (
	"reply_id"
);
ALTER TABLE inquiry_replies ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE "coupons" ADD CONSTRAINT "PK_COUPONS" PRIMARY KEY (
	"coupon_id"
);
ALTER TABLE coupons ADD CONSTRAINT CK_coupons_discount_type
	CHECK (discount_type IN ('정액','정률'));
ALTER TABLE coupons ADD CONSTRAINT CK_coupons_discount_value
	CHECK (
		(discount_type = '정률' AND discount_value > 0 AND discount_value <= 100)
		OR (discount_type = '정액' AND discount_value > 0)
	);
ALTER TABLE coupons ADD CONSTRAINT CK_coupons_minprice CHECK (minimum_price >= 0);
ALTER TABLE coupons ADD CONSTRAINT CK_coupons_maxdiscount CHECK (maximum_discount_price >= 0);
ALTER TABLE coupons ADD CONSTRAINT CK_coupons_dates CHECK (end_date >= start_date);

ALTER TABLE "user_coupons" ADD CONSTRAINT "PK_USER_COUPONS" PRIMARY KEY (
	"user_coupon_id"
);
ALTER TABLE user_coupons ADD CONSTRAINT UQ_user_coupons_user_coupon UNIQUE (user_id, coupon_id);
ALTER TABLE user_coupons ADD CONSTRAINT CK_usercoupons_used_consistency
	CHECK ((is_used = false AND used_at IS NULL) OR (is_used = true AND used_at IS NOT NULL));


-- 외래키(FOREIGN KEY) 누락 제약조건 추가
ALTER TABLE "top_measurements" ADD CONSTRAINT "FK_products_TO_top_measurements_1" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");
ALTER TABLE "skirt_measurements" ADD CONSTRAINT "FK_products_TO_skirt_measurements_1" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");
ALTER TABLE "bottom_measurements" ADD CONSTRAINT "FK_products_TO_bottom_measurements_1" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");
ALTER TABLE "onepiece_measurements" ADD CONSTRAINT "FK_products_TO_onepiece_measurements_1" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");

ALTER TABLE "products" ADD CONSTRAINT "FK_products_brand" FOREIGN KEY ("brand_id") REFERENCES "brands" ("brand_id");
ALTER TABLE "products" ADD CONSTRAINT "FK_products_fabric" FOREIGN KEY ("fabric_id") REFERENCES "fabrics" ("fabric_id");
ALTER TABLE "products" ADD CONSTRAINT "FK_products_category" FOREIGN KEY ("category_id") REFERENCES "categories" ("category_id");

ALTER TABLE "product_options" ADD CONSTRAINT "FK_options_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");

ALTER TABLE "orders" ADD CONSTRAINT "FK_orders_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");
ALTER TABLE "orders" ADD CONSTRAINT "FK_orders_user_coupon" FOREIGN KEY ("user_coupon_id") REFERENCES "user_coupons" ("user_coupon_id");
ALTER TABLE orders ADD CONSTRAINT UQ_orders_user_coupon UNIQUE (user_coupon_id);

ALTER TABLE "order_items" ADD CONSTRAINT "FK_orderitems_order" FOREIGN KEY ("order_id") REFERENCES "orders" ("order_id");
ALTER TABLE "order_items" ADD CONSTRAINT "FK_orderitems_option" FOREIGN KEY ("option_id") REFERENCES "product_options" ("option_id");

ALTER TABLE "carts" ADD CONSTRAINT "FK_carts_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "carts" ADD CONSTRAINT "FK_carts_option" FOREIGN KEY ("option_id") REFERENCES "product_options" ("option_id");

ALTER TABLE "body_infos" ADD CONSTRAINT "FK_bodyinfos_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;

ALTER TABLE "fitting_results" ADD CONSTRAINT "FK_fitting_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "fitting_results" ADD CONSTRAINT "FK_fitting_option" FOREIGN KEY ("option_id") REFERENCES "product_options" ("option_id");
ALTER TABLE "fitting_results" ADD CONSTRAINT "FK_fitting_body" FOREIGN KEY ("body_id") REFERENCES "body_infos" ("body_id") ON DELETE SET NULL;

ALTER TABLE "delivery" ADD CONSTRAINT "FK_delivery_order" FOREIGN KEY ("order_id") REFERENCES "orders" ("order_id");
ALTER TABLE "delivery_status_history" ADD CONSTRAINT "FK_delivery_status_history_delivery" FOREIGN KEY ("delivery_id") REFERENCES "delivery" ("delivery_id") ON DELETE CASCADE;

ALTER TABLE "payments" ADD CONSTRAINT "FK_payments_order" FOREIGN KEY ("order_id") REFERENCES "orders" ("order_id");

ALTER TABLE "discounts" ADD CONSTRAINT "FK_discounts_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");

ALTER TABLE "inventory" ADD CONSTRAINT "FK_inventory_option" FOREIGN KEY ("option_id") REFERENCES "product_options" ("option_id");

ALTER TABLE "reviews" ADD CONSTRAINT "FK_reviews_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");
ALTER TABLE "reviews" ADD CONSTRAINT "FK_reviews_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");
ALTER TABLE "reviews" ADD CONSTRAINT "FK_reviews_order_item" FOREIGN KEY ("order_item_id") REFERENCES "order_items" ("order_item_id");

ALTER TABLE "review_replies" ADD CONSTRAINT "FK_review_replies_review" FOREIGN KEY ("review_id") REFERENCES "reviews" ("review_id");
ALTER TABLE "review_replies" ADD CONSTRAINT "FK_review_replies_admin" FOREIGN KEY ("admin_id") REFERENCES "users" ("user_id");

ALTER TABLE "review_images" ADD CONSTRAINT "FK_review_images_review" FOREIGN KEY ("review_id") REFERENCES "reviews" ("review_id");

ALTER TABLE "inquiries" ADD CONSTRAINT "FK_inquiries_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");
ALTER TABLE "inquiries" ADD CONSTRAINT "FK_inquiries_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");

ALTER TABLE "inquiry_replies" ADD CONSTRAINT "FK_inquiry_replies_inquiry" FOREIGN KEY ("inquiry_id") REFERENCES "inquiries" ("inquiry_id");
ALTER TABLE "inquiry_replies" ADD CONSTRAINT "FK_inquiry_replies_admin" FOREIGN KEY ("admin_id") REFERENCES "users" ("user_id");

ALTER TABLE "user_coupons" ADD CONSTRAINT "FK_user_coupons_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id");
ALTER TABLE "user_coupons" ADD CONSTRAINT "FK_user_coupons_coupon" FOREIGN KEY ("coupon_id") REFERENCES "coupons" ("coupon_id");

-- ============================================
-- FK 컬럼 인덱스
-- ============================================
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_products_fabric_id ON products (fabric_id);
CREATE INDEX idx_products_category_id ON products (category_id);

CREATE INDEX idx_product_options_product_id ON product_options (product_id);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_user_coupon_id ON orders (user_coupon_id);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_option_id ON order_items (option_id);

CREATE INDEX idx_carts_user_id ON carts (user_id);
CREATE INDEX idx_carts_option_id ON carts (option_id);

CREATE INDEX idx_body_infos_user_id ON body_infos (user_id);

CREATE INDEX idx_fitting_results_user_id ON fitting_results (user_id);
CREATE INDEX idx_fitting_results_option_id ON fitting_results (option_id);
CREATE INDEX idx_fitting_results_body_id ON fitting_results (body_id);

CREATE INDEX idx_delivery_order_id ON delivery (order_id);
CREATE INDEX idx_delivery_status_history_delivery_id ON delivery_status_history (delivery_id);
CREATE INDEX idx_delivery_status_history_admin_id ON delivery_status_history (admin_id);

CREATE INDEX idx_payments_order_id ON payments (order_id);

CREATE INDEX idx_discounts_product_id ON discounts (product_id);

CREATE INDEX idx_inventory_option_id ON inventory (option_id);

CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_product_id ON reviews (product_id);
CREATE INDEX idx_reviews_order_item_id ON reviews (order_item_id);

CREATE INDEX idx_review_replies_review_id ON review_replies (review_id);
CREATE INDEX idx_review_replies_admin_id ON review_replies (admin_id);

CREATE INDEX idx_review_images_review_id ON review_images (review_id);

CREATE INDEX idx_inquiries_user_id ON inquiries (user_id);
CREATE INDEX idx_inquiries_product_id ON inquiries (product_id);

CREATE INDEX idx_inquiry_replies_inquiry_id ON inquiry_replies (inquiry_id);
CREATE INDEX idx_inquiry_replies_admin_id ON inquiry_replies (admin_id);

CREATE INDEX idx_user_coupons_user_id ON user_coupons (user_id);
CREATE INDEX idx_user_coupons_coupon_id ON user_coupons (coupon_id);


-- ============================================
-- updated_at 자동 갱신 트리거
-- DEFAULT now()는 INSERT 시점에만 채워지고 UPDATE 시 자동 갱신되지 않으므로 트리거로 보완
-- ============================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
	NEW.updated_at = now();
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_options_updated_at BEFORE UPDATE ON product_options
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_carts_updated_at BEFORE UPDATE ON carts
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_body_infos_updated_at BEFORE UPDATE ON body_infos
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_fabrics_updated_at BEFORE UPDATE ON fabrics
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_delivery_updated_at BEFORE UPDATE ON delivery
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_brands_updated_at BEFORE UPDATE ON brands
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON categories
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_top_measurements_updated_at BEFORE UPDATE ON top_measurements
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_bottom_measurements_updated_at BEFORE UPDATE ON bottom_measurements
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_skirt_measurements_updated_at BEFORE UPDATE ON skirt_measurements
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_onepiece_measurements_updated_at BEFORE UPDATE ON onepiece_measurements
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_discounts_updated_at BEFORE UPDATE ON discounts
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_inventory_updated_at BEFORE UPDATE ON inventory
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_reviews_updated_at BEFORE UPDATE ON reviews
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_review_replies_updated_at BEFORE UPDATE ON review_replies
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_review_images_updated_at BEFORE UPDATE ON review_images
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_inquiries_updated_at BEFORE UPDATE ON inquiries
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_inquiry_replies_updated_at BEFORE UPDATE ON inquiry_replies
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_coupons_updated_at BEFORE UPDATE ON coupons
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_delivery_items_updated_at BEFORE UPDATE ON delivery_items
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_wishlists_updated_at BEFORE UPDATE ON wishlists
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_delivery_items_delivery_id ON delivery_items (delivery_id);
CREATE INDEX idx_delivery_items_order_item_id ON delivery_items (order_item_id);
CREATE INDEX idx_delivery_items_order_id ON delivery_items (order_id);


-- ============================================
-- reviews.product_id가 실제 주문상품의 상품과 일치하는지 검증하는 트리거
-- (데이터 정합성은 DB가 최종 방어, 비즈니스 정책 성격의 검증은 백엔드가 담당하는 것으로 확정)
-- ============================================
CREATE OR REPLACE FUNCTION check_review_product()
RETURNS TRIGGER AS $$
DECLARE
	actual_product_id INTEGER;
BEGIN
	SELECT po.product_id INTO actual_product_id
	FROM order_items oi
	JOIN product_options po ON oi.option_id = po.option_id
	WHERE oi.order_item_id = NEW.order_item_id;

	IF actual_product_id IS NULL THEN
		RAISE EXCEPTION '존재하지 않는 주문상품입니다.';
	END IF;

	IF NEW.product_id <> actual_product_id THEN
		RAISE EXCEPTION '리뷰의 product_id가 주문상품의 상품과 일치하지 않습니다.';
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_review_product
	BEFORE INSERT OR UPDATE OF product_id, order_item_id ON reviews
	FOR EACH ROW EXECUTE FUNCTION check_review_product();


-- ============================================
-- 배송수량 합계가 주문수량을 넘지 않도록 검증하는 트리거
-- ============================================
CREATE OR REPLACE FUNCTION check_delivery_quantity()
RETURNS TRIGGER AS $$
DECLARE
	total_qty INTEGER;
	order_qty INTEGER;
BEGIN
	SELECT COALESCE(SUM(quantity), 0) INTO total_qty
	FROM delivery_items
	WHERE order_item_id = NEW.order_item_id
	  AND delivery_item_id <> COALESCE(NEW.delivery_item_id, -1);

	total_qty := total_qty + NEW.quantity;

	SELECT quantity INTO order_qty FROM order_items WHERE order_item_id = NEW.order_item_id;

	IF total_qty > order_qty THEN
		RAISE EXCEPTION '배송 수량 합계(%)가 주문 수량(%)을 초과합니다.', total_qty, order_qty;
	END IF;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_delivery_quantity
	BEFORE INSERT OR UPDATE OF quantity, order_item_id ON delivery_items
	FOR EACH ROW EXECUTE FUNCTION check_delivery_quantity();


-- ============================================
-- delivery_status_history에 세부 단계가 쌓일 때마다
-- delivery.delivery_status(대분류)를 자동으로 동기화하는 트리거
-- (delivery는 배송준비/배송중/배송완료/배송지연 같은 대분류만 허용하고,
--  delivery_status_history는 출고시작/집하장도착/배송지도착/배송중/수령인 받음 같은
--  세부 단계를 허용하므로, 두 테이블의 값을 앱이 수동으로 맞추지 않고
--  이 트리거가 자동으로 매핑해서 항상 일치시킨다)
-- ============================================
CREATE OR REPLACE FUNCTION sync_delivery_status()
RETURNS TRIGGER AS $$
BEGIN
	UPDATE delivery
	SET delivery_status = CASE NEW.status
			WHEN '출고시작' THEN '배송중'
			WHEN '집하장도착' THEN '배송중'
			WHEN '배송지도착' THEN '배송중'
			WHEN '배송중' THEN '배송중'
			WHEN '수령인 받음' THEN '배송완료'
			ELSE delivery_status
		END,
		shipped_at = CASE WHEN NEW.status = '출고시작' AND shipped_at IS NULL THEN NEW.changed_at ELSE shipped_at END,
		delivered_at = CASE WHEN NEW.status = '수령인 받음' THEN NEW.changed_at ELSE delivered_at END
	WHERE delivery_id = NEW.delivery_id;

	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_delivery_status
	AFTER INSERT ON delivery_status_history
	FOR EACH ROW EXECUTE FUNCTION sync_delivery_status();


-- ============================================
-- 찜하기 테이블 제약조건
-- ============================================
ALTER TABLE "wishlists" ADD CONSTRAINT "PK_WISHLISTS" PRIMARY KEY (
	"wishlist_id"
);
ALTER TABLE wishlists ADD CONSTRAINT UQ_wishlists_user_product UNIQUE (user_id, product_id);

ALTER TABLE "wishlists" ADD CONSTRAINT "FK_wishlists_user" FOREIGN KEY ("user_id") REFERENCES "users" ("user_id") ON DELETE CASCADE;
ALTER TABLE "wishlists" ADD CONSTRAINT "FK_wishlists_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id");

CREATE INDEX idx_wishlists_user_id ON wishlists (user_id);
CREATE INDEX idx_wishlists_product_id ON wishlists (product_id);


-- ============================================
-- 추천 알고리즘(A 체형 분류 · B 옷 매칭 · C 추천)용 새 표
-- 위의 [알고리즘] 표시는 기존 표에 더한 컬럼·제약이고, 아래는 새로 만든 표다.
-- ============================================

-- --------------------------------------------
-- [알고리즘 추가] body_profiles : A의 골격진단 결과 (classify의 반환값)
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
-- [알고리즘 추가] product_tags : B가 상품에 붙인 태그 (등록할 때 한 번 계산해 저장, 추천은 이것만 읽는다)
--    tags            점수에 쓰는 태그 전체 (예: ["fit:slim","mat:wool"])
--    text_added      그중 상품명·설명 글에서 추가로 읽은 태그
--    fit_stated      slim | regular | over | unknown (unknown + 태그 없음이면 추천에서 제외)
--    material_basis  main | unlabeled | unclear | NULL
--    attrs           판매자 등록 양식 입력값 전체 (data/attributes.json의 항목)
--    source          direct(판매자 직접 등록) | manual(팀이 같은 양식으로 입력) | text(양식 없이 상품명·설명 글만으로 태깅)
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
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_source CHECK (source IN ('direct','manual','text'));
ALTER TABLE product_tags ADD CONSTRAINT CK_product_tags_tags_array
	CHECK (jsonb_typeof(tags) = 'array' AND jsonb_typeof(text_added) = 'array');
ALTER TABLE "product_tags" ADD CONSTRAINT "FK_product_tags_product" FOREIGN KEY ("product_id") REFERENCES "products" ("product_id") ON DELETE CASCADE;
CREATE INDEX idx_product_tags_tags ON product_tags USING gin (tags);

-- --------------------------------------------
-- [알고리즘 추가] user_events : 클릭·찜·구매·반품 기록 (C의 취향 학습과 재진단 안내에 쓴다)
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
-- [알고리즘 추가] 새 표의 updated_at 자동 갱신 (원본의 set_updated_at() 함수를 그대로 쓴다)
-- --------------------------------------------
CREATE TRIGGER trg_body_profiles_updated_at BEFORE UPDATE ON body_profiles
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_product_tags_updated_at BEFORE UPDATE ON product_tags
	FOR EACH ROW EXECUTE FUNCTION set_updated_at();
