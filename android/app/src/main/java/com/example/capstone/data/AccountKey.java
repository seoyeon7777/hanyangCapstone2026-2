package com.example.capstone.data;

/**
 * 계정별 기록(체형·장바구니·주문·검색 기록 …)을 나누는 이름을 정한다 (순수 자바라 단위 테스트가 된다).
 *
 * 로그인한 회원은 아이디다. 아이디는 바꿀 수 없어서 이메일·번호를 바꿔도 기록을 옮길 일이 없다.
 * 아이디 규칙(영문 소문자·숫자를 모두 포함해 4~16자)상 아이디는 "guest"(숫자 없음)나 전화번호(영문 없음),
 * 이메일('@' 없음)과 같아질 수 없어서 다른 이름과 겹치지 않는다.
 * 아이디를 모르는 옛 계정은 예전처럼 이메일, 없으면 번호를 쓴다. 로그인하지 않았으면 "guest".
 */
public final class AccountKey {

    public static final String GUEST = "guest";

    private AccountKey() {
    }

    public static String choose(String userId, String email, String phone) {
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        if (email != null && !email.isEmpty()) {
            return email;
        }
        return phone == null || phone.isEmpty() ? GUEST : phone;
    }
}
