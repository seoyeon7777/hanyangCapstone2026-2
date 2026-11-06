package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 장바구니 저장소.
 *
 * 한 줄 = 상품 하나 + 옵션(사이즈·색상) + 수량 + 체크 여부.
 * - 로그인 계정마다 따로, 기기(SharedPreferences "cart_store")에 저장한다 → 앱을 꺼도 남는다
 *   계정 이름은 다른 계정별 기록과 같은 BodyProfileStore.accountKey (비로그인은 guest)
 * - 같은 상품도 옵션이 다르면 다른 줄로 담는다. 같은 옵션을 또 담으면 그 줄의 수량을 더한다
 * - 비로그인(guest)일 때 담은 장바구니는 로그인하면 그 계정 장바구니에 합친다
 * - 화면에는 줄마다 만든 Product 복사본을 준다 (Product.copyForCart).
 *   isInCart·getCartQuantity·isCartSelected·getSelectedSize·getSelectedColor·getCartLineId가 그 줄의 값이다.
 *   값을 바꿀 때는 복사본을 직접 고치지 말고 이 클래스의 함수를 부른다 (그래야 저장된다)
 *
 * [여기에 DB 입력] 서버에 장바구니 API(carts 표)가 생기면 저장 위치를 그쪽으로 바꾼다
 */
public final class CartStore {

    public static final int MAX_QUANTITY = 99;

    private static final String TAG = "CartStore";
    private static final String PREF = "cart_store";
    private static final String GUEST = "guest";
    private static final String KEY_NEXT_ID = "next_line_id";

    // 지금 불러와 둔 계정과 그 계정의 장바구니 줄 (담은 순서)
    private static String loadedAccount = null;
    private static final List<Product> LINES = new ArrayList<>();

    private CartStore() {
    }

    // =========================
    // 읽기
    // =========================

    /** 지금 계정의 장바구니 줄 (담은 순서). 돌려준 목록을 바꿔도 장바구니는 안 바뀐다 */
    public static synchronized List<Product> lines(Context context) {
        ensureLoaded(context);
        return new ArrayList<>(LINES);
    }

    /** 체크된 줄만 */
    public static synchronized List<Product> selectedLines(Context context) {
        ensureLoaded(context);
        List<Product> selected = new ArrayList<>();
        for (Product line : LINES) {
            if (line.isCartSelected()) {
                selected.add(line);
            }
        }
        return selected;
    }

    /** 번호로 줄 찾기 (주문 화면이 주문할 줄을 받을 때) */
    public static synchronized List<Product> linesById(Context context, long[] lineIds) {
        ensureLoaded(context);
        List<Product> found = new ArrayList<>();
        if (lineIds == null) {
            return found;
        }
        for (long id : lineIds) {
            Product line = find(id);
            if (line != null) {
                found.add(line);
            }
        }
        return found;
    }

    /** 이 상품이 옵션과 상관없이 장바구니에 있는지 (추천 목록에서 뺄 때) */
    public static synchronized boolean contains(Context context, Product product) {
        ensureLoaded(context);
        for (Product line : LINES) {
            if (sameProduct(line, product)) {
                return true;
            }
        }
        return false;
    }

    /** 이 상품의 이 옵션이 이미 담겨 있는지 */
    public static synchronized boolean containsOption(Context context, Product product, String size, String color) {
        ensureLoaded(context);
        return findOption(product, size, color, 0L) != null;
    }

    // =========================
    // 담기·바꾸기·빼기 (바꿀 때마다 저장)
    // =========================

    /**
     * 장바구니에 담는다. 같은 상품·같은 옵션이 있으면 그 줄의 수량을 더하고, 없으면 새 줄을 만든다.
     * 담은(또는 수량을 더한) 줄을 돌려준다.
     */
    public static synchronized Product add(Context context, Product product, String size, String color, int quantity) {
        ensureLoaded(context);
        Product same = findOption(product, size, color, 0L);
        if (same != null) {
            same.setCartQuantity(clampQuantity(same.getCartQuantity() + quantity));
            same.setCartSelected(true);
            save(context);
            return same;
        }
        Product line = product.copyForCart(nextLineId(context), size, color, clampQuantity(quantity), true);
        LINES.add(line);
        save(context);
        return line;
    }

