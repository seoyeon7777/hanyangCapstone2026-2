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

    // 장바구니에서 고객이 선택한 옵션
    private String selectedSize;
    private String selectedColor;

    // 상품 검색 및 필터용 정보
    private String category;
    private final List<String> colors = new ArrayList<>();
    private final List<String> styles = new ArrayList<>();
    private final List<String> seasons = new ArrayList<>();

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
