package com.example.capstone.data;

import android.content.Context;

/**
 * 최근 검색어를 계정마다 따로 저장한다 (SharedPreferences "search_history"의 `recent_terms@계정`).
 * 계정 = BodyProfileStore.accountKey (비로그인은 guest). 탈퇴하면 그 계정 기록을 지운다.
 */
public final class SearchHistory {

    public static final String PREF_NAME = "search_history";
    private static final String PREF_KEY = "recent_terms";

    private SearchHistory() {
    }

    /** 지금 계정의 저장 키 */
    public static String key(Context context) {
        return PREF_KEY + "@" + BodyProfileStore.accountKey(context);
    }

    /** 계정 구분 없이 저장하던 예전 기록은 누구 것인지 알 수 없어 지운다 */
    public static void dropLegacy(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_KEY)
                .apply();
    }

    /** 탈퇴: 그 계정 이름의 최근 검색어를 지운다 */
    public static void deleteAccount(Context context, String account) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_KEY + "@" + account)
                .apply();
    }
}
