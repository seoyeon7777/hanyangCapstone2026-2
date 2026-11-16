package com.example.capstone.data;

import android.content.Context;

/**
 * 최근 검색어를 계정마다 따로 저장한다 (SharedPreferences "search_history"의 `recent_terms@계정`).
 * 계정 = BodyProfileStore.accountKey (비로그인은 guest).
 * 계정 이름이 바뀌면(moveAccount) 새 이름으로 옮기고, 탈퇴하면(deleteAccount) 지운다.
 */
public final class SearchHistory {

    public static final String PREF_NAME = "search_history";
    private static final String PREF_KEY = "recent_terms";

    private SearchHistory() {
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String account) {
        return PREF_KEY + "@" + account;
    }

    /** 지금 계정의 저장 키 */
    public static String key(Context context) {
        return key(BodyProfileStore.accountKey(context));
    }

    /** 계정 구분 없이 저장하던 예전 기록은 누구 것인지 알 수 없어 지운다 */
    public static void dropLegacy(Context context) {
        prefs(context).edit().remove(PREF_KEY).apply();
    }

    /** 계정 이름이 바뀌었다(이메일·번호 변경): 기록을 새 이름으로 옮긴다 */
    public static void moveAccount(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }
        android.content.SharedPreferences p = prefs(context);
        String saved = p.getString(key(from), null);
        if (saved == null) {
            return;
        }
        p.edit().putString(key(to), saved).remove(key(from)).apply();
    }

    /** 예전 이름의 기록을, 새 이름에 기록이 없을 때만 옮긴다 */
    public static void adoptAccount(Context context, String from, String to) {
        if (from.equals(to) || prefs(context).contains(key(to))) {
            return;
        }
        moveAccount(context, from, to);
    }

    /** 탈퇴: 그 계정 이름의 기록을 지운다 */
    public static void deleteAccount(Context context, String account) {
        prefs(context).edit().remove(key(account)).apply();
    }
}
