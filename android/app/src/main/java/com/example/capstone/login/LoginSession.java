package com.example.capstone.login;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.ProfilePhotoStore;

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
        String savedPhone = phone == null ? "" : phone;
        String normalized = LocalAccounts.normalizePhone(savedPhone);
        if (normalized != null) {
            savedPhone = normalized;
        }
        prefs(context).edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_EMAIL, email == null ? "" : email)
                .putString(KEY_PHONE, savedPhone)
                .apply();
        adoptOldPhoneData(context, phone);
        // 비회원일 때의 기록은 새로 로그인한 계정으로 가져오지 않는다 (계정마다 따로)
        reloadAccountData(context);
    }

    /**
     * 계정이 바뀌었을 때(로그인·로그아웃·앱 시작): 메모리에 남은 이전 계정의 기록을 버리고
     * 지금 계정의 기록(주문·리뷰·배송지·카드·찜 …)을 다시 읽는다.
     * 체형·설문·장바구니는 읽을 때마다 지금 계정 것을 쓰므로 여기서 할 일이 없다.
     */
    public static void reloadAccountData(Context context) {
        MyPageStore.init(context);
        DummyData.clearLikes();
        RecommendApi.refreshLikes(context);
    }

    /** 앱을 켤 때 호출. 예전에 하이픈 없이 저장한 로그인 번호도 같은 계정이 되게 바꾼다. */
    public static void normalizeSavedPhone(Context context) {
        String phone = getPhone(context);
        String normalized = LocalAccounts.normalizePhone(phone);
        if (normalized == null || normalized.equals(phone)) {
            return;
        }
        prefs(context).edit().putString(KEY_PHONE, normalized).apply();
        adoptOldPhoneData(context, phone);
    }

    /** 다른 형식의 번호로 저장된 체형·설문·찜·사진을, 지금 계정에 기록이 없을 때만 가져온다. */
    private static void adoptOldPhoneData(Context context, String oldPhone) {
        if (oldPhone == null || LocalAccounts.normalizePhone(oldPhone) == null) {
            return;
        }
        String to = BodyProfileStore.accountKey(context);
        for (String from : new String[]{oldPhone, oldPhone.replaceAll("[^0-9]", "")}) {
            BodyProfileStore.adoptAccount(context, from, to);
            MyPageStore.adoptAccount(context, from, to);
            AccountPrefs.adoptAccount(context, from, to);
            CartStore.adoptAccount(context, from, to);
            ProfilePhotoStore.adoptAccount(context, from, to);
        }
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
        MyPageStore.logout();   // 메모리의 이전 계정 기록을 비우고 비로그인 기록을 읽는다
        DummyData.clearLikes();
        RecommendApi.refreshLikes(context);
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
