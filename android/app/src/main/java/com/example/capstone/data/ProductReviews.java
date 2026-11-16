package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ProductReviews {

    public static final String PREF = "capstone_reviews";

    private ProductReviews() {
    }

    public static String key(int productId, String serverId) {
        if (serverId != null && !serverId.isEmpty()) {
            return "reviews_server_" + serverId;
        }
        return "reviews_product_" + productId;
    }

    public static float[] summary(Context context, int productId, String serverId) {
        try {
            JSONArray array = new JSONArray(context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .getString(key(productId, serverId), "[]"));
            float sum = 0f;
            for (int i = 0; i < array.length(); i++) {
                sum += (float) array.getJSONObject(i).optDouble("rating", 0);
            }
            return new float[]{array.length(), array.length() == 0 ? 0f : sum / array.length()};
        } catch (Exception e) {
            return new float[]{0f, 0f};
        }
    }

    static String keyFor(MyPageStore.Order order) {
        if (order == null) {
            return null;
        }
        if (order.serverProductId != null && !order.serverProductId.isEmpty()) {
            return key(-1, order.serverProductId);
        }
        Product product = order.productId >= 0 ? DummyData.getProductById(order.productId) : null;
        if (product == null || product.isFromServer()) {
            product = null;
            for (Product candidate : DummyData.getProducts()) {
                if (candidate.getName() != null && candidate.getName().equals(order.productName)) {
                    product = candidate;
                    break;
                }
            }
        }
        if (product == null) {
            return null;
        }
        return key(product.getId(), product.isFromServer() ? product.getServerId() : null);
    }

    static void put(Context context, MyPageStore.Order order, String reviewKey,
                    float rating, String content, String date, String option) {
        String key = keyFor(order);
        if (context == null || key == null) {
            return;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(prefs.getString(key, "[]"));
            JSONArray next = new JSONArray();

            JSONObject review = new JSONObject();
            review.put("reviewKey", reviewKey);
            review.put("rating", rating);
            review.put("content", content);
            review.put("date", date);
            review.put("option", option);
            next.put(review);

            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.getJSONObject(i);
                if (!reviewKey.equals(item.optString("reviewKey"))) {
                    next.put(item);
                }
            }
            prefs.edit().putString(key, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void remove(Context context, MyPageStore.Order order, String reviewKey) {
        String key = keyFor(order);
        if (context == null || key == null) {
            return;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(prefs.getString(key, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.getJSONObject(i);
                if (!reviewKey.equals(item.optString("reviewKey"))) {
                    next.put(item);
                }
            }
            prefs.edit().putString(key, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
