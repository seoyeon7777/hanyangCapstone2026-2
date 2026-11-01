package com.example.capstone.data;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AuthCodes {

    public static final long VALID_MILLIS = 3 * 60 * 1000L;

    private static final Map<String, String> CODES = new HashMap<>();
    private static final Map<String, Long> EXPIRES = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Result { OK, WRONG, EXPIRED, NOT_SENT }

    private AuthCodes() {
    }

    public static synchronized String issue(String target) {
        int bound = (int) Math.pow(10, InputRules.AUTH_CODE_LENGTH);
        String code = String.format(Locale.ROOT, "%0" + InputRules.AUTH_CODE_LENGTH + "d",
                RANDOM.nextInt(bound));
        CODES.put(target, code);
        EXPIRES.put(target, System.currentTimeMillis() + VALID_MILLIS);
        return code;
    }

    public static synchronized Result verify(String target, String code) {
        String saved = CODES.get(target);
        Long expires = EXPIRES.get(target);
        if (saved == null || expires == null) {
            return Result.NOT_SENT;
        }
        if (System.currentTimeMillis() > expires) {
            CODES.remove(target);
            EXPIRES.remove(target);
            return Result.EXPIRED;
        }
        if (!saved.equals(code == null ? "" : code.trim())) {
            return Result.WRONG;
        }
        CODES.remove(target);
        EXPIRES.remove(target);
        return Result.OK;
    }

    public static String message(Result result) {
        switch (result) {
            case WRONG:
                return "인증번호가 일치하지 않습니다.";
            case EXPIRED:
                return "인증 시간이 지났어요. 인증번호를 다시 받아주세요.";
            case NOT_SENT:
                return "먼저 인증받기를 눌러주세요.";
            default:
                return "인증이 완료되었습니다.";
        }
    }

    public static String sentMessage(String code) {
        return "인증번호가 발송되었습니다. (시연용 인증번호: " + code + ", 3분 안에 입력)";
    }
}
