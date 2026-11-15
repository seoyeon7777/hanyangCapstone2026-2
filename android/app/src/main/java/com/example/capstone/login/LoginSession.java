package com.example.capstone.login;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

import com.example.capstone.data.AccountKey;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.mypage.EditProfileActivity;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.GuestSurvey;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.DeliveryRequests;
import com.example.capstone.data.SearchHistory;
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

    /** 로그인 성공 시 호출. 모르는 값은 빈 문자열로 넘긴다. (아이디를 모르는 옛 방식: 저장된 아이디는 건드리지 않는다) */
    public static void login(Context context, String name, String email, String phone) {
        doLogin(context, null, name, email, phone);
    }

    private static void doLogin(Context context, String userId, String name, String email, String phone) {
        // [여기에 DB 입력] 로그인 성공 시 서버에서 받은 회원번호/토큰도 함께 저장 (users)
        String savedPhone = phone == null ? "" : phone;
        String normalized = LocalAccounts.normalizePhone(savedPhone);
        if (normalized != null) {
            savedPhone = normalized;
        }
        // 아이디는 기록을 나누는 이름이라, 기록을 가져오거나 읽기 전에 먼저 저장한다
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_EMAIL, email == null ? "" : email)
                .putString(KEY_PHONE, savedPhone);
        if (userId != null) {
            editor.putString(KEY_USER_ID, userId);
        }
        editor.apply();
        // 아이디가 계정 이름이 되기 전(이메일·번호 이름)에 저장된 기록은 이 계정 것이면 가져온다
        adoptOldNames(context, email, phone);
        // 비회원일 때의 기록은 새로 로그인한 계정으로 가져오지 않는다 (계정마다 따로)
        reloadAccountData(context);
    }

    /**
     * 계정이 바뀌었을 때(로그인·로그아웃·앱 시작): 메모리에 남은 이전 계정의 기록을 버리고
     * 지금 계정의 기록(주문·리뷰·배송지·카드·찜 …)을 다시 읽는다.
     * 체형·설문·장바구니는 읽을 때마다 지금 계정 것을 쓰므로 여기서 할 일이 없다.
     */
    public static void reloadAccountData(Context context) {
        GuestSurvey.clear();        // 비회원 설문은 로그인·로그아웃하면 사라진다 (새 계정이 이어받지 않는다)
        MyPageStore.init(context);
        DummyData.clearLikes();
        RecommendApi.refreshLikes(context);
    }

    /**
     * 앱을 켤 때 호출. 예전에 하이픈 붙여 저장한 회원의 번호를 모두 숫자만 남기는 모양으로 고치고,
     * 그 번호를 계정 이름으로 써서 저장해 둔 체형·설문·찜·장바구니·주문 기록·사진도 새 이름으로 옮긴다.
     * (이메일이 있는 회원은 기록이 이메일 이름이라 옮길 것이 없다. 여러 번 불러도 결과가 같다)
     */
    public static void migrateStoredPhones(Context context) {
        for (String[] change : LocalAccounts.normalizeStoredPhones(context)) {
            if (!change[2].isEmpty()) {
                continue;
            }
            moveOldKeys(context, change[0], change[1]);
        }
    }

    /** 앱을 켤 때 호출. 예전 모양(하이픈 있는 번호)으로 저장한 로그인 번호도 숫자만 남기는 모양으로 바꾼다. */
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
        moveOldKeys(context, oldPhone, BodyProfileStore.accountKey(context));
    }

    /** 옛 이름(번호 모양이 다른 것, 숫자만 있는 것)으로 저장된 기록을 to로 옮긴다. to에 기록이 있으면 안 옮긴다 */
    private static void moveOldKeys(Context context, String oldPhone, String to) {
        for (String from : new String[]{oldPhone, oldPhone.replaceAll("[^0-9]", "")}) {
            adoptEverything(context, from, to);
        }
    }

    /** from 이름으로 저장된 모든 기록(체형·주문·장바구니·검색 기록·요청사항·마케팅 설정 …)을, to에 기록이 없는 것만 to로 옮긴다 */
    private static void adoptEverything(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }
        BodyProfileStore.adoptAccount(context, from, to);
        MyPageStore.adoptAccount(context, from, to);
        AccountPrefs.adoptAccount(context, from, to);
        CartStore.adoptAccount(context, from, to);
        ProfilePhotoStore.adoptAccount(context, from, to);
        SearchHistory.adoptAccount(context, from, to);
        DeliveryRequests.adoptAccount(context, from, to);
        EditProfileActivity.adoptMarketingSettings(context, from, to);
    }

    /**
     * 기록을 나누는 이름이 이메일·번호에서 아이디로 바뀐 뒤에도 예전 기록이 보이게,
     * 이 회원의 이메일·번호(하이픈 있는 것·없는 것) 이름으로 저장된 기록을 지금 이름(아이디)으로 옮긴다.
     * 지금 이름에 이미 기록이 있는 항목은 옮기지 않는다. 여러 번 불러도 결과가 같다.
     */
    private static void adoptOldNames(Context context, String email, String phone) {
        String to = BodyProfileStore.accountKey(context);
        Set<String> names = new LinkedHashSet<>();
        if (email != null) {
            names.add(email);
        }
        if (phone != null) {
            names.add(phone);
            names.add(phone.replaceAll("[^0-9]", ""));
            names.add(LocalAccounts.formatPhone(phone));
        }
        for (String from : names) {
            if (from == null || from.isEmpty() || AccountKey.GUEST.equals(from)) {
                continue;
            }
            adoptEverything(context, from, to);
        }
    }

    /** 앱을 켤 때 호출: 이미 로그인된 회원이 아이디로 기록을 나누게 바뀐 뒤에도 예전 기록을 그대로 보게 옮긴다 */
    public static void migrateToUserId(Context context) {
        if (!isLoggedIn(context) || getUserId(context).isEmpty()) {
            return;
        }
        adoptOldNames(context, getEmail(context), getPhone(context));
    }

    public static void login(Context context, String userId, String name, String email, String phone) {
        doLogin(context, userId == null ? "" : userId, name, email, phone);
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
        GuestSurvey.clear();
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