    /**
     * 장바구니 줄의 옵션·수량을 바꾼다.
     * 바꾼 옵션이 이미 다른 줄에 있으면 그 줄로 합치고 이 줄은 지운다. 남은 줄을 돌려준다.
     */
    public static synchronized Product changeOption(Context context, Product line, String size, String color, int quantity) {
        ensureLoaded(context);
        Product mine = find(line.getCartLineId());
        if (mine == null) {
            return null;
        }
        Product other = findOption(mine, size, color, mine.getCartLineId());
        if (other != null) {
            other.setCartQuantity(clampQuantity(other.getCartQuantity() + quantity));
            other.setCartSelected(other.isCartSelected() || mine.isCartSelected());
            LINES.remove(mine);
            save(context);
            return other;
        }
        mine.setSelectedSize(size);
        mine.setSelectedColor(color);
        mine.setCartQuantity(clampQuantity(quantity));
        save(context);
        return mine;
    }

    public static synchronized void setQuantity(Context context, Product line, int quantity) {
        ensureLoaded(context);
        Product mine = find(line.getCartLineId());
        if (mine != null) {
            mine.setCartQuantity(clampQuantity(quantity));
            save(context);
        }
    }

    public static synchronized void setSelected(Context context, Product line, boolean selected) {
        ensureLoaded(context);
        Product mine = find(line.getCartLineId());
        if (mine != null) {
            mine.setCartSelected(selected);
            save(context);
        }
    }

    public static synchronized void setAllSelected(Context context, boolean selected) {
        ensureLoaded(context);
        for (Product line : LINES) {
            line.setCartSelected(selected);
        }
        save(context);
    }

    public static synchronized void remove(Context context, Product line) {
        ensureLoaded(context);
        Product mine = find(line.getCartLineId());
        if (mine != null) {
            LINES.remove(mine);
            save(context);
        }
    }

    /** 결제를 마친 줄들을 뺀다 (주문하지 않은 줄은 그대로) */
    public static synchronized void removeLines(Context context, long[] lineIds) {
        ensureLoaded(context);
        if (lineIds == null || lineIds.length == 0) {
            return;
        }
        Set<Long> ids = new HashSet<>();
        for (long id : lineIds) {
            ids.add(id);
        }
        List<Product> keep = new ArrayList<>();
        for (Product line : LINES) {
            if (!ids.contains(line.getCartLineId())) {
                keep.add(line);
            }
        }
        LINES.clear();
        LINES.addAll(keep);
        save(context);
    }

    // =========================
    // 계정이 바뀔 때 (LoginSession·EditProfileActivity가 부른다)
    // =========================

    /** 계정 이름이 바뀌었다(이메일·전화번호 변경): 장바구니를 새 이름으로 옮긴다 */
    public static synchronized void moveAccount(Context context, String from, String to) {
        if (from.equals(to)) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        String saved = prefs.getString(key(from), null);
        if (saved == null) {
            return;
        }
        prefs.edit().putString(key(to), saved).remove(key(from)).apply();
        loadedAccount = null;   // 다음에 읽을 때 새 이름으로 다시 불러온다
    }

    /** 탈퇴: 이 계정의 장바구니를 지운다 (메모리에 불러온 것도 비운다) */
    public static synchronized void deleteAccount(Context context, String account) {
        prefs(context).edit().remove(key(account)).apply();
        if (account.equals(loadedAccount)) {
            LINES.clear();
            loadedAccount = null;
        }
    }

    /** 예전 형식 이름(전화번호 하이픈 차이 등)의 장바구니를, 새 이름에 장바구니가 없을 때만 옮긴다 */
    public static synchronized void adoptAccount(Context context, String from, String to) {
        if (from.equals(to) || prefs(context).contains(key(to))) {
            return;
        }
        moveAccount(context, from, to);
    }

    /** 테스트용: 메모리에 불러온 장바구니를 잊는다 (앱을 껐다 켠 것과 같다). 다음에 읽을 때 저장소에서 다시 불러온다 */
    static synchronized void forgetLoadedForTest() {
        loadedAccount = null;
        LINES.clear();
    }

    // =========================
    // 내부
    // =========================

