package com.example.capstone.ui;

import com.example.capstone.data.RecommendApi;

import java.util.regex.Pattern;

/**
 * 이메일 입력 검증을 한 곳에서 한다 (회원가입·프로필 이메일 변경·아이디 찾기·결제 영수증 이메일이 같이 쓴다).
 * 1) 형식: 앞부분@도메인.최상위 (한글·공백·@ 두 개 등은 거절)
 * 2) 서버가 @ 뒤 도메인이 실제로 있는지 확인 (메일은 보내지 않는다). 서버에 연결이 안 되면 막지 않는다.
 * 선택 입력(가입 이메일·영수증 이메일)은 값을 넣었을 때만 이 검증을 부른다.
 */
public final class EmailCheck {

    /** 형식이 틀렸을 때 안내 문구 (토스트) */
    public static final String FORMAT_ERROR = "이메일 형식이 올바르지 않아요.";

    private static final Pattern FORMAT = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    public interface Listener {
        /** 형식이 맞고, 도메인이 있거나 서버에서 확인하지 못했다 */
        void onValid();

        /** 형식이 틀렸거나 없는 도메인이다. message는 그대로 보여 줄 안내 문구 */
        void onInvalid(String message);
    }

    private EmailCheck() {
    }

    /** 형식만 본다 (서버 호출 없음) */
    public static boolean isValidFormat(String email) {
        return email != null && email.length() <= 100 && FORMAT.matcher(email).matches();
    }

    /** 형식을 보고, 맞으면 서버에 도메인 확인을 요청한다. 결과는 메인 스레드에서 listener로 온다 */
    public static void verify(String email, Listener listener) {
        if (!isValidFormat(email)) {
            listener.onInvalid(FORMAT_ERROR);
            return;
        }
        RecommendApi.checkEmailDomain(email, new RecommendApi.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean ok) {
                listener.onValid();
            }

            @Override
            public void onError(int code, String message) {
                if (code == 0 || code >= 500) {
                    // 서버에 연결이 안 되거나 서버가 일시적으로 문제면 막지 않는다
                    listener.onValid();
                } else {
                    listener.onInvalid(message);
                }
            }
        });
    }
}
