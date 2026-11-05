package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.BodyTypeActivity;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 설문만 해도 '내 체형 관리'에 체형이 저장된다 (사진 측정을 안 해도) */
public class BodyProfileSurveyTest {

    private static final int[] ANSWERS = {1, 2, 3, 1, 2, 3, 1};
    private static final int[] NEW_ANSWERS = {2, 2, 2, 2, 2, 2, 2};
    private static final long DAY = 24L * 60 * 60 * 1000;

    private Context context;

    @Before
    public void setUp() {
        context = new FakeContext();
    }

    /** BodyTypeActivity가 서버 진단 뒤 저장하는 것과 같은 값 */
    private void finishSurvey(int[] answers, int type, long at) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < answers.length; i++) {
            text.append(i == 0 ? "" : ",").append(answers[i]);
        }
        AccountPrefs.survey(context).edit()
                .putString(AccountPrefs.key(context, AccountPrefs.SURVEY_ANSWERS), text.toString())
                .putInt(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE), type)
                .putFloat(AccountPrefs.key(context, AccountPrefs.SURVEY_HEIGHT), 165f)
                .putLong(AccountPrefs.key(context, AccountPrefs.SURVEY_AT), at)
                .apply();
    }

    @Test
    public void surveyOnlyCreatesOneActiveCard() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());

        BodyTypeActivity.saveSurveyProfile(context);

        List<BodyProfileStore.Profile> list = BodyProfileStore.getProfiles(context);
        assertEquals(1, list.size());
        BodyProfileStore.Profile card = list.get(0);
        assertTrue(card.isSurveyOnly());
        assertEquals("웨이브", card.bodyType);
        assertEquals(165f, card.heightCm, 0.01f);
        assertEquals(card.id, BodyProfileStore.getActiveId(context));   // 홈 추천도 이 카드로
        assertEquals(ANSWERS[2], card.surveyAnswers[2]);
    }

    @Test
    public void surveyAgainUpdatesSameCard() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyTypeActivity.saveSurveyProfile(context);

        finishSurvey(NEW_ANSWERS, BodyTypeActivity.TYPE_NATURAL, System.currentTimeMillis());
        BodyTypeActivity.saveSurveyProfile(context);

        List<BodyProfileStore.Profile> list = BodyProfileStore.getProfiles(context);
        assertEquals(1, list.size());                        // 새 카드가 쌓이지 않는다
        assertEquals("내추럴", list.get(0).bodyType);
        assertEquals(2, list.get(0).surveyAnswers[0]);
    }

    @Test
    public void surveyKeepsMeasuredCardParts() throws Exception {
        LoginSession.login(context, "가", "a@test.com", "");
        JSONObject parts = new JSONObject();
        parts.put("SHOULDER", 40.0);
        BodyProfileStore.save(context, null, "측정", 165f, 0f, "스트레이트", parts, ANSWERS);

        finishSurvey(NEW_ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyTypeActivity.saveSurveyProfile(context);

        List<BodyProfileStore.Profile> list = BodyProfileStore.getProfiles(context);
        assertEquals(1, list.size());
        assertEquals("웨이브", list.get(0).bodyType);
        assertEquals(40f, list.get(0).part("SHOULDER"), 0.01f);   // 치수는 그대로
        assertFalse(list.get(0).isSurveyOnly());
    }

    @Test
    public void guestSurveyBecomesCardOnLogin() {
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_STRAIGHT, System.currentTimeMillis() - 60_000L);
        BodyTypeActivity.saveSurveyProfile(context);              // 비로그인은 카드를 만들지 않는다
        assertEquals(0, BodyProfileStore.getProfiles(context).size());

        LoginSession.login(context, "가", "a@test.com", "");

        BodyProfileStore.Profile active = BodyProfileStore.getActive(context);
        assertNotNull(active);
        assertEquals("스트레이트", active.bodyType);
        assertTrue(BodyTypeActivity.hasLastSurvey(context));
    }

    @Test
    public void oldGuestSurveyIsNotGivenToNewAccount() {
        // 며칠 전 같은 기기에서 비로그인으로 해 본 설문 → 방금 가입한 계정에 '내 체형'이 생기면 안 된다
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis() - 3 * DAY);

        LoginSession.login(context, "새사람", "new@test.com", "");

        assertEquals(0, BodyProfileStore.getProfiles(context).size());
        assertFalse(BodyTypeActivity.hasLastSurvey(context));
    }

    @Test
    public void undiagnosedSurveyIsNotSaved() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        AccountPrefs.survey(context).edit()
                .remove(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE)).apply();   // 서버 진단 전

        BodyTypeActivity.saveSurveyProfile(context);

        assertEquals(0, BodyProfileStore.getProfiles(context).size());
    }

    @Test
    public void recentGuestSurveyWindow() {
        long now = 10 * DAY;
        assertTrue(AccountPrefs.isRecentGuestSurvey(now - DAY, now));
        assertFalse(AccountPrefs.isRecentGuestSurvey(now - DAY - 1, now));
        assertFalse(AccountPrefs.isRecentGuestSurvey(0L, now));
        assertFalse(AccountPrefs.isRecentGuestSurvey(now + 1, now));
    }

    // ── 기기 저장소(SharedPreferences)를 메모리로 흉내 낸다 ──

    private static final class FakeContext extends ContextWrapper {
        private final Map<String, FakePrefs> prefs = new HashMap<>();

        FakeContext() {
            super(null);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            FakePrefs p = prefs.get(name);
            if (p == null) {
                p = new FakePrefs();
                prefs.put(name, p);
            }
            return p;
        }
    }

    private static final class FakePrefs implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public String getString(String k, String d) { Object v = values.get(k); return v == null ? d : (String) v; }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String k, Set<String> d) { Object v = values.get(k); return v == null ? d : (Set<String>) v; }
        @Override public int getInt(String k, int d) { Object v = values.get(k); return v == null ? d : (Integer) v; }
        @Override public long getLong(String k, long d) { Object v = values.get(k); return v == null ? d : (Long) v; }
        @Override public float getFloat(String k, float d) { Object v = values.get(k); return v == null ? d : (Float) v; }
        @Override public boolean getBoolean(String k, boolean d) { Object v = values.get(k); return v == null ? d : (Boolean) v; }
        @Override public boolean contains(String k) { return values.containsKey(k); }
        @Override public Editor edit() { return new FakeEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        private final class FakeEditor implements Editor {
            private final Map<String, Object> puts = new HashMap<>();
            private final Set<String> removes = new HashSet<>();
            private boolean clear;

            @Override public Editor putString(String k, String v) { puts.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { puts.put(k, v); return this; }
            @Override public Editor putInt(String k, int v) { puts.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { puts.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { puts.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { puts.put(k, v); return this; }
            @Override public Editor remove(String k) { removes.add(k); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) {
                    values.clear();
                }
                for (String k : removes) {
                    values.remove(k);
                }
                values.putAll(puts);
            }
        }
    }
}
