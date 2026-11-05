package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import com.example.capstone.login.LoginSession;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 장바구니: 앱을 다시 켜도 남고, 계정마다 따로이고, 같은 상품도 옵션이 다르면 따로 담긴다 */
public class CartStoreTest {

    private Context context;
    private Product shirt;
    private Product pants;

    @Before
    public void setUp() {
        context = new FakeContext();
        CartStore.forgetLoadedForTest();
        // 앱 상품(DummyData)과 번호가 겹치지 않게 9000번대 (겹치면 다시 켤 때 그 상품 정보로 복원된다)
        shirt = new Product(9001, "브랜드", "셔츠", 10000);
        pants = new Product(9002, "브랜드", "바지", 20000);
    }

    @Test
    public void sameProductDifferentOptionGoesToSeparateLines() {
        CartStore.add(context, shirt, "M", "BLACK", 1);
        CartStore.add(context, shirt, "L", "BLACK", 2);
        CartStore.add(context, shirt, "M", "BLACK", 3);     // 같은 옵션은 수량을 더한다

        List<Product> lines = CartStore.lines(context);
        assertEquals(2, lines.size());
        assertEquals("M", lines.get(0).getSelectedSize());
        assertEquals(4, lines.get(0).getCartQuantity());
        assertEquals("L", lines.get(1).getSelectedSize());
        assertEquals(2, lines.get(1).getCartQuantity());
        assertTrue(lines.get(0).getCartLineId() != lines.get(1).getCartLineId());
    }

    @Test
    public void cartSurvivesAppRestart() {
        CartStore.add(context, shirt, "M", "BLACK", 2);
        Product pantsLine = CartStore.add(context, pants, "S", "NAVY", 1);
        CartStore.setSelected(context, pantsLine, false);

        CartStore.forgetLoadedForTest();                    // 앱을 껐다 켬

        List<Product> lines = CartStore.lines(context);
        assertEquals(2, lines.size());
        assertEquals("셔츠", lines.get(0).getName());
        assertEquals(2, lines.get(0).getCartQuantity());
        assertTrue(lines.get(0).isCartSelected());
        assertEquals("NAVY", lines.get(1).getSelectedColor());
        assertFalse(lines.get(1).isCartSelected());          // 체크 해제도 남는다
        assertTrue(lines.get(1).isInCart());
    }

    @Test
    public void appProductIsRestoredFromCatalogAfterRestart() {
        Product real = DummyData.getProductById(1);
        CartStore.add(context, real, "M", "BLACK", 1);

        CartStore.forgetLoadedForTest();

        Product line = CartStore.lines(context).get(0);
        assertEquals(real.getName(), line.getName());
        assertEquals(real.getCategory(), line.getCategory());
        assertEquals(real.getColors(), line.getColors());     // 옵션 변경 화면이 쓰는 색상 목록도 그대로
    }

    @Test
    public void eachAccountHasItsOwnCartAndGuestCartMergesOnLogin() {
        CartStore.add(context, shirt, "M", "BLACK", 1);     // 비로그인으로 담음

        LoginSession.updateProfile(context, "가", "a@test.com", "");
        List<Product> a = CartStore.lines(context);
        assertEquals(1, a.size());                           // 로그인하면 비로그인 장바구니가 합쳐짐
        CartStore.add(context, pants, "S", "NAVY", 1);

        LoginSession.logout(context);
        assertEquals(0, CartStore.lines(context).size());    // 비로그인 장바구니는 비었음 (계정으로 옮겨짐)

        LoginSession.updateProfile(context, "나", "b@test.com", "");
        assertEquals(0, CartStore.lines(context).size());    // 다른 계정은 따로

        CartStore.forgetLoadedForTest();
        LoginSession.updateProfile(context, "가", "a@test.com", "");
        assertEquals(2, CartStore.lines(context).size());    // 처음 계정의 장바구니는 그대로
    }

    @Test
    public void changingOptionToAnExistingOneMergesLines() {
        Product m = CartStore.add(context, shirt, "M", "BLACK", 1);
        CartStore.add(context, shirt, "L", "BLACK", 2);

        CartStore.changeOption(context, m, "L", "BLACK", 1);

        List<Product> lines = CartStore.lines(context);
        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).getCartQuantity());
    }

    @Test
    public void orderRemovesOnlyOrderedLines() {
        Product m = CartStore.add(context, shirt, "M", "BLACK", 1);
        CartStore.add(context, shirt, "L", "BLACK", 1);
        CartStore.add(context, pants, "S", "NAVY", 1);

        CartStore.removeLines(context, new long[]{m.getCartLineId()});

        List<Product> lines = CartStore.lines(context);
        assertEquals(2, lines.size());
        assertEquals("L", lines.get(0).getSelectedSize());
        assertEquals("바지", lines.get(1).getName());
    }

    @Test
    public void accountRenameMovesCart() {
        LoginSession.updateProfile(context, "가", "old@test.com", "");
        CartStore.add(context, shirt, "M", "BLACK", 1);

        LoginSession.updateProfile(context, "가", "new@test.com", "");
        CartStore.moveAccount(context, "old@test.com", "new@test.com");

        assertEquals(1, CartStore.lines(context).size());
    }

    @Test
    public void quantityIsClamped() {
        Product line = CartStore.add(context, shirt, "M", "BLACK", 500);
        assertEquals(CartStore.MAX_QUANTITY, line.getCartQuantity());
        CartStore.setQuantity(context, line, 0);
        assertEquals(1, CartStore.lines(context).get(0).getCartQuantity());
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
