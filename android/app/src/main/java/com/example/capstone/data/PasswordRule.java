package com.example.capstone.data;

/**
 * 비밀번호 규칙은 회원가입·비밀번호 변경·비밀번호 재설정이 모두 같다 (LocalAccounts.isValidPassword):
 * 영문 대소문자·숫자·특수문자 중 2가지 이상, 8~16자, 특수문자는 안내된 것만.
 * (11/08 통합: 연오의 재설정 규칙 "3가지 모두 8~20자"와 지현의 가입·변경 규칙이 달라 지현 규칙 하나로 맞춤)
 */
public final class PasswordRule {

    public static final String MESSAGE =
            "새 비밀번호는 영문 대소문자, 숫자, 특수문자 중 2가지 이상을 조합해 8~16자로 입력해주세요.";

    private PasswordRule() {
    }

    public static boolean isValid(String password) {
        return LocalAccounts.isValidPassword(password);
    }
}
