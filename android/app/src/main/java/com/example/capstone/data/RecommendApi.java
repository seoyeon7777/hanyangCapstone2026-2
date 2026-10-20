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
import java.util.List;
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

    private static final int TIMEOUT_MS = 5000;

    private static final String PREF_NAME = "recommend";
    private static final String KEY_USER_ID = "user_id";

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
    // 홈 피드 (무한 스크롤)
    // =========================

    public static void loadFeed(
            Context context,
            String mode,
            int page,
            Callback<FeedPage> callback
    ) {
        JSONObject body = new JSONObject();

        try {
            body.put("user_id", userId(context));
            body.put("mode", mode);
            body.put("page", page);
            body.put("size", PAGE_SIZE);

            // 설문 답을 매번 같이 보낸다.
            // 서버는 기록을 메모리에만 두므로, 서버를 다시 켜도 체형을 바로 다시 진단할 수 있다.
            int[] answers = BodyTypeActivity.lastSurveyAnswers(context);

            if (answers != null) {
                JSONObject answerJson = new JSONObject();

                for (int i = 0; i < answers.length; i++) {
                    answerJson.put("q" + (i + 1), answers[i]);
                }

                body.put("answers", answerJson);
            }
        } catch (JSONException e) {
            callback.onError(0, "요청을 만들지 못했어요");
            return;
        }

        post("/api/feed/scroll", body, new Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                try {
                    callback.onSuccess(parsePage(result));
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

    private static FeedPage parsePage(JSONObject result) throws JSONException {

        JSONArray array = result.getJSONArray("items");
        List<Product> items = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            items.add(toProduct(array.getJSONObject(i)));
        }

        JSONObject body = result.optJSONObject("body");
        String primary = body == null ? "" : body.optString("primary", "");

        return new FeedPage(items, result.optBoolean("has_more", false), primary);
    }

    /** 서버 상품 → 앱 Product. 서버 상품에는 아직 가격·브랜드·사진이 없다. */
    private static Product toProduct(JSONObject item) throws JSONException {

        String category = categoryName(item.optString("clothing_type"));

        // 서버 상품은 DummyData와 번호가 겹치지 않게 id를 -1로 둔다 (상세 화면은 이름만 보여 준다)
        Product product = new Product(
                -1,
                category,
                item.getString("product_name"),
                0
        );

        List<String> seasons = new ArrayList<>();
        JSONArray seasonArray = item.optJSONArray("seasons");

        if (seasonArray != null) {
            for (int i = 0; i < seasonArray.length(); i++) {
                seasons.add(seasonArray.getString(i));
            }
        }

        product.setFilterInfo(category, null, null, seasons.toArray(new String[0]));
        product.setServerInfo(item.getString("product_id"), item.optString("reason", ""));

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

        IO.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
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
