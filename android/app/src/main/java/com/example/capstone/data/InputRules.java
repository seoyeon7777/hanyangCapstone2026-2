package com.example.capstone.data;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InputRules {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 20;
    public static final String PASSWORD_HINT = "영문, 숫자, 특수문자 포함 8~20자 (공백 불가)";
    public static final String PASSWORD_ERROR =
            "비밀번호는 영문, 숫자, 특수문자를 포함해 8~20자로 입력해주세요. (공백 불가)";

    public static final int NICKNAME_MIN = 2;
    public static final int NICKNAME_MAX = 10;
    public static final String NICKNAME_ERROR = "닉네임은 공백 없이 2~10자로 입력해주세요.";

    public static final int NAME_MAX = 20;
    public static final int BIRTH_YEAR_MIN = 1900;
    public static final int AUTH_CODE_LENGTH = 6;

    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");
    private static final Pattern PASSWORD = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9])\\S{" + PASSWORD_MIN + "," + PASSWORD_MAX + "}$");

    private InputRules() {
    }

    public static String normalizeEmail(String input) {
        if (input == null) {
            return null;
        }
        String email = input.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        return EMAIL.matcher(email).matches() ? email : null;
    }

    public static String normalizePhone(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[^0-9]", "");
        if (!digits.matches("01[016789][0-9]{7,8}")) {
            return null;
        }
        int midEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, midEnd) + "-" + digits.substring(midEnd);
    }

    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    public static String normalizeNickname(String input) {
        if (input == null) {
            return null;
        }
        String nickname = input.trim();
        if (nickname.length() < NICKNAME_MIN || nickname.length() > NICKNAME_MAX
                || !nickname.matches("\\S+")) {
            return null;
        }
        return nickname;
    }

    public static String normalizeName(String input) {
        if (input == null) {
            return null;
        }
        String name = input.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > NAME_MAX) {
            return null;
        }
        return name;
    }

    public static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                return leap ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public static boolean isValidBirthYear(int year) {
        return year >= BIRTH_YEAR_MIN && year <= currentYear();
    }

    public static boolean isValidBirth(int year, int month, int day) {
        if (!isValidBirthYear(year) || month < 1 || month > 12) {
            return false;
        }
        if (day < 1 || day > daysInMonth(year, month)) {
            return false;
        }
        Calendar today = Calendar.getInstance();
        Calendar birth = Calendar.getInstance();
        birth.clear();
        birth.set(year, month - 1, day);
        return !birth.after(today);
    }

    public static int age(int year, int month, int day) {
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - year;
        int m = today.get(Calendar.MONTH) + 1;
        int d = today.get(Calendar.DAY_OF_MONTH);
        if (m < month || (m == month && d < day)) {
            age--;
        }
        return age;
    }

    public static String formatBirth(int year, int month, int day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String id = email.substring(0, at);
        int keep = Math.min(2, id.length());
        StringBuilder masked = new StringBuilder(id.substring(0, keep));
        for (int i = keep; i < Math.max(id.length(), keep + 2); i++) {
            masked.append('*');
        }
        return masked + email.substring(at);
    }

    public static String toDbGender(String gender) {
        if (gender == null) {
            return "F";
        }
        switch (gender.trim()) {
            case "M":
            case "남":
            case "남성":
                return "M";
            case "F":
            case "여":
            case "여성":
            case "":
                return "F";
            default:
                return "기타";
        }
    }
}
