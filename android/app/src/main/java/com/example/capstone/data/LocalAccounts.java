package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 가입한 회원을 기기에 저장하고 로그인 비밀번호를 확인한다.
 * 비밀번호는 원문을 저장하지 않고 PBKDF2 해시만 저장한다.
 * [여기에 DB 입력] 로그인이 서버와 연결되면 이 클래스 대신 users 표를 쓴다. 지금은 가입한 기기에서만 로그인된다
 */
public final class LocalAccounts {

    private static final String PREF_NAME = "local_accounts";
    private static final String KEY_ACCOUNTS = "accounts";

    // minSdk 24에서 쓸 수 있는 PBKDF2는 SHA1 방식뿐이다 (SHA256은 API 26부터)
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int HASH_ITERATIONS = 10000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;

    public static final class Account {
        public final String id;
        public final String name;
        public final String email;
        public final String phone;

        Account(JSONObject j) {
            id = j.optString("id");
            name = j.optString("name");
            email = j.optString("email");
            phone = j.optString("phone");
        }
    }

    private LocalAccounts() {
    }

    /**
     * 저장·비교용 휴대폰 번호: 숫자만 (01012345678). 하이픈·공백은 떼어 낸다.
     * 휴대폰 번호가 아니면 null. 화면에 보여 줄 때는 formatPhone을 쓴다
     */
    public static String normalizePhone(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[^0-9]", "");
        return digits.matches("01[016789][0-9]{7,8}") ? digits : null;
    }

    /** 화면에 보여 줄 모양 (010-1234-5678). 휴대폰 번호가 아니면 받은 그대로 돌려준다 */
    public static String formatPhone(String phone) {
        String digits = normalizePhone(phone);
        if (digits == null) {
            return phone == null ? "" : phone;
        }
        int midEnd = digits.length() - 4;
        return digits.substring(0, 3) + "-" + digits.substring(3, midEnd) + "-" + digits.substring(midEnd);
    }

    /** 아이디 규칙: 영문 소문자와 숫자를 모두 포함해 4~16자 (다른 글자는 쓸 수 없다) */
    public static boolean isValidId(String id) {
        return id != null && id.matches("(?=.*[a-z])(?=.*[0-9])[a-z0-9]{4,16}");
    }

    /** 비밀번호에 쓸 수 있는 특수문자 (회원가입 화면 안내 문구와 같게 유지) */
    public static final String PASSWORD_SPECIALS = "~`!@#$%^()*_-={}[]|;:<>,.?/";

    /** 비밀번호에 영문·숫자·PASSWORD_SPECIALS 말고 다른 글자(공백·한글·&·+ 등)가 있으면 true */
    public static boolean hasInvalidPasswordChar(String password) {
        for (char c : password.toCharArray()) {
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || PASSWORD_SPECIALS.indexOf(c) >= 0;
            if (!ok) {
                return true;
            }
        }
        return false;
    }

