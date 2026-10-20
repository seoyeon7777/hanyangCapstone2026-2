package com.example.capstone.data;

public class ProductOption {

    // =========================
    // ERD - product_options 테이블
    // =========================

    private int optionId;       // option_id
    private int productId;      // product_id
    private String size;        // size
    private String color;       // color
    private String createdAt;   // created_at
    private String updatedAt;   // updated_at


    // =========================
    // 기본 생성자
    // API / DB 연결할 때 사용
    // =========================

    public ProductOption() {
    }


    // =========================
    // 간단 생성자
    // =========================

    public ProductOption(
            int optionId,
            int productId,
            String size,
            String color
    ) {
        this.optionId = optionId;
        this.productId = productId;
        this.size = size;
        this.color = color;
    }


    // =========================
    // 전체 생성자
    // =========================

    public ProductOption(
            int optionId,
            int productId,
            String size,
            String color,
            String createdAt,
            String updatedAt
    ) {
        this.optionId = optionId;
        this.productId = productId;
        this.size = size;
        this.color = color;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }


    // =========================
    // Getter / Setter
    // =========================

    public int getOptionId() {
        return optionId;
    }

    public void setOptionId(int optionId) {
        this.optionId = optionId;
    }


    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }


    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }


    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }


    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }


    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}