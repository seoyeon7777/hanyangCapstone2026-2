package com.example.capstone.data;

/**
 * 새 비밀번호 규칙: 영문 + 숫자 + 특수문자를 모두 포함해 8~20자.
 * 프로필 수정의 비밀번호 변경과 로그인 화면의 비밀번호 재설정이 같은 규칙을 쓴다.
 * (회원가입 화면은 따로 검사한다)
 */
public final class PasswordRule {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 20;

    public static final String MESSAGE = "새 비밀번호는 영문, 숫자, 특수문자를 포함해 8~20자로 입력해주세요.";

    private static final String RULE =
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$";

    private PasswordRule() {
    }

    public static boolean isValid(String password) {
        return password != null && password.matches(RULE);
    }
}
