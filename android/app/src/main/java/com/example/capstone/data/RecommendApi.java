package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.capstone.ui.BodyTypeActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =========================
 * 추천 서버(저장소 맨 위 app.py) 연결
 * =========================
 *
 * 홈 피드: POST /api/feed/scroll  → 알고리즘 순서대로 page번째 묶음
 * 사용자 반응: POST /api/feed/event → 클릭·찜을 보내면 다음 추천 순서에 반영
 *
 * 서버 켜기: 저장소 맨 위에서 python app.py
 * 자세한 요청·응답 모양은 recommend/안드로이드_연결가이드.md
 */
public final class RecommendApi {

    // 에뮬레이터에서는 10.0.2.2가 PC를 가리킨다.
    // 실제 휴대폰이면 서버 화면에 찍힌 PC IP로 바꾼다. 예) "http://192.168.0.12:5000"
    public static final String BASE_URL = "http://10.0.2.2:5000";

    public static final String MODE_BODY = "body";        // 토글 ON: 골격 맞춤 추천
    public static final String MODE_GENERAL = "general";  // 토글 OFF: 일반 추천

    public static final int PAGE_SIZE = 10;

    // Supabase(싱가포르)를 쓰면 첫 요청이 느릴 수 있어서 넉넉하게
    private static final int TIMEOUT_MS = 10000;

    private static final int DIAGNOSE_TIMEOUT_MS = 3000;

    private static final String PREF_NAME = "recommend";
    private static final String KEY_USER_ID = "user_id";

    // 찜한 서버 상품 {product_id: {name, brand, price, category}}. 앱을 꺼도 찜 탭에 남게 기기에 저장한다
    // [여기에 DB 입력] 로그인·찜 목록 API가 생기면 서버(wishlists)에서 받아 오게 바꾼다
    private static final String KEY_LIKED = "liked_products";

    // 서버 상품은 product_id마다 Product를 하나만 만든다.
    // 홈을 다시 불러와도 같은 객체를 써서 찜 표시가 유지되고, 찜 탭·상세 화면이 같은 상품을 본다
    private static final Map<String, Product> SERVER_PRODUCTS = new LinkedHashMap<>();

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private RecommendApi() {
    }

    /** 결과는 항상 화면(메인) 스레드에서 불린다. */
    public interface Callback<T> {
        void onSuccess(T result);

        /** code: 서버가 돌려준 HTTP 코드. 서버에 아예 연결하지 못했으면 0 */
        void onError(int code, String message);
    }

    /** 홈 피드 한 묶음 */
    public static class FeedPage {
        public final List<Product> items;
        public final boolean hasMore;
        /** 진단된 1위 체형 S / W / N. 토글 OFF이고 진단 기록이 없으면 빈 문자열 */
        public final String primary;

        FeedPage(List<Product> items, boolean hasMore, String primary) {
            this.items = items;
            this.hasMore = hasMore;
            this.primary = primary;
        }
    }

    // =========================
    // 홈 피드 체형 (대표 체형 / 최근 설문)
    // =========================

    /** 내 체형관리의 대표 체형 → S / W / N. 대표 체형이 없거나 골격 체형이 아니면 null */
    public static String activeBodyType(Context context) {

        BodyProfileStore.Profile active = BodyProfileStore.getActive(context);

        return active == null ? null : typeCode(active.bodyType);
    }

    private static String typeCode(String label) {

        if (label == null) {
            return null;
        }

        switch (label) {
            case "스트레이트":
                return "S";
            case "웨이브":
                return "W";
            case "내추럴":
                return "N";
            default:
                return null;
        }
    }

    /** 골격 맞춤 추천에 쓸 체형 정보가 있는지 (대표 체형 또는 설문) */
    public static boolean hasBodyInput(Context context) {

        return activeBodyType(context) != null
                || BodyTypeActivity.lastSurveyAnswers(context) != null;
    }