    private static void ensureLoaded(Context context) {
        String account = BodyProfileStore.accountKey(context);
        if (account.equals(loadedAccount)) {
            return;
        }
        LINES.clear();
        LINES.addAll(read(context, account));
        loadedAccount = account;

        // 비로그인으로 담아 둔 장바구니는 로그인한 계정으로 합친다 (같은 옵션은 수량을 더한다)
        if (!GUEST.equals(account)) {
            List<Product> guest = read(context, GUEST);
            if (!guest.isEmpty()) {
                for (Product line : guest) {
                    Product same = findOption(line, line.getSelectedSize(), line.getSelectedColor(), 0L);
                    if (same == null) {
                        LINES.add(line);
                    } else {
                        same.setCartQuantity(clampQuantity(same.getCartQuantity() + line.getCartQuantity()));
                    }
                }
                prefs(context).edit().remove(key(GUEST)).apply();
                save(context);
            }
        }
    }

    private static List<Product> read(Context context, String account) {
        List<Product> lines = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(key(account), "[]"));
            for (int i = 0; i < array.length(); i++) {
                Product line = fromJson(array.getJSONObject(i));
                if (line != null) {
                    lines.add(line);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "장바구니를 불러오지 못했습니다", e);
        }
        return lines;
    }

    private static void save(Context context) {
        if (loadedAccount == null) {
            return;
        }
        JSONArray array = new JSONArray();
        try {
            for (Product line : LINES) {
                array.put(toJson(line));
            }
        } catch (JSONException e) {
            Log.e(TAG, "장바구니를 저장하지 못했습니다", e);
            return;
        }
        prefs(context).edit().putString(key(loadedAccount), array.toString()).apply();
    }

    private static JSONObject toJson(Product line) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("line_id", line.getCartLineId());
        j.put("product_id", line.getId());
        j.put("server_id", line.getServerId());
        // 앱을 다시 켰을 때 상품 목록에서 못 찾으면(서버 상품을 아직 안 받았을 때 등) 이 값으로 보여 준다
        j.put("brand", line.getBrand());
        j.put("name", line.getName());
        j.put("price", line.getPrice());
        j.put("category", line.getCategory());
        j.put("image_url", line.getImageUrl());
        j.put("size", line.getSelectedSize());
        j.put("color", line.getSelectedColor());
        j.put("quantity", line.getCartQuantity());
        j.put("selected", line.isCartSelected());
        return j;
    }

    private static Product fromJson(JSONObject j) {
        long lineId = j.optLong("line_id", 0L);
        if (lineId <= 0L) {
            return null;
        }
        String serverId = j.optString("server_id", "");
        Product source = serverId.isEmpty()
                ? DummyData.getProductById(j.optInt("product_id", -1))
                : RecommendApi.serverProduct(serverId);
        if (source == null) {
            source = new Product(j.optInt("product_id", -1), j.optString("brand"), j.optString("name"),
                    j.optInt("price", 0));
            source.setFilterInfo(j.optString("category"), null, null, null);
            source.setServerInfo(serverId, "");
            source.setImageUrl(j.optString("image_url"));
        }
        return source.copyForCart(lineId, j.optString("size"), j.optString("color"),
                clampQuantity(j.optInt("quantity", 1)), j.optBoolean("selected", true));
    }

    private static Product find(long lineId) {
        for (Product line : LINES) {
            if (line.getCartLineId() == lineId) {
                return line;
            }
        }
        return null;
    }

    /** 같은 상품·같은 옵션인 줄 (exceptLineId 줄은 빼고 찾는다) */
    private static Product findOption(Product product, String size, String color, long exceptLineId) {
        String s = size == null ? "" : size;
        String c = color == null ? "" : color;
        for (Product line : LINES) {
            if (line.getCartLineId() != exceptLineId && sameProduct(line, product)
                    && s.equals(line.getSelectedSize()) && c.equals(line.getSelectedColor())) {
                return line;
            }
        }
        return null;
    }

    /** 서버 상품은 서버 번호로, 앱 상품(DummyData)은 상품 번호로 같은 상품인지 본다 */
    private static boolean sameProduct(Product a, Product b) {
        if (a.isFromServer() || b.isFromServer()) {
            return a.getServerId().equals(b.getServerId());
        }
        return a.getId() == b.getId();
    }

    private static int clampQuantity(int quantity) {
        return Math.max(1, Math.min(MAX_QUANTITY, quantity));
    }

    private static long nextLineId(Context context) {
        SharedPreferences prefs = prefs(context);
        long id = Math.max(1L, prefs.getLong(KEY_NEXT_ID, 1L));
        for (Product line : LINES) {
            id = Math.max(id, line.getCartLineId() + 1);
        }
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).apply();
        return id;
    }

    private static String key(String account) {
        return "cart@" + account;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
