package com.example.capstone.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

/** 앱에 내장된 상품의 찜을 계정별로 기기에 저장한다. 서버 상품의 찜은 RecommendApi가 저장한다. */
public final class LocalLikes {

    private LocalLikes() {
    }

    public static void save(Context context, Product product) {
        if (product == null || product.isFromServer()) {
            return;
        }
        JSONObject liked = load(context);
        try {
            if (product.isLiked()) {
                liked.put(String.valueOf(product.getId()), true);
            } else {
                liked.remove(String.valueOf(product.getId()));
            }
        } catch (JSONException e) {
            return;
        }
        AccountPrefs.recommend(context).edit()
                .putString(AccountPrefs.key(context, AccountPrefs.RECOMMEND_LIKED_LOCAL), liked.toString())
                .apply();
    }

    /** 내장 상품의 하트를 지금 계정의 찜 기록에 맞춘다. */
    public static void refresh(Context context) {
        JSONObject liked = load(context);
        for (Product product : DummyData.getProducts()) {
            if (!product.isFromServer()) {
                product.setLiked(liked.has(String.valueOf(product.getId())));
            }
        }
    }

    private static JSONObject load(Context context) {
        try {
            return new JSONObject(AccountPrefs.recommend(context)
                    .getString(AccountPrefs.key(context, AccountPrefs.RECOMMEND_LIKED_LOCAL), "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
