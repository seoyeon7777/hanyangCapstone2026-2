package com.example.capstone.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Product {

    private int id;
    private String brand;
    private String name;
    private int price;

    private boolean liked;
    private boolean inCart;
    private int cartQuantity;
    private boolean cartSelected = true;

    // 장바구니에서 고객이 선택한 옵션
    private String selectedSize;
    private String selectedColor;

    // 장바구니 한 줄의 번호 (CartStore가 만든 장바구니용 복사본만 0보다 크다)
    private long cartLineId = 0L;

    // 상품 검색 및 필터용 정보
    private String category;
    private final List<String> colors = new ArrayList<>();
    private final List<String> styles = new ArrayList<>();
    private final List<String> seasons = new ArrayList<>();

    // 추천 서버에서 받은 상품만 채워진다 (DummyData 상품은 빈 문자열)
    private String serverId = "";
    private String reason = "";
    private String imageUrl = "";
    private float popularity = -1f;

    public Product(int id, String brand, String name, int price) {
        this.id = id;
        this.brand = brand;
        this.name = name;
        this.price = price;

        this.liked = false;
        this.inCart = false;
        this.cartQuantity = 1;
        this.selectedSize = "";
        this.selectedColor = "";
        this.category = "";
    }

    public int getId() {
        return id;
    }

    public String getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public boolean isLiked() {
        return liked;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

    public boolean isInCart() {
        return inCart;
    }

    public void setInCart(boolean inCart) {
        this.inCart = inCart;
    }

    public int getCartQuantity() {
        return cartQuantity;
    }

    public void setCartQuantity(int cartQuantity) {
        this.cartQuantity = cartQuantity;
    }

    public boolean isCartSelected() {
        return cartSelected;
    }

    public void setCartSelected(boolean cartSelected) {
        this.cartSelected = cartSelected;
    }

    public String getSelectedSize() {
        return selectedSize;
    }

    public void setSelectedSize(String selectedSize) {
        this.selectedSize = selectedSize;
    }

    public String getSelectedColor() {
        return selectedColor;
    }

    public void setSelectedColor(String selectedColor) {
        this.selectedColor = selectedColor;
    }

    /** "L / NAVY" 모양의 선택 옵션. 고른 옵션이 없으면 빈 문자열 */
    public String getOptionLabel() {
        boolean hasSize = selectedSize != null && !selectedSize.isEmpty();
        boolean hasColor = selectedColor != null && !selectedColor.isEmpty();

        if (hasSize && hasColor) {
            return selectedSize + " / " + selectedColor;
        }

        return hasSize ? selectedSize : hasColor ? selectedColor : "";
    }

    public long getCartLineId() {
        return cartLineId;
    }

    /**
     * 장바구니 한 줄로 쓸 복사본 (CartStore가 부른다).
     * 같은 상품도 옵션이 다르면 줄이 따로 있어야 해서, 상품 정보는 그대로 두고 장바구니 값만 따로 갖는다.
     */
    public Product copyForCart(long lineId, String size, String color, int quantity, boolean selected) {
        Product line = new Product(id, brand, name, price);
        line.setFilterInfo(category,
                colors.toArray(new String[0]), styles.toArray(new String[0]), seasons.toArray(new String[0]));
        line.setServerInfo(serverId, reason);
        line.setImageUrl(imageUrl);
        line.setPopularity(popularity);
        line.liked = liked;
        line.cartLineId = lineId;
        line.inCart = true;
        line.selectedSize = size == null ? "" : size;
        line.selectedColor = color == null ? "" : color;
        line.cartQuantity = quantity;
        line.cartSelected = selected;
        return line;
    }

    /** 장바구니에서 뺀다 (삭제·결제 완료) */
    public void removeFromCart() {
        inCart = false;
        cartQuantity = 1;
        cartSelected = true;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getColors() {
        return new ArrayList<>(colors);
    }

    public List<String> getStyles() {
        return new ArrayList<>(styles);
    }

    public List<String> getSeasons() {
        return new ArrayList<>(seasons);
    }

    public String getServerId() {
        return serverId;
    }

    /** 추천 서버가 붙여 준 추천 이유 문장 */
    public String getReason() {
        return reason;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public float getPopularity() {
        return popularity;
    }

    public void setPopularity(float popularity) {
        this.popularity = popularity;
    }

    public boolean isFromServer() {
        return !serverId.isEmpty();
    }

    // 추천 서버 상품 정보 설정 (RecommendApi가 호출)
    public void setServerInfo(String serverId, String reason) {
        this.serverId = serverId == null ? "" : serverId;
        this.reason = reason == null ? "" : reason;
    }

    /**
     * 서버에서 다시 받은 이름·브랜드·가격으로 바꾼다 (RecommendApi가 호출).
     * 찜·장바구니 상태는 그대로 둔다. 하나라도 바뀌었으면 true
     */
    public boolean updateServerDetails(String brand, String name, int price) {
        boolean changed = !this.brand.equals(brand) || !this.name.equals(name) || this.price != price;
        this.brand = brand;
        this.name = name;
        this.price = price;
        return changed;
    }

    // 상품 필터 정보 설정
    public void setFilterInfo(
            String category,
            String[] colors,
            String[] styles,
            String[] seasons
    ) {
        this.category = category == null ? "" : category;

        this.colors.clear();
        this.styles.clear();
        this.seasons.clear();

        if (colors != null) {
            this.colors.addAll(Arrays.asList(colors));
        }

        if (styles != null) {
            this.styles.addAll(Arrays.asList(styles));
        }

        if (seasons != null) {
            this.seasons.addAll(Arrays.asList(seasons));
        }
    }
}
