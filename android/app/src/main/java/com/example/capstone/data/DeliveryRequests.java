package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 주문별 배송 요청사항을 계정마다 따로 저장한다 (SharedPreferences "order_delivery_requests"의 `계정|주문번호`).
 * 계정 = BodyProfileStore.accountKey. 계정 이름이 바뀌면(moveAccount) 새 이름으로 옮기고, 탈퇴하면(deleteAccount) 지운다.
 * 예전에는 주문번호만 키로 써서 계정 구분이 없었다 → 처음 읽을 때 지금 계정의 키로 옮긴다(get).
 */
public final class DeliveryRequests {

    public static final String PREF_NAME = "order_delivery_requests";
    private static final String SEP = "|";

    private DeliveryRequests() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String account, String orderNo) {
        return account + SEP + orderNo;
    }

    /** 요청사항을 저장한다. 비어 있으면 저장하지 않는다 */
    public static void save(Context context, String orderNo, String request) {
        if (orderNo == null || request == null || request.trim().isEmpty()) {
            return;
        }
        prefs(context).edit()
                .putString(key(BodyProfileStore.accountKey(context), orderNo), request.trim())
                .apply();
    }

    /** 주문의 요청사항. 없으면 빈 글자 */
    public static String get(Context context, String orderNo) {
        if (orderNo == null) {
            return "";
        }
        SharedPreferences p = prefs(context);
        String key = key(BodyProfileStore.accountKey(context), orderNo);
        String value = p.getString(key, null);
        if (value == null) {
            // 예전 방식(주문번호만 키)으로 저장된 것은 처음 읽을 때 이 계정의 키로 옮긴다
            String legacy = p.getString(orderNo, null);
            if (legacy == null) {
                return "";
            }
            p.edit().putString(key, legacy).remove(orderNo).apply();
            return legacy;
        }
        return value;
    }

    /** 계정 이름이 바뀌었다(이메일·번호 변경): 기록을 새 이름으로 옮긴다 */
    public static void moveAccount(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor editor = p.edit();
        String prefix = from + SEP;
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue() instanceof String) {
                editor.putString(to + SEP + e.getKey().substring(prefix.length()), (String) e.getValue())
                        .remove(e.getKey());
            }
        }
        editor.apply();
    }

    /** 예전 이름의 기록을, 새 이름에 기록이 없을 때만 옮긴다 */
    public static void adoptAccount(Context context, String from, String to) {
        if (from.equals(to) || hasAny(context, to)) {
            return;
        }
        moveAccount(context, from, to);
    }

    private static boolean hasAny(Context context, String account) {
        String prefix = account + SEP;
        for (String k : prefs(context).getAll().keySet()) {
            if (k.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 탈퇴: 그 계정 이름의 기록을 지운다 */
    public static void deleteAccount(Context context, String account) {
        SharedPreferences p = prefs(context);
        List<String> remove = new ArrayList<>();
        String prefix = account + SEP;
        for (String k : p.getAll().keySet()) {
            if (k.startsWith(prefix)) {
                remove.add(k);
            }
        }
        if (remove.isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = p.edit();
        for (String k : remove) {
            editor.remove(k);
        }
        editor.apply();
    }
}
