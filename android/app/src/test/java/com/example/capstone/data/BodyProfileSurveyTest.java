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

/** 설문 결과 화면의 "이대로 저장하기"로 사진 없이도 '내 체형'에 저장된다. "나가기"는 저장 안 함 */
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
    public void eachSaveAddsCardAndNewestBecomesCurrent() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyProfileStore.Profile first = BodyTypeActivity.saveSurveyProfile(context);

        finishSurvey(NEW_ANSWERS, BodyTypeActivity.TYPE_NATURAL, System.currentTimeMillis());
        BodyProfileStore.Profile second = BodyTypeActivity.saveSurveyProfile(context);

        List<BodyProfileStore.Profile> list = BodyProfileStore.getProfiles(context);
        assertEquals(2, list.size());                        // 배송지처럼 여러 개 저장
        assertEquals("내추럴", list.get(0).bodyType);
        assertEquals("웨이브", list.get(1).bodyType);         // 예전 체형은 그대로
        assertEquals("내 체형 1", first.name);
        assertEquals("내 체형 2", second.name);
        assertEquals(second.id, BodyProfileStore.getActiveId(context));   // 새 체형이 현재 체형 (기본 설정)

        BodyProfileStore.setActive(context, first.id);       // '내 체형 관리'에서 예전 체형을 현재 체형으로 선택
        assertEquals("웨이브", BodyProfileStore.getActive(context).bodyType);
        assertEquals(ANSWERS[2], BodyProfileStore.getActive(context).surveyAnswers[2]);   // 홈 추천도 이 답으로
    }

    @Test
    public void newSaveKeepsCurrentWhenAutoSelectOff() throws Exception {
        LoginSession.login(context, "가", "a@test.com", "");
        JSONObject parts = new JSONObject();
        parts.put("SHOULDER", 40.0);
        BodyProfileStore.Profile measured =
                BodyProfileStore.save(context, null, "측정", 165f, 0f, "스트레이트", parts, ANSWERS);
        BodyProfileStore.setSetting(context, BodyProfileStore.SETTING_AUTO_ACTIVE, false);

        finishSurvey(NEW_ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyTypeActivity.saveSurveyProfile(context);

        assertEquals(2, BodyProfileStore.getProfiles(context).size());
        BodyProfileStore.Profile current = BodyProfileStore.getActive(context);
        assertEquals(measured.id, current.id);                // 자동 선택을 끄면 현재 체형 유지
        assertEquals(40f, current.part("SHOULDER"), 0.01f);   // 치수 카드도 그대로
    }

    @Test
    public void continuePhotoFillsSameSurveyCard() throws Exception {
        // 설문 카드에서 "사진 분석 이어서 하기" → MeasurementResultActivity가 그 카드 id로 저장 (새 카드 안 생김)
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyProfileStore.Profile card = BodyTypeActivity.saveSurveyProfile(context);
        assertTrue(card.isSurveyOnly());

        JSONObject parts = new JSONObject();
        parts.put("SHOULDER", 38.5);
        BodyProfileStore.save(context, card.id, card.name, 165f, 0f, card.bodyType, parts, card.surveyAnswers);

        List<BodyProfileStore.Profile> list = BodyProfileStore.getProfiles(context);
        assertEquals(1, list.size());
        assertEquals(card.id, list.get(0).id);
        assertFalse(list.get(0).isSurveyOnly());
        assertEquals("웨이브", list.get(0).bodyType);
        assertEquals(38.5f, list.get(0).part("SHOULDER"), 0.01f);
    }

    @Test
    public void atMostTenProfiles() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        for (int i = 0; i < BodyProfileStore.MAX_PROFILES; i++) {
            assertNotNull(BodyTypeActivity.saveSurveyProfile(context));
        }
        assertFalse(BodyProfileStore.canAddProfile(context));
        assertEquals(null, BodyTypeActivity.saveSurveyProfile(context));
        assertEquals(BodyProfileStore.MAX_PROFILES, BodyProfileStore.getProfiles(context).size());
    }

    @Test
    public void defaultNameSkipsUsedNumbers() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        BodyProfileStore.Profile one = BodyTypeActivity.saveSurveyProfile(context);
        BodyTypeActivity.saveSurveyProfile(context);           // 내 체형 2
        BodyProfileStore.setActive(context, null);
        BodyProfileStore.delete(context, one.id);              // 1번을 지우면 목록 1개

        assertEquals("내 체형 3", BodyTypeActivity.saveSurveyProfile(context).name);   // "내 체형 2"와 안 겹침
    }

    @Test
    public void guestSaveChoiceBecomesCardOnLogin() {
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_STRAIGHT, System.currentTimeMillis() - 60_000L);
        BodyTypeActivity.saveSurveyProfile(context);              // 비로그인은 카드를 만들지 않는다
        assertEquals(0, BodyProfileStore.getProfiles(context).size());

        BodyTypeActivity.markSurveySavePending(context);          // 결과 화면 "이대로 저장하기" → 로그인하러 감
        LoginSession.login(context, "가", "a@test.com", "");

        BodyProfileStore.Profile active = BodyProfileStore.getActive(context);
        assertNotNull(active);
        assertEquals("스트레이트", active.bodyType);
        assertTrue(BodyTypeActivity.hasLastSurvey(context));
    }

    @Test
    public void guestLeaveChoiceIsNotSavedOnLogin() {
        // "저장하지 않고 나가기"를 고른 비로그인 설문 → 로그인해도 카드로 만들지 않고, 홈 추천에도 안 쓴다
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis() - 60_000L);
        assertNotNull(BodyTypeActivity.homeSurveyAnswers(context));   // 비로그인은 최근 설문으로 추천

        LoginSession.login(context, "가", "a@test.com", "");

        assertEquals(0, BodyProfileStore.getProfiles(context).size());
        assertEquals(null, BodyTypeActivity.homeSurveyAnswers(context));  // 저장한 체형이 없으니 골격 맞춤 없음
        assertEquals(null, BodyProfileStore.getActive(context));
    }

    @Test
    public void savedSurveyCardIsUsedForHome() {
        LoginSession.login(context, "가", "a@test.com", "");
        finishSurvey(ANSWERS, BodyTypeActivity.TYPE_WAVE, System.currentTimeMillis());
        assertEquals(null, BodyTypeActivity.homeSurveyAnswers(context));  // 진단만 하고 저장 전 → 홈에 안 씀
        assertEquals(null, BodyProfileStore.getActive(context));

        BodyTypeActivity.saveSurveyProfile(context);              // "이대로 저장하기"

        BodyProfileStore.Profile active = BodyProfileStore.getActive(context);   // 홈은 대표 카드의 설문 답으로 추천
        assertNotNull(active);
        assertEquals(ANSWERS[1], active.surveyAnswers[1]);
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
