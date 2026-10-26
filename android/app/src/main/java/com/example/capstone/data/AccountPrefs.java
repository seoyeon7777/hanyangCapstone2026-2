package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public final class AccountPrefs {

    public static final String SURVEY_PREF = "avatar_input";
    public static final String SURVEY_ANSWERS = "last_survey_answers";
    public static final String SURVEY_TYPE = "last_survey_type";
    public static final String SURVEY_HEIGHT = "last_survey_height";
    public static final String SURVEY_AT = "last_survey_at";

    public static final String RECOMMEND_PREF = "recommend";
    public static final String RECOMMEND_USER_ID = "user_id";
    public static final String RECOMMEND_LIKED = "liked_products";

    private static final String[] SURVEY_KEYS = {
            SURVEY_ANSWERS, SURVEY_TYPE, SURVEY_HEIGHT, SURVEY_AT
    };

    private static final String[] RECOMMEND_KEYS = {
            RECOMMEND_USER_ID, RECOMMEND_LIKED
    };

    private static final String GUEST = "guest";

    private AccountPrefs() {
    }

    public static SharedPreferences survey(Context context) {
        return open(context, SURVEY_PREF, SURVEY_KEYS);
    }

    public static SharedPreferences recommend(Context context) {
        return open(context, RECOMMEND_PREF, RECOMMEND_KEYS);
    }

    public static String key(Context context, String base) {
        return base + suffix(BodyProfileStore.accountKey(context));
    }

    public static void adoptGuest(Context context) {
        String account = BodyProfileStore.accountKey(context);
        if (GUEST.equals(account)) {
            return;
        }
        adopt(survey(context), SURVEY_KEYS, suffix(GUEST), suffix(account));
        adopt(recommend(context), RECOMMEND_KEYS, suffix(GUEST), suffix(account));
    }

    private static void adopt(SharedPreferences prefs, String[] keys, String from, String to) {
        if (!hasAny(prefs, keys, from) || hasAny(prefs, keys, to)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        copy(prefs, editor, keys, from, to);
        remove(editor, keys, from);
        editor.apply();
    }

    public static void moveAccount(Context context, String fromAccount, String toAccount) {
        if (fromAccount.equals(toAccount)) {
            return;
        }
        move(survey(context), SURVEY_KEYS, suffix(fromAccount), suffix(toAccount));
        move(recommend(context), RECOMMEND_KEYS, suffix(fromAccount), suffix(toAccount));
    }

    private static SharedPreferences open(Context context, String name, String[] keys) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(name, Context.MODE_PRIVATE);
        if (hasAny(prefs, keys, "")) {
            String to = suffix(BodyProfileStore.accountKey(context));
            SharedPreferences.Editor editor = prefs.edit();
            if (!hasAny(prefs, keys, to)) {
                copy(prefs, editor, keys, "", to);
            }
            remove(editor, keys, "");
            editor.commit();
        }
        return prefs;
    }

    private static void move(SharedPreferences prefs, String[] keys, String from, String to) {
        if (!hasAny(prefs, keys, from)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        remove(editor, keys, to);
        copy(prefs, editor, keys, from, to);
        remove(editor, keys, from);
        editor.apply();
    }

    private static String suffix(String account) {
        return "@" + account;
    }

    private static boolean hasAny(SharedPreferences prefs, String[] keys, String suffix) {
        for (String key : keys) {
            if (prefs.contains(key + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static void copy(SharedPreferences prefs, SharedPreferences.Editor editor,
                             String[] keys, String from, String to) {
        Map<String, ?> all = prefs.getAll();
        for (String key : keys) {
            Object value = all.get(key + from);
            if (value instanceof String) {
                editor.putString(key + to, (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(key + to, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key + to, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key + to, (Float) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key + to, (Boolean) value);
            }
        }
    }

    private static void remove(SharedPreferences.Editor editor, String[] keys, String suffix) {
        for (String key : keys) {
            editor.remove(key + suffix);
        }
    }
}