    /** 비밀번호 규칙: 영문 대소문자·숫자·특수문자 중 2가지 이상 조합해 8~16자 (특수문자는 PASSWORD_SPECIALS만) */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 16
                || hasInvalidPasswordChar(password)) {
            return false;
        }
        int kinds = 0;
        if (password.matches(".*[A-Za-z].*")) {
            kinds++;
        }
        if (password.matches(".*[0-9].*")) {
            kinds++;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            kinds++;
        }
        return kinds >= 2;
    }

    /** 앞뒤 공백을 지우고 소문자로 바꾼 이메일 주소. 이메일 형식이 아니면 null */
    public static String normalizeEmail(String input) {
        if (input == null) {
            return null;
        }
        String email = input.trim().toLowerCase(java.util.Locale.ROOT);
        return email.matches("[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}") ? email : null;
    }

    public static boolean register(Context context, String id, String password, String name, String phone) {
        return register(context, id, password, name, phone, "");
    }

    /**
     * 가입할 때 쓴다. 이메일은 선택이라 빈 값이면 저장하지 않는다.
     * 규칙에 맞지 않거나 아이디·휴대폰 번호·이메일이 이미 있으면 저장하지 않고 false
     */
    public static boolean register(Context context, String id, String password, String name, String phone,
                                   String email) {
        String saved = normalizePhone(phone);
        String savedEmail = email == null || email.trim().isEmpty() ? "" : normalizeEmail(email);
        if (saved == null || savedEmail == null || !isValidId(id) || password == null
                || !isValidPassword(password.trim())
                || idExists(context, id) || isTaken(context, "", saved)
                || (!savedEmail.isEmpty() && isTaken(context, "", savedEmail))) {
            return false;
        }
        JSONArray accounts = load(context);
        try {
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("name", name);
            j.put("email", savedEmail);
            j.put("phone", saved);
            putPassword(j, password);
            accounts.put(j);
        } catch (JSONException | GeneralSecurityException e) {
            return false;
        }
        save(context, accounts);
        return true;
    }

    /** 휴대폰 번호 또는 이메일과 비밀번호가 맞으면 회원 정보, 틀리면 null */
    public static Account login(Context context, String phoneOrEmail, String password) {
        JSONObject j = findByContact(load(context), phoneOrEmail);
        if (j == null || !passwordMatches(j, password)) {
            return null;
        }
        return new Account(j);
    }

    /**
     * 예전에 하이픈 붙여·공백 섞어 저장한 회원의 휴대폰 번호를 숫자만 남기는 모양(01012345678)으로 고쳐 저장한다.
     * 바뀐 회원마다 {예전 번호, 새 번호, 이메일}을 돌려준다 (번호로 저장해 둔 다른 기록을 옮길 때 쓴다).
     * 이미 맞는 모양이면 아무것도 안 바꾸고, 여러 번 불러도 결과가 같다.
     */
    public static List<String[]> normalizeStoredPhones(Context context) {
        List<String[]> changed = new ArrayList<>();
        JSONArray accounts = load(context);
        try {
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject j = accounts.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                String old = j.optString("phone");
                String normalized = normalizePhone(old);
                if (normalized != null && !normalized.equals(old)) {
                    j.put("phone", normalized);
                    changed.add(new String[]{old, normalized, j.optString("email")});
                }
            }
        } catch (JSONException ignored) {
            // 문자열만 넣으므로 일어나지 않는다
        }
        if (!changed.isEmpty()) {
            save(context, accounts);
        }
        return changed;
    }

    /** 아이디와 비밀번호가 맞으면 회원 정보, 틀리면 null */
    public static Account loginWithId(Context context, String id, String password) {
        JSONObject j = findById(load(context), id);
        if (j == null || !passwordMatches(j, password)) {
            return null;
        }
        return new Account(j);
    }

    /** 아이디로 회원을 찾는다. 없으면 null */
    public static Account findAccountById(Context context, String id) {
        JSONObject j = id == null ? null : findById(load(context), id);
        return j == null ? null : new Account(j);
    }

    /** 이 휴대폰 번호로 가입한 회원을 찾는다 (하이픈 유무는 상관없다). 없으면 null */
    public static Account findAccountByPhone(Context context, String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null) {
            return null;
        }
        JSONArray accounts = load(context);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject j = accounts.optJSONObject(i);
            if (j != null && normalized.equals(normalizePhone(j.optString("phone")))) {
                return new Account(j);
            }
        }
        return null;
    }

    /** 이 이메일을 등록한 회원을 찾는다 (대소문자는 상관없다). 없으면 null */
    public static Account findAccountByEmail(Context context, String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        JSONArray accounts = load(context);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject j = accounts.optJSONObject(i);
            if (j != null && !j.optString("email").isEmpty()
                    && email.trim().equalsIgnoreCase(j.optString("email"))) {
                return new Account(j);
            }
        }
        return null;
    }

    public static boolean idExists(Context context, String id) {
        return findById(load(context), id) != null;
    }

    public static boolean contactExists(Context context, String phoneOrEmail) {
        return findByContact(load(context), phoneOrEmail) != null;
    }

    /** 이 휴대폰 번호·이메일을 다른 회원이 쓰고 있으면 true */
    public static boolean isTaken(Context context, String myId, String phoneOrEmail) {
        JSONObject j = findByContact(load(context), phoneOrEmail);
        return j != null && !j.optString("id").equals(myId);
    }

    public static boolean checkPassword(Context context, String id, String password) {
        JSONObject j = findById(load(context), id);
        return j != null && passwordMatches(j, password);
    }

    public static boolean changePassword(Context context, String id, String password) {
        JSONArray accounts = load(context);
        return setPassword(context, accounts, findById(accounts, id), password);
    }

    public static boolean resetPassword(Context context, String phoneOrEmail, String password) {
        JSONArray accounts = load(context);
        return setPassword(context, accounts, findByContact(accounts, phoneOrEmail), password);
    }

    /** 프로필에서 바꾼 값을 회원 정보에도 반영한다 */
    public static void updateProfile(Context context, String id, String name, String email, String phone) {
        JSONArray accounts = load(context);
        JSONObject j = findById(accounts, id);
        if (j == null) {
            return;
        }
        try {
            j.put("name", name);
            j.put("email", email);
            j.put("phone", phone);
            save(context, accounts);
        } catch (JSONException ignored) {
            // 문자열만 넣으므로 일어나지 않는다
        }
    }

    public static void delete(Context context, String id) {
        JSONArray accounts = load(context);
        List<JSONObject> left = new ArrayList<>();
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject j = accounts.optJSONObject(i);
            if (j != null && !j.optString("id").equals(id)) {
                left.add(j);
            }
        }
        save(context, new JSONArray(left));
    }

    private static boolean setPassword(Context context, JSONArray accounts, JSONObject j, String password) {
        if (j == null) {
            return false;
        }
        try {
            putPassword(j, password);
        } catch (JSONException | GeneralSecurityException e) {
            return false;
        }
        save(context, accounts);
        return true;
    }

    private static void putPassword(JSONObject j, String password) throws JSONException, GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        j.put("salt", toHex(salt));
        j.put("hash", hash(password, salt));
    }

    private static boolean passwordMatches(JSONObject j, String password) {
        try {
            String expected = j.optString("hash");
            String actual = hash(password, fromHex(j.optString("salt")));
            return MessageDigest.isEqual(expected.getBytes(), actual.getBytes());
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** 입력 화면들이 앞뒤 공백을 지우고 다루므로 해시도 같은 값으로 만든다 */
    static String hash(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.trim().toCharArray(), salt, HASH_ITERATIONS, HASH_BITS);
        return toHex(SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).getEncoded());
    }

    static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static JSONObject findById(JSONArray accounts, String id) {
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject j = accounts.optJSONObject(i);
            if (j != null && j.optString("id").equals(id)) {
                return j;
            }
        }
        return null;
    }

    private static JSONObject findByContact(JSONArray accounts, String phoneOrEmail) {
        if (phoneOrEmail == null || phoneOrEmail.isEmpty()) {
            return null;
        }
        String phone = normalizePhone(phoneOrEmail);
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject j = accounts.optJSONObject(i);
            if (j == null) {
                continue;
            }
            if (phone != null ? phone.equals(normalizePhone(j.optString("phone")))
                    : phoneOrEmail.equalsIgnoreCase(j.optString("email"))) {
                return j;
            }
        }
        return null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static JSONArray load(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_ACCOUNTS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray accounts) {
        prefs(context).edit().putString(KEY_ACCOUNTS, accounts.toString()).apply();
    }
}
