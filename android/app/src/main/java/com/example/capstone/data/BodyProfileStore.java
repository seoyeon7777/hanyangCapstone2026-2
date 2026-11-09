package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.capstone.login.LoginSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public final class BodyProfileStore {

    public static final String[] PART_KEYS = {
            "SHOULDER", "CHEST", "WAIST", "HIP", "ARM_LENGTH", "LEG_LENGTH",
            "NECK", "BACK_LENGTH", "UPPER_ARM", "FOREARM", "THIGH", "CALF"
    };

    public static final String[] PART_LABELS = {
            "어깨너비", "가슴둘레", "허리둘레", "엉덩이둘레", "팔길이", "다리길이",
            "목둘레", "상체길이", "상완둘레", "전완둘레", "허벅지둘레", "종아리둘레"
    };

    public static final int KEY_PART_COUNT = 6;

    /** 프로필 parts 안에서, 키·몸무게·나이로 어림해서 채운 부위 이름들(예: "CHEST,WAIST") */
    public static final String EST_PARTS = "EST_PARTS";

    /** 프로필 parts 안에서, 가슴·허리·엉덩이가 사진 모델(딥러닝)로 구한 값이면 "1" */
    public static final String PHOTO_MODEL = "PHOTO_MODEL";

    public static final String PART_CHEST_DEPTH = "CHEST_DEPTH";
    public static final String PART_SHOULDER_SLOPE = "SHOULDER_SLOPE";

    public static final String SETTING_SIZE_WARNING = "size_warning";
    /** 체형 측정 전 장바구니/주문 시 "아직 체형 측정을 하지 않았습니다" 팝업 */
    public static final String SETTING_MEASURE_NOTICE = "measure_notice";
    /** 새로 측정해 저장한 체형을 대표 체형으로 자동 설정 */
    public static final String SETTING_AUTO_ACTIVE = "auto_active";

    /** 상품 상세에 "내 치수 기준 추천 사이즈" 문구 표시 */
    public static final String SETTING_SIZE_RECOMMEND = "size_recommend";

    public static final String[] SETTING_KEYS = {
            SETTING_SIZE_WARNING, SETTING_MEASURE_NOTICE, SETTING_AUTO_ACTIVE, SETTING_SIZE_RECOMMEND
    };

    private static final String PREF_NAME = "body_profile_store";

    public static class Profile {
        public String id;
        public String name;
        public long savedAt;
        public float heightCm;
        public float bodyFat;
        public String bodyType;
        public JSONObject parts;
        public int[] surveyAnswers;

        public float part(String key) {
            return parts == null ? -1f : (float) parts.optDouble(key, -1d);
        }

        /** 설문만 하고 사진 측정은 안 한 체형 (치수가 없다) */
        public boolean isSurveyOnly() {
            return parts == null || parts.length() == 0;
        }

        public String savedDate() {
            SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA);
            format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            return format.format(new Date(savedAt));
        }

        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("name", name);
            j.put("savedAt", savedAt);
            j.put("height", heightCm);
            j.put("bodyFat", bodyFat);
            j.put("bodyType", bodyType);
            j.put("parts", parts == null ? new JSONObject() : parts);
            if (surveyAnswers != null) {
                JSONArray answers = new JSONArray();
                for (int answer : surveyAnswers) {
                    answers.put(answer);
                }
                j.put("surveyAnswers", answers);
            }
            return j;
        }

        static Profile fromJson(JSONObject j) {
            Profile p = new Profile();
            p.id = j.optString("id");
            p.name = j.optString("name");
            p.savedAt = j.optLong("savedAt");
            p.heightCm = (float) j.optDouble("height", -1d);
            p.bodyFat = (float) j.optDouble("bodyFat", 0d);
            p.bodyType = j.optString("bodyType", "표준");
            p.parts = j.optJSONObject("parts");
            if (p.parts == null) {
                p.parts = new JSONObject();
            }
            p.surveyAnswers = readAnswers(j.optJSONArray("surveyAnswers"));
            return p;
        }

        private static int[] readAnswers(JSONArray array) {
            if (array == null || array.length() != 7) {
                return null;
            }
            int[] answers = new int[7];
            for (int i = 0; i < 7; i++) {
                answers[i] = array.optInt(i, 0);
                if (answers[i] < 1 || answers[i] > 3) {
                    return null;
                }
            }
            return answers;
        }
    }

    private BodyProfileStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String account(Context context) {
        return accountKey(context);
    }

    // 이 부분에 서버 회원번호(user_id) 넣기
    public static String accountKey(Context context) {
        String email = LoginSession.getEmail(context);
        if (!email.isEmpty()) {
            return email;
        }
        String phone = LoginSession.getPhone(context);
        return phone.isEmpty() ? "guest" : phone;
    }

    public static void moveAccount(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit();

        String profiles = p.getString("profiles_" + from, null);
        if (profiles != null) {
            e.putString("profiles_" + to, profiles).remove("profiles_" + from);
        }
        String active = p.getString("active_" + from, null);
        if (active != null) {
            e.putString("active_" + to, active).remove("active_" + from);
        }
        for (String key : SETTING_KEYS) {
            String name = "setting_" + key + "_";
            if (p.contains(name + from)) {
                e.putBoolean(name + to, p.getBoolean(name + from, true)).remove(name + from);
            }
        }
        e.apply();
    }

    /** to 계정에 체형 기록이 없을 때만 from의 기록을 옮긴다. */
    public static void adoptAccount(Context context, String from, String to) {
        SharedPreferences p = prefs(context);
        if (!p.contains("profiles_" + to) && !p.contains("active_" + to)) {
            moveAccount(context, from, to);
        }
    }

    public static List<Profile> getProfiles(Context context) {
        // 이 부분에 서버 body_measurements 목록 조회 API 넣기
        List<Profile> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("profiles_" + account(context), "[]"));
            for (int i = 0; i < array.length(); i++) {
                list.add(Profile.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void writeProfiles(Context context, List<Profile> list) {
        // 이 부분에 서버 body_measurements 저장/수정/삭제 API 넣기
        JSONArray array = new JSONArray();
        try {
            for (Profile p : list) {
                array.put(p.toJson());
            }
        } catch (Exception ignored) {
        }
        prefs(context).edit().putString("profiles_" + account(context), array.toString()).apply();
    }

    public static Profile find(Context context, String id) {
        if (id == null) {
            return null;
        }
        for (Profile p : getProfiles(context)) {
            if (id.equals(p.id)) {
                return p;
            }
        }
        return null;
    }

    /** "내 체형 N" 중 아직 안 쓴 가장 작은 번호 (지운 뒤 다시 저장해도 이름이 겹치지 않게) */
    public static String nextDefaultName(Context context) {
        List<Profile> list = getProfiles(context);
        for (int n = list.size() + 1; ; n++) {
            String name = "내 체형 " + n;
            boolean used = false;
            for (Profile p : list) {
                if (name.equals(p.name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return name;
            }
        }
    }

    public static Profile save(Context context, String editId, String name, float heightCm,
                               float bodyFat, String bodyType, JSONObject parts,
                               int[] surveyAnswers) {
        List<Profile> list = getProfiles(context);
        Profile target = null;
        if (editId != null) {
            for (Profile p : list) {
                if (editId.equals(p.id)) {
                    target = p;
                    break;
                }
            }
        }
        boolean isNew = target == null;
        if (isNew) {
            target = new Profile();
            target.id = UUID.randomUUID().toString();
            list.add(0, target);
        }
        target.name = name;
        target.savedAt = System.currentTimeMillis();
        target.heightCm = heightCm;
        target.bodyFat = bodyFat;
        target.bodyType = bodyType;
        target.parts = parts;
        target.surveyAnswers = surveyAnswers;
        writeProfiles(context, list);
        // 새로 측정한 체형만 자동으로 대표 지정 (기존 체형 수정 시에는 대표 유지)
        // 대표 체형이 하나도 없으면 설정과 상관없이 대표로 지정
        if ((isNew && isSettingOn(context, SETTING_AUTO_ACTIVE)) || getActive(context) == null) {
            setActive(context, target.id);
        } else if (target.id.equals(getActiveId(context))) {
            // 대표 체형을 수정한 경우 바뀐 치수를 반영
            syncActiveProfile(context);
        }
        return target;
    }

    public static void rename(Context context, String id, String name) {
        List<Profile> list = getProfiles(context);
        for (Profile p : list) {
            if (p.id.equals(id)) {
                p.name = name;
            }
        }
        writeProfiles(context, list);
    }

    public static void delete(Context context, String id) {
        List<Profile> list = getProfiles(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
            }
        }
        writeProfiles(context, list);
        if (id.equals(getActiveId(context))) {
            setActive(context, list.isEmpty() ? null : list.get(0).id);
        }
    }

    public static String getActiveId(Context context) {
        return prefs(context).getString("active_" + account(context), null);
    }

    public static Profile getActive(Context context) {
        return find(context, getActiveId(context));
    }

    public static void setActive(Context context, String id) {
        // 이 부분에 서버 대표 체형 변경 API 넣기
        prefs(context).edit().putString("active_" + account(context), id).apply();
        syncActiveProfile(context);
    }

    /** 저장할 수 있는 체형 개수 (배송지처럼 여러 개 저장하고 하나를 현재 체형으로 고른다) */
    public static final int MAX_PROFILES = 10;

    public static boolean canAddProfile(Context context) {
        return getProfiles(context).size() < MAX_PROFILES;
    }

    /**
     * 설문 결과 화면의 "이대로 저장하기": 설문만 한 카드(치수 없음)를 **새로** 만든다.
     * 예전 카드는 그대로 남고(여러 개 저장), 현재 체형은 설정('새 체형을 현재 체형으로 자동 선택')을 따른다.
     * 이미 MAX_PROFILES개면 저장하지 않고 null
     */
    public static Profile saveSurvey(Context context, int[] answers, String bodyType, float heightCm) {
        if (answers == null || !canAddProfile(context)) {
            return null;
        }
        return save(context, null, nextDefaultName(context), heightCm, 0f, bodyType,
                new JSONObject(), answers.clone());
    }

    /** 탈퇴: 이 계정의 저장한 체형·대표 체형·체형 설정을 지운다 */
    public static void deleteAccount(Context context, String account) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove("profiles_" + account)
                .remove("active_" + account);
        for (String key : SETTING_KEYS) {
            editor.remove("setting_" + key + "_" + account);
        }
        editor.apply();
    }

    public static void syncActiveProfile(Context context) {
        UserBodyProfile profile = UserBodyProfile.getInstance();
        profile.clear();
        Profile active = getActive(context);
        float height = active != null ? active.heightCm : BodyInput.height(context);

        // 가슴·허리·엉덩이·허벅지는 키·몸무게·나이로 어림한 값(기준선)과 맞춘다:
        // 측정값이 없거나 옷·팔 때문에 기준선과 크게 다르면 기준선 값을 쓰고 "추정"으로 표시한다.
        // 체형 측정을 안 한 사람(대표 체형 없음)도 키·몸무게·나이를 입력했으면 이 값으로 사이즈 알림을 받는다.
        BodyPrior.Result prior = BodyInput.prior(context, height);
        float[] measured = new float[BodyPrior.PART_KEYS.length];
        String savedEst = active == null || active.parts == null ? "" : active.parts.optString(EST_PARTS, "");
        for (int i = 0; i < measured.length; i++) {
            measured[i] = active == null ? -1f : active.part(BodyPrior.PART_KEYS[i]);
        }
        boolean[] est = new boolean[measured.length];
        boolean fromModel = active != null && active.parts != null
                && "1".equals(active.parts.optString(PHOTO_MODEL, ""));
        // 사진 모델로 구한 가슴·허리·엉덩이는 기준선과 달라도 믿는다 (허벅지는 모델이 없다)
        boolean[] trusted = {fromModel, fromModel, fromModel, false};
        float[] fixed = BodyPrior.reconcile(prior, measured, est, trusted);

        profile.updateFromMeasurement(
                height,
                active == null ? -1f : active.part("SHOULDER"),
                fixed[BodyPrior.CHEST],
                fixed[BodyPrior.WAIST],
                fixed[BodyPrior.HIP],
                fixed[BodyPrior.THIGH]);
        for (int i = 0; i < est.length; i++) {
            profile.setEstimated(i, est[i] || savedEst.contains(BodyPrior.PART_KEYS[i]));
        }
    }

    public static boolean isSettingOn(Context context, String key) {
        return prefs(context).getBoolean("setting_" + key + "_" + account(context), true);
    }

    public static void setSetting(Context context, String key, boolean on) {
        // 이 부분에 서버 사용자 설정 저장 API 넣기
        prefs(context).edit().putBoolean("setting_" + key + "_" + account(context), on).apply();
    }
}
