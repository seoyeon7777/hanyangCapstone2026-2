package com.example.capstone.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.capstone.login.LoginSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShopStore {

    public static final int FREE_SHIPPING_MIN = 50000;
    public static final int SHIPPING_FEE = 3000;
    public static final int MAX_QUANTITY = 99;

    private static final String PREF = "shop_store";
    private static final String GUEST = "guest";

    public static final class CartItem {
        public final long id;
        public int productId;
        public String serverId = "";
        public String brand = "";
        public String name = "";
        public String category = "";
        public int price;
        public String size = "";
        public String color = "";
        public int quantity = 1;
        public boolean selected = true;

        CartItem(long id) {
            this.id = id;
        }

        public Product product() {
            if (!serverId.isEmpty()) {
                return RecommendApi.rememberServerProduct(serverId, brand, name, price, category);
            }
            Product product = DummyData.getProductById(productId);
            if (product != null) {
                return product;
            }
            Product snapshot = new Product(productId, brand, name, price);
            snapshot.setFilterInfo(category, null, null, null);
            return snapshot;
        }

        public int totalPrice() {
            return price * quantity;
        }

        public String optionText() {
            StringBuilder sb = new StringBuilder();
            if (!size.isEmpty()) {
                sb.append(size);
            }
            if (!color.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(color);
            }
            return sb.length() == 0 ? "옵션 없음" : sb.toString();
        }

        boolean sameOption(Product product, String size, String color) {
            return sameProduct(product) && this.size.equals(size) && this.color.equals(color);
        }

        boolean sameProduct(Product product) {
            if (product.isFromServer()) {
                return product.getServerId().equals(serverId);
            }
            return serverId.isEmpty() && product.getId() == productId;
        }

        JSONObject toJson() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("productId", productId);
            j.put("serverId", serverId);
            j.put("brand", brand);
            j.put("name", name);
            j.put("category", category);
            j.put("price", price);
            j.put("size", size);
            j.put("color", color);
            j.put("quantity", quantity);
            j.put("selected", selected);
            return j;
        }

        static CartItem fromJson(JSONObject j) {
            CartItem item = new CartItem(j.optLong("id"));
            item.productId = j.optInt("productId");
            item.serverId = j.optString("serverId");
            item.brand = j.optString("brand");
            item.name = j.optString("name");
            item.category = j.optString("category");
            item.price = j.optInt("price");
            item.size = j.optString("size");
            item.color = j.optString("color");
            item.quantity = Math.max(1, j.optInt("quantity", 1));
            item.selected = j.optBoolean("selected", true);
            return item;
        }
    }

    private static final List<CartItem> CART = new ArrayList<>();
    private static final Set<Integer> LIKED = new LinkedHashSet<>();
    private static SharedPreferences prefs;
    private static String account = GUEST;
    private static long nextId = 1;

    private ShopStore() {
    }

    public static synchronized void init(Context context) {
        if (prefs != null) {
            return;
        }
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        nextId = prefs.getLong("next_id", 1);
        load(context);
    }

    public static synchronized void reload(Context context) {
        if (prefs == null) {
            init(context);
            return;
        }
        load(context);
    }

    private static String accountOf(Context context) {
        if (!LoginSession.isLoggedIn(context)) {
            return GUEST;
        }
        String id = LoginSession.getUserId(context);
        return id.isEmpty() ? BodyProfileStore.accountKey(context) : id;
    }

    private static void load(Context context) {
        String previous = account;
        account = accountOf(context);
        CART.clear();
        LIKED.clear();

        readCart(account, CART);
        readLiked(account, LIKED);

        if (!GUEST.equals(account) && GUEST.equals(previous)) {
            List<CartItem> guestCart = new ArrayList<>();
            readCart(GUEST, guestCart);
            for (CartItem item : guestCart) {
                CartItem same = null;
                for (CartItem mine : CART) {
                    if (mine.productId == item.productId && mine.serverId.equals(item.serverId)
                            && mine.size.equals(item.size) && mine.color.equals(item.color)) {
                        same = mine;
                    }
                }
                if (same == null) {
                    CART.add(item);
                } else {
                    same.quantity = Math.min(MAX_QUANTITY, same.quantity + item.quantity);
                }
            }
            Set<Integer> guestLiked = new LinkedHashSet<>();
            readLiked(GUEST, guestLiked);
            LIKED.addAll(guestLiked);
            prefs.edit().remove("cart@" + GUEST).remove("liked@" + GUEST).apply();
            save();
        }

        for (Product product : DummyData.getLocalProducts()) {
            product.setLiked(LIKED.contains(product.getId()));
        }
        RecommendApi.refreshLikes(context);
        syncCartFlags();
    }

    private static void readCart(String key, List<CartItem> into) {
        try {
            JSONArray array = new JSONArray(prefs.getString("cart@" + key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                CartItem item = CartItem.fromJson(array.getJSONObject(i));
                into.add(item);
                nextId = Math.max(nextId, item.id + 1);
            }
        } catch (Exception e) {
            Log.e("ShopStore", "장바구니 불러오기 실패", e);
        }
    }

    private static void readLiked(String key, Set<Integer> into) {
        try {
            JSONArray array = new JSONArray(prefs.getString("liked@" + key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                into.add(array.getInt(i));
            }
        } catch (Exception e) {
            Log.e("ShopStore", "찜 불러오기 실패", e);
        }
    }

    private static void save() {
        if (prefs == null) {
            return;
        }
        try {
            JSONArray cart = new JSONArray();
            for (CartItem item : CART) {
                cart.put(item.toJson());
            }
            prefs.edit()
                    .putString("cart@" + account, cart.toString())
                    .putString("liked@" + account, new JSONArray(LIKED).toString())
                    .putLong("next_id", nextId)
                    .apply();
        } catch (Exception e) {
            Log.e("ShopStore", "장바구니 저장 실패", e);
        }
        syncCartFlags();
    }

    private static void syncCartFlags() {
        for (Product product : DummyData.getProducts()) {
            product.setInCart(false);
        }
        for (CartItem item : CART) {
            item.product().setInCart(true);
        }
    }

    public static synchronized List<CartItem> getCart() {
        return new ArrayList<>(CART);
    }

    public static synchronized List<CartItem> getSelected() {
        List<CartItem> result = new ArrayList<>();
        for (CartItem item : CART) {
            if (item.selected) {
                result.add(item);
            }
        }
        return result;
    }

    public static synchronized CartItem find(long id) {
        for (CartItem item : CART) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    public static synchronized int cartCount() {
        int count = 0;
        for (CartItem item : CART) {
            count += item.quantity;
        }
        return count;
    }

    public static CartItem newItem(Product product, String size, String color, int quantity) {
        CartItem item = new CartItem(0);
        fill(item, product);
        item.size = size == null ? "" : size;
        item.color = color == null ? "" : color;
        item.quantity = Math.max(1, Math.min(MAX_QUANTITY, quantity));
        return item;
    }

    private static void fill(CartItem item, Product product) {
        item.productId = product.getId();
        item.serverId = product.getServerId();
        item.brand = product.getBrand();
        item.name = product.getName();
        item.category = product.getCategory();
        item.price = product.getPrice();
    }

    public static synchronized boolean add(Product product, String size, String color, int quantity) {
        String s = size == null ? "" : size;
        String c = color == null ? "" : color;
        for (CartItem item : CART) {
            if (item.sameOption(product, s, c)) {
                item.quantity = Math.min(MAX_QUANTITY, item.quantity + quantity);
                item.selected = true;
                fill(item, product);
                save();
                return false;
            }
        }
        CartItem item = new CartItem(nextId++);
        fill(item, product);
        item.size = s;
        item.color = c;
        item.quantity = Math.max(1, Math.min(MAX_QUANTITY, quantity));
        CART.add(0, item);
        save();
        return true;
    }

    public static synchronized boolean changeOption(CartItem target, String size, String color, int quantity) {
        Product product = target.product();
        for (CartItem item : CART) {
            if (item != target && item.sameOption(product, size, color)) {
                item.quantity = Math.min(MAX_QUANTITY, item.quantity + quantity);
                CART.remove(target);
                save();
                return true;
            }
        }
        target.size = size;
        target.color = color;
        target.quantity = Math.max(1, Math.min(MAX_QUANTITY, quantity));
        save();
        return false;
    }

    public static synchronized void setQuantity(CartItem item, int quantity) {
        item.quantity = Math.max(1, Math.min(MAX_QUANTITY, quantity));
        save();
    }

    public static synchronized void setSelected(CartItem item, boolean selected) {
        item.selected = selected;
        save();
    }

    public static synchronized void selectAll(boolean selected) {
        for (CartItem item : CART) {
            item.selected = selected;
        }
        save();
    }

    public static synchronized void remove(CartItem item) {
        CART.remove(item);
        save();
    }

    public static synchronized void removeIds(long[] ids) {
        if (ids == null) {
            return;
        }
        for (long id : ids) {
            CartItem item = find(id);
            if (item != null) {
                CART.remove(item);
            }
        }
        save();
    }

    public static synchronized void refreshPrices() {
        boolean changed = false;
        for (CartItem item : CART) {
            Product product = item.product();
            if (product.getPrice() > 0 && product.getPrice() != item.price) {
                item.price = product.getPrice();
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public static synchronized void deleteAccountData(String key) {
        if (prefs == null || key == null) {
            return;
        }
        prefs.edit().remove("cart@" + key).remove("liked@" + key).apply();
        if (key.equals(account)) {
            CART.clear();
            LIKED.clear();
            account = GUEST;
            syncCartFlags();
        }
    }

    public static int shippingFee(int productTotal) {
        if (productTotal <= 0 || productTotal >= FREE_SHIPPING_MIN) {
            return 0;
        }
        return SHIPPING_FEE;
    }

    public static synchronized void saveLike(Product product) {
        if (product == null || product.isFromServer()) {
            return;
        }
        if (product.isLiked()) {
            LIKED.add(product.getId());
        } else {
            LIKED.remove(product.getId());
        }
        save();
    }
}
