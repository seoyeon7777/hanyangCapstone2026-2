package com.example.capstone.data;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 판매자 상품 등록 서버 API (recommend/seller_api.py). 외부 라이브러리 없이 HttpURLConnection + org.json.
 * 백그라운드 스레드에서 보내고 결과는 메인 스레드로 준다. 서버 주소는 추천 API와 같다(RecommendApi.BASE_URL).
 *
 * GET  /api/seller/form              등록 화면 정의
 * POST /api/seller/products/validate 입력 검증·태그 미리보기
 * POST /api/seller/products          상품 등록
 * GET  /api/seller/products?brand=   내 브랜드 상품 목록
 * POST /api/seller/images            사진 올리기 (multipart)
 */
public final class SellerApi {

    public interface Callback {
        void onSuccess(JSONObject result);

        /** message는 사용자에게 그대로 보여 줄 한국어 문장 */
        void onError(String message);
    }

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SellerApi() {
    }

    public static void loadForm(Callback callback) {
        run("GET", "/api/seller/form", null, callback);
    }

    public static void validate(JSONObject product, Callback callback) {
        run("POST", "/api/seller/products/validate", product, callback);
    }

    public static void register(JSONObject product, Callback callback) {
        run("POST", "/api/seller/products", product, callback);
    }

    public static void myProducts(String brand, Callback callback) {
        String query;
        try {
            query = java.net.URLEncoder.encode(brand, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            query = brand;
        }
        run("GET", "/api/seller/products?brand=" + query, null, callback);
    }

    /** 사진(JPEG 바이트)을 올리고 결과 {"url": ...}를 돌려준다. */
    public static void uploadImage(byte[] jpeg, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String boundary = "----seller" + UUID.randomUUID();
                connection = open("POST", "/api/seller/images");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
                            + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(jpeg);
                    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                finish(connection, callback);
            } catch (Exception e) {
                fail(callback, "서버에 연결하지 못했어요. 서버가 켜져 있는지 확인해 주세요.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static void run(String method, String path, JSONObject body, Callback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(method, path);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                finish(connection, callback);
            } catch (Exception e) {
                fail(callback, "서버에 연결하지 못했어요. 서버가 켜져 있는지 확인해 주세요.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static HttpURLConnection open(String method, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RecommendApi.BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static void finish(HttpURLConnection connection, Callback callback) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = stream == null ? "" : read(stream);
        JSONObject json;
        try {
            json = new JSONObject(text);
        } catch (Exception e) {
            fail(callback, "서버 응답을 읽지 못했어요 (" + code + ")");
            return;
        }
        if (code >= 400 || !json.optBoolean("ok", false)) {
            fail(callback, json.optString("error", "요청을 처리하지 못했어요 (" + code + ")"));
            return;
        }
        MAIN.post(() -> callback.onSuccess(json));
    }

    private static String read(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    private static void fail(Callback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }
}
