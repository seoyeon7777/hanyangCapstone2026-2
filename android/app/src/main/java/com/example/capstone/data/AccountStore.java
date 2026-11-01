package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountStore {

    private static final String PREF = "accounts";
    private static final String KEY_LIST = "list";

    public static final class Account {
        public String id = "";
        public String email = "";
        public String phone = "";
        public String name = "";
        public String nickname = "";
        public String birth = "";
        public String gender = "F";
        String salt = "";
        String hash = "";

        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("email", email);
            j.put("phone", phone);
            j.put("name", name);
            j.put("nickname", nickname);
            j.put("birth", birth);
            j.put("gender", gender);
            j.put("salt", salt);
            j.put("hash", hash);
            return j;
        }

        static Account fromJson(JSONObject j) {
            Account a = new Account();
            a.id = j.optString("id");
            a.email = j.optString("email");
            a.phone = j.optString("phone");
            a.name = j.optString("name");
            a.nickname = j.optString("nickname");
            a.birth = j.optString("birth");
            a.gender = j.optString("gender", "F");
            a.salt = j.optString("salt");
            a.hash = j.optString("hash");
            return a;
        }

        public String displayName() {
            if (!nickname.isEmpty()) {
                return nickname;
            }
            return name;
        }
    }

    private AccountStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static synchronized List<Account> load(Context context) {
        List<Account> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                list.add(Account.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e("AccountStore", "회원 정보 불러오기 실패", e);
        }
        return list;
    }

    private static synchronized void save(Context context, List<Account> list) {
        try {
            JSONArray array = new JSONArray();
            for (Account a : list) {
                array.put(a.toJson());
            }
            prefs(context).edit().putString(KEY_LIST, array.toString()).apply();
        } catch (Exception e) {
            Log.e("AccountStore", "회원 정보 저장 실패", e);
        }
    }

    public static Account findByEmail(Context context, String email) {
        String normalized = InputRules.normalizeEmail(email);
        if (normalized == null) {
            return null;
        }
        for (Account a : load(context)) {
            if (a.email.equals(normalized)) {
                return a;
            }
        }
        return null;
    }

    public static Account findByPhone(Context context, String phone) {
        String normalized = InputRules.normalizePhone(phone);
        if (normalized == null) {
            return null;
        }
        for (Account a : load(context)) {
            if (a.phone.equals(normalized)) {
                return a;
            }
        }
        return null;
    }

    public static Account findById(Context context, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Account a : load(context)) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    public static Account findByNameAndPhone(Context context, String name, String phone) {
        Account a = findByPhone(context, phone);
        String normalizedName = InputRules.normalizeName(name);
        if (a == null || normalizedName == null || !a.name.equals(normalizedName)) {
            return null;
        }
        return a;
    }

    public static boolean emailExists(Context context, String email) {
        return findByEmail(context, email) != null;
    }

    public static boolean phoneExists(Context context, String phone) {
        return findByPhone(context, phone) != null;
    }

    public static synchronized Account register(Context context, String email, String phone,
                                                String password, String name, String nickname,
                                                String birth, String gender) {
        String e = InputRules.normalizeEmail(email);
        String p = InputRules.normalizePhone(phone);
        if (e == null || p == null || !InputRules.isValidPassword(password)
                || emailExists(context, e) || phoneExists(context, p)) {
            return null;
        }
        Account a = new Account();
        a.id = UUID.randomUUID().toString();
        a.email = e;
        a.phone = p;
        a.name = name == null ? "" : name;
        a.nickname = nickname == null ? "" : nickname;
        a.birth = birth == null ? "" : birth;
        a.gender = InputRules.toDbGender(gender);
        setPassword(a, password);

        List<Account> list = load(context);
        list.add(a);
        save(context, list);
        return a;
    }

    public static Account loginByEmail(Context context, String email, String password) {
        Account a = findByEmail(context, email);
        return a != null && matches(a, password) ? a : null;
    }

    public static Account loginByPhone(Context context, String phone, String password) {
        Account a = findByPhone(context, phone);
        return a != null && matches(a, password) ? a : null;
    }

    public static boolean checkPassword(Context context, String id, String password) {
        Account a = findById(context, id);
        return a != null && matches(a, password);
    }

    public static synchronized boolean changePassword(Context context, String id, String newPassword) {
        if (!InputRules.isValidPassword(newPassword)) {
            return false;
        }
        List<Account> list = load(context);
        for (Account a : list) {
            if (a.id.equals(id)) {
                setPassword(a, newPassword);
                save(context, list);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean updateProfile(Context context, String id, String name,
                                                     String nickname, String email, String phone) {
        List<Account> list = load(context);
        Account target = null;
        for (Account a : list) {
            if (a.id.equals(id)) {
                target = a;
            }
        }
        if (target == null) {
            return false;
        }
        for (Account a : list) {
            if (a != target && (a.email.equals(email) || a.phone.equals(phone))) {
                return false;
            }
        }
        target.name = name;
        target.nickname = nickname;
        target.email = email;
        target.phone = phone;
        save(context, list);
        return true;
    }

    public static boolean isEmailTakenByOther(Context context, String id, String email) {
        Account a = findByEmail(context, email);
        return a != null && !a.id.equals(id);
    }

    public static boolean isPhoneTakenByOther(Context context, String id, String phone) {
        Account a = findByPhone(context, phone);
        return a != null && !a.id.equals(id);
    }

    public static synchronized void delete(Context context, String id) {
        List<Account> list = load(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
            }
        }
        save(context, list);
    }

    private static boolean matches(Account a, String password) {
        return password != null && !a.hash.isEmpty() && a.hash.equals(hash(a.salt, password));
    }

    private static void setPassword(Account a, String password) {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        a.salt = toHex(bytes);
        a.hash = hash(a.salt, password);
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return toHex(out);
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