    /** 홈이 체형이 바뀌었는지 알아챌 때 쓰는 값 (설문 시각 + 대표 체형) */
    public static String bodyInputKey(Context context) {

        return BodyTypeActivity.lastSurveyTime(context) + "/" + activeBodyType(context);
    }

    // =========================
    // 홈 피드 (무한 스크롤)
    // =========================

    /**
     * 대표 체형(BodyProfileStore)에서 서버 A 파트가 받는 치수(cm)를 꺼낸다.
     * height·shoulder_width·arm_length·upper_body_length·leg_length 5개가 모두 있어야 하고,
     * 가슴두께(chest_depth)와 어깨 경사각(shoulder_slope)은 있으면 같이 보낸다. 없으면 null.
     * 지금은 쓰지 않는다 (팀 결정 10/24: 체형 진단은 설문만). 치수를 다시 쓰기로 하면 loadFeed·diagnose에 넘긴다
     */
    public static JSONObject cameraMeasurements(Context context) {

        BodyProfileStore.Profile profile = BodyProfileStore.getActive(context);

        if (profile == null) {
            return null;
        }

        float shoulder = profile.part("SHOULDER");
        float arm = profile.part("ARM_LENGTH");
        float torso = profile.part("BACK_LENGTH");
        float leg = profile.part("LEG_LENGTH");

        if (profile.heightCm <= 0f || shoulder <= 0f || arm <= 0f || torso <= 0f || leg <= 0f) {
            return null;
        }

        try {
            JSONObject m = new JSONObject();
            m.put("height", profile.heightCm);
            m.put("shoulder_width", shoulder);
            m.put("arm_length", arm);
            m.put("upper_body_length", torso);
            m.put("leg_length", leg);

            float chestDepth = profile.part("CHEST_DEPTH");
            float slope = profile.part("SHOULDER_SLOPE");

            if (chestDepth > 0f) {
                m.put("chest_depth", chestDepth);
            }

            if (slope > 0f) {
                m.put("shoulder_slope", slope);
            }

            return m;

        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * 홈 피드 다음 묶음.
     * shown: 지금 화면에 이미 있는 서버 상품 product_id (위에서부터 순서대로).
     * 서버는 이 상품들을 그 순서로 고정하고 그 뒤를 이어 준다
     * → 스크롤 도중 클릭·찜으로 추천 순서가 바뀌어도 같은 상품이 두 번 나오거나 빠지지 않는다.
     */
    public static void loadFeed(
            Context context,
            String mode,
            int page,
            List<String> shown,
            Callback<FeedPage> callback
    ) {
        JSONObject body = new JSONObject();

        try {
            body.put("user_id", userId(context));
            body.put("mode", mode);
            body.put("page", page);
            body.put("size", PAGE_SIZE);
            body.put("shown", new JSONArray(shown));

            // 설문 답을 매번 같이 보낸다.
            // 서버가 메모리 저장소로 돌 때 서버를 다시 켜도 체형을 바로 다시 진단할 수 있다.
            // 단, 내 체형관리에 대표 체형이 있으면 항상 대표 체형을 따른다.
            int[] answers = BodyTypeActivity.lastSurveyAnswers(context);
            String activeType = activeBodyType(context);

            if (activeType != null) {

                JSONObject ratio = new JSONObject();
                ratio.put(activeType, 1.0);

                JSONObject bodyJson = new JSONObject();
                bodyJson.put("ratio", ratio);

                body.put("body", bodyJson);

            } else if (answers != null) {
                JSONObject answerJson = new JSONObject();

                for (int i = 0; i < answers.length; i++) {
                    answerJson.put("q" + (i + 1), answers[i]);
                }

                body.put("answers", answerJson);

                // 카메라 치수는 보내지 않는다 (팀 결정: 체형 진단은 설문만. 서버 rec_config.DIAGNOSIS_USE_MEASUREMENTS)
            }
        } catch (JSONException e) {
            callback.onError(0, "요청을 만들지 못했어요");
            return;
        }

        Context appContext = context.getApplicationContext();

        post("/api/feed/scroll", body, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                try {
                    callback.onSuccess(parsePage(appContext, result));
                } catch (JSONException e) {
                    callback.onError(0, "서버 응답을 읽지 못했어요");
                }
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }

    private static FeedPage parsePage(Context context, JSONObject result) throws JSONException {

        JSONArray array = result.getJSONArray("items");
        List<Product> items = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            items.add(toProduct(context, array.getJSONObject(i)));
        }

        JSONObject body = result.optJSONObject("body");
        String primary = body == null ? "" : body.optString("primary", "");

        return new FeedPage(items, result.optBoolean("has_more", false), primary);
    }

    /**
     * 서버 상품 → 앱 Product.
     * 가격·브랜드는 서버가 DB(Supabase)를 쓸 때만 온다. 없으면 브랜드 자리에 종류, 가격은 0(카드에서 숨김)
     */
    private static Product toProduct(Context context, JSONObject item) throws JSONException {

        String serverId = item.getString("product_id");
        String category = categoryName(item.optString("clothing_type"));
        Product product = SERVER_PRODUCTS.get(serverId);

        if (product == null) {
            String brand = item.optString("brand", "");

            // 서버 상품은 DummyData와 번호가 겹치지 않게 id를 -1로 둔다 (상세 화면은 이름만 보여 준다)
            product = new Product(
                    -1,
                    brand.isEmpty() ? category : brand,
                    item.getString("product_name"),
                    item.optInt("price", 0)
            );
            product.setLiked(likedJson(context).has(serverId));
            SERVER_PRODUCTS.put(serverId, product);
        }

        List<String> seasons = new ArrayList<>();
        JSONArray seasonArray = item.optJSONArray("seasons");

        if (seasonArray != null) {
            for (int i = 0; i < seasonArray.length(); i++) {
                seasons.add(seasonArray.getString(i));
            }
        }

        product.setFilterInfo(category, null, null, seasons.toArray(new String[0]));
        // 추천 이유는 토글(ON/OFF)마다 달라서 받을 때마다 새로 넣는다
        product.setServerInfo(serverId, item.optString("reason", ""));

        return product;
    }

    /** 서버 clothing_type → 앱 상단 메뉴 이름 */
    private static String categoryName(String clothingType) {

        switch (clothingType) {
            case "top":
                return "상의";
            case "outer":
                return "아우터";
            case "pants":
                return "바지";
            case "skirt":
                return "치마";
            case "dress":
                return "원피스";
            default:
                return "기타";
        }
    }

    public static void finalBodyType(
            Context context,
            float heightCm,
            float shoulderCm,
            float armCm,
            float upperBodyCm,
            float legCm,
            Callback<String> callback
    ) {
        int[] answers = BodyTypeActivity.lastSurveyAnswers(context);

        if (answers == null) {
            callback.onError(0, "설문 기록이 없어요");
            return;
        }

        JSONObject body = new JSONObject();

        try {
            JSONObject answerJson = new JSONObject();

            for (int i = 0; i < answers.length; i++) {
                answerJson.put("q" + (i + 1), answers[i]);
            }

            // 치수(heightCm 등)는 받지만 보내지 않는다 (팀 결정: 체형 진단은 설문만).
            // 다시 쓰기로 하면 여기서 measurements를 같이 보내고 서버 DIAGNOSIS_USE_MEASUREMENTS를 켠다
            body.put("answers", answerJson);
        } catch (JSONException e) {
            callback.onError(0, "요청을 만들지 못했어요");
            return;
        }

        // 설문 결과 화면과 같은 서버 진단 (치수가 이상하면 서버가 설문만으로 진단한다)
        post("/api/diagnose", body, DIAGNOSE_TIMEOUT_MS, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                String primary = result.optString("primary", "");

                if (primary.isEmpty()) {
                    callback.onError(0, "서버 응답을 읽지 못했어요");
                } else {
                    callback.onSuccess(primary);
                }
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }

    // =========================
    // 설문 결과 화면: 서버 체형 진단
    // =========================

    /** 서버(A 파트) 체형 진단 결과. 체형 채점은 서버 한 곳에서만 한다 (앱에는 채점 코드 없음) */
    public static class Diagnosis {
        public final String primary;   // S / W / N
        public final float ratioS;
        public final float ratioW;
        public final float ratioN;
        public final boolean mixed;    // 1·2위 차이가 작아 두 체형 특징이 섞임
        public final String notice;    // 치수를 못 써서 설문만으로 진단했을 때 안내 (없으면 빈 문자열)

        Diagnosis(String primary, float s, float w, float n, boolean mixed, String notice) {
            this.primary = primary;
            this.ratioS = s;
            this.ratioW = w;
            this.ratioN = n;
            this.mixed = mixed;
            this.notice = notice;
        }
    }

    /**
     * POST /api/diagnose — 설문 답(+ 있으면 카메라 치수)으로 체형을 진단한다. 서버에 저장하지는 않는다.
     * 홈 추천(/api/feed/scroll)과 같은 계산이라 결과 화면과 추천 체형이 어긋나지 않는다.
     */
    public static void diagnose(
            Context context,
            int[] answers,
            JSONObject measurements,
            Callback<Diagnosis> callback
    ) {
        JSONObject body = new JSONObject();

        try {
            JSONObject answerJson = new JSONObject();

            for (int i = 0; i < answers.length; i++) {
                answerJson.put("q" + (i + 1), answers[i]);
            }

            body.put("answers", answerJson);

            if (measurements != null) {
                body.put("measurements", measurements);
            }
        } catch (JSONException e) {
            callback.onError(0, "요청을 만들지 못했어요");
            return;
        }

        post("/api/diagnose", body, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONObject ratios = result.optJSONObject("ratios");
                String primary = result.optString("primary", "");

                if (ratios == null || primary.isEmpty()) {
                    callback.onError(0, "서버 응답을 읽지 못했어요");
                    return;
                }

                callback.onSuccess(new Diagnosis(
                        primary,
                        (float) ratios.optDouble("S", 0),
                        (float) ratios.optDouble("W", 0),
                        (float) ratios.optDouble("N", 0),
                        result.optBoolean("is_mixed", false),
                        result.optString("notice", "")
                ));
            }

            @Override
            public void onError(int code, String message) {
                callback.onError(code, message);
            }
        });
    }

    // =========================
    // 찜 (서버 상품)
    // =========================

    /**
     * 하트를 누른 뒤 부른다 (product.isLiked()가 바뀐 상태).
     * 서버 상품이면 찜 상태를 기기에 저장하고, 찜했을 때는 추천 서버에 like를 보낸다 (찜 해제는 서버에 안 보냄).
     * DummyData 상품이면 아무것도 하지 않는다 (기존 동작 그대로).
     */
    public static void saveLike(Context context, Product product) {

        if (product == null || !product.isFromServer()) {
            return;
        }

        JSONObject liked = likedJson(context);

        try {
            if (product.isLiked()) {
                JSONObject info = new JSONObject();
                info.put("name", product.getName());
                info.put("brand", product.getBrand());
                info.put("price", product.getPrice());
                info.put("category", product.getCategory());
                liked.put(product.getServerId(), info);
            } else {
                liked.remove(product.getServerId());
            }
        } catch (JSONException e) {
            return;
        }

        prefs(context).edit().putString(KEY_LIKED, liked.toString()).apply();

        if (product.isLiked()) {
            sendEvent(context, product, "like");
        }
    }

    /** 찜한 서버 상품 목록 (찜 탭용). 홈에서 아직 안 불러온 상품은 저장해 둔 정보로 만든다 */
    public static List<Product> likedServerProducts(Context context) {

        JSONObject liked = likedJson(context);
        List<Product> result = new ArrayList<>();

        for (Iterator<String> it = liked.keys(); it.hasNext(); ) {
            String serverId = it.next();
            Product product = SERVER_PRODUCTS.get(serverId);

            if (product == null) {
                JSONObject info = liked.optJSONObject(serverId);

                if (info == null) {
                    continue;
                }

                product = new Product(
                        -1,
                        info.optString("brand"),
                        info.optString("name"),
                        info.optInt("price", 0)
                );
                product.setFilterInfo(info.optString("category"), null, null, null);
                product.setServerInfo(serverId, "");
                product.setLiked(true);
                SERVER_PRODUCTS.put(serverId, product);
            }

            if (product.isLiked()) {
                result.add(product);
            }
        }

        return result;
    }

    /** 상세 화면용: 홈·찜 탭에서 본 서버 상품 (없으면 null) */
    public static Product serverProduct(String serverId) {
        return serverId == null ? null : SERVER_PRODUCTS.get(serverId);
    }

    private static JSONObject likedJson(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_LIKED, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // =========================
    // 사용자 반응 (click / like / purchase / return)
    // =========================

    /**
     * 반응을 보내고, 서버가 "체형을 다시 진단해 볼까요?"라고 하면 안내를 띄운다.
     * 서버 상품이 아니거나 서버가 꺼져 있으면 조용히 넘어간다 (쇼핑 화면은 그대로 쓸 수 있게).
     */
    public static void sendEvent(Context context, Product product, String event) {

        if (product == null || !product.isFromServer()) {
            return;
        }

        Context appContext = context.getApplicationContext();
        JSONObject body = new JSONObject();

        try {
            body.put("user_id", userId(appContext));
            body.put("product_id", product.getServerId());
            body.put("event", event);
        } catch (JSONException e) {
            return;
        }

        post("/api/feed/event", body, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                if (result.optBoolean("need_rediagnosis", false)) {
                    Toast.makeText(
                            appContext,
                            result.optString("message"),
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            @Override
            public void onError(int code, String message) {
                // 반응 기록이 실패해도 화면 동작에는 영향이 없다
            }
        });
    }

    // =========================
    // 사용자 구분
    // =========================

    /**
     * 기기마다 한 번 만든 아이디. 같은 아이디면 서버가 찜·클릭 기록을 이어서 쓴다.
     * [여기에 DB 입력] 로그인 회원번호가 생기면 그 값으로 바꾼다.
     */
    public static String userId(Context context) {

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        String id = prefs.getString(KEY_USER_ID, "");

        if (id.isEmpty()) {
            id = "app-" + UUID.randomUUID();
            prefs.edit().putString(KEY_USER_ID, id).apply();
        }

        return id;
    }

    // =========================
    // HTTP (백그라운드 스레드에서 보내고, 결과는 메인 스레드로)
    // =========================

    private static void post(String path, JSONObject body, Callback<JSONObject> callback) {
        post(path, body, TIMEOUT_MS, callback);
    }

    private static void post(String path, JSONObject body, int timeoutMs, Callback<JSONObject> callback) {

        IO.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setConnectTimeout(timeoutMs);
                connection.setReadTimeout(timeoutMs);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream stream = code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                JSONObject json = new JSONObject(readAll(stream));

                if (code < 400) {
                    MAIN.post(() -> callback.onSuccess(json));
                } else {
                    // 서버 오류는 {"ok": false, "error": "..."} 모양
                    String message = json.optString("error", "서버 오류 (" + code + ")");
                    MAIN.post(() -> callback.onError(code, message));
                }

            } catch (IOException | JSONException e) {
                MAIN.post(() -> callback.onError(0, "추천 서버에 연결할 수 없어요"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String readAll(InputStream stream) throws IOException {

        if (stream == null) {
            return "{}";
        }

        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
