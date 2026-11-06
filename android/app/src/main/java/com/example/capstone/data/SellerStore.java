package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 상품 등록 화면에서 마지막으로 쓴 브랜드 이름을 이 기기에 기억해, 다음 등록 때 미리 채우고 판매자 센터의 상품 목록을 보여 준다.
 * 판매자 계정이나 권한은 아니다: 서버에 판매자 권한이 없어서(users.role은 user/admin만 허용) 브랜드 이름으로 상품을 가른다.
 * [여기에 DB 입력] 서버에 판매자 권한과 브랜드-계정 연결이 생기면 서버 값으로 바꾼다.
 */
public final class SellerStore {

    private static final String PREF = "seller_profile";
    public static final int BRAND_MIN = 2;
    public static final int BRAND_MAX = 30;

    private SellerStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String key(Context context) {
        return "brand@" + BodyProfileStore.accountKey(context);
    }

    /** 탈퇴: 이 계정이 마지막으로 쓴 브랜드 이름을 지운다 */
    public static void deleteAccount(Context context, String account) {
        prefs(context).edit().remove("brand@" + account).apply();
    }

    /** 마지막으로 쓴 브랜드 이름. 없으면 빈 문자열 */
    public static String getBrand(Context context) {
        return prefs(context).getString(key(context), "");
    }

    public static void saveBrand(Context context, String brand) {
        prefs(context).edit().putString(key(context), brand).apply();
    }

    /** 브랜드 이름 앞뒤 공백을 지우고 연속 공백을 하나로 줄인다. 규칙에 안 맞으면 null. */
    public static String normalizeBrand(String input) {
        if (input == null) {
            return null;
        }
        String brand = input.trim().replaceAll("\\s+", " ");
        return brand.length() < BRAND_MIN || brand.length() > BRAND_MAX ? null : brand;
    }
}
