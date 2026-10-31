package com.example.capstone.login;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.capstone.data.AccountPrefs;

/**
 * 로그인 상태를 기기에 기억해 둔다. (앱을 껐다 켜도 유지)
 *
 * - 로그인 성공 시 login(...) 호출
 * - "마이" 버튼 등에서 isLoggedIn() 으로 로그인 여부 확인
 * - 로그아웃/회원탈퇴 시 logout() 호출
 */
public final class LoginSession {

    private static final String PREF_NAME = "login_session";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";

    private LoginSession() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 로그인 성공 시 호출. 모르는 값은 빈 문자열로 넘긴다. */
    public static void login(Context context, String name, String email, String phone) {
        // [여기에 DB 입력] 로그인 성공 시 서버에서 받은 회원번호/토큰도 함께 저장 (users)
        prefs(context).edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_EMAIL, email == null ? "" : email)
                .putString(KEY_PHONE, phone == null ? "" : phone)
                .apply();
        AccountPrefs.adoptGuest(context);
    }

    public static void login(Context context, String userId, String name, String email, String phone) {
        login(context, name, email, phone);
        prefs(context).edit()
                .putString(KEY_USER_ID, userId == null ? "" : userId)
                .apply();
    }

    /** 프로필 수정 후 바뀐 정보를 반영한다. */
    public static void updateProfile(Context context, String name, String email, String phone) {
        prefs(context).edit()
                .putString(KEY_NAME, name)
                .putString(KEY_EMAIL, email)
                .putString(KEY_PHONE, phone)
                .apply();
    }

    public static void logout(Context context) {
        // [여기에 DB 입력] 서버 쪽 로그인 세션/토큰 만료 처리
        prefs(context).edit().clear().apply();
    }

    public static boolean isLoggedIn(Context context) {
        return prefs(context).getBoolean(KEY_LOGGED_IN, false);
    }

    public static String getUserId(Context context) {
        return prefs(context).getString(KEY_USER_ID, "");
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static String getPhone(Context context) {
        return prefs(context).getString(KEY_PHONE, "");
    }
}
