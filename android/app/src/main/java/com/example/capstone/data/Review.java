package com.example.capstone.data;

public class Review {

    private int reviewId;
    private int productId;
    private String title;
    private String content;
    private float rating;

    public Review(
            int reviewId,
            int productId,
            String title,
            String content,
            float rating
    ) {
        this.reviewId = reviewId;
        this.productId = productId;
        this.title = title;
        this.content = content;
        this.rating = rating;
    }

    public int getReviewId() {
        return reviewId;
    }

    public int getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public float getRating() {
        return rating;
    }
}