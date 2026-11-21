package com.example.capstone.ui;

/** 화면에 보여 줄 이름·계좌번호를 일부 가린다 (순수 자바라 단위 테스트가 된다) */
public final class MaskText {

    private MaskText() {
    }

    /** 홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수. 한 글자·빈 값은 그대로 */
    public static String holder(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim();
        int n = s.codePointCount(0, s.length());
        if (n <= 1) {
            return s;
        }
        int[] cps = s.codePoints().toArray();
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(cps[0]);
        if (n == 2) {
            return sb.append('*').toString();
        }
        for (int i = 1; i < n - 1; i++) {
            sb.append('*');
        }
        return sb.appendCodePoint(cps[n - 1]).toString();
    }

    /**
     * 아이디를 앞 2글자와 마지막 1글자만 남기고 *로 가린다. tester02 → te*****2, abcd → ab*d, abc → a**.
     * 아이디 찾기 결과가 인증 뒤에도 전체가 그대로 보이지 않게 한다.
     */
    public static String userId(String id) {
        if (id == null) {
            return "";
        }
        String s = id.trim();
        int n = s.length();
        if (n <= 1) {
            return s;
        }
        if (n <= 3) {
            return s.charAt(0) + "*".repeat(n - 1);
        }
        return s.substring(0, 2) + "*".repeat(n - 3) + s.charAt(n - 1);
    }

    /**
     * 계좌번호의 숫자 중 앞 4자리·뒤 4자리만 남기고 *로 가린다. 하이픈 등 숫자가 아닌 글자는 그대로 둔다.
     * 숫자가 8자리 이하면 앞뒤가 겹치므로 앞 1자리·뒤 1자리만 남긴다.
     */
    public static String account(String accountNo) {
        if (accountNo == null) {
            return "";
        }
        int digits = 0;
        for (int i = 0; i < accountNo.length(); i++) {
            if (Character.isDigit(accountNo.charAt(i))) {
                digits++;
            }
        }
        int keep = digits > 8 ? 4 : 1;
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (int i = 0; i < accountNo.length(); i++) {
            char c = accountNo.charAt(i);
            if (!Character.isDigit(c)) {
                sb.append(c);
                continue;
            }
            seen++;
            sb.append(seen <= keep || seen > digits - keep ? c : '*');
        }
        return sb.toString();
    }
}
