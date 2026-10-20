package com.example.capstone.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.data.Review;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final List<Review> reviewList;

    public ReviewAdapter(List<Review> reviewList) {
        this.reviewList = reviewList;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_review,
                        parent,
                        false
                );

        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ReviewViewHolder holder,
            int position
    ) {

        Review review = reviewList.get(position);

        holder.tvReviewTitle.setText(
                review.getTitle()
        );

        holder.tvReviewContent.setText(
                review.getContent()
        );

        holder.tvReviewRating.setText(
                makeStars(review.getRating())
                        + "  "
                        + review.getRating()
        );
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    private String makeStars(float rating) {

        int starCount = Math.round(rating);

        StringBuilder stars = new StringBuilder();

        for (int i = 0; i < 5; i++) {

            if (i < starCount) {
                stars.append("★");
            } else {
                stars.append("☆");
            }
        }

        return stars.toString();
    }

    static class ReviewViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvReviewRating;
        TextView tvReviewTitle;
        TextView tvReviewContent;

        public ReviewViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            tvReviewRating =
                    itemView.findViewById(
                            R.id.tvReviewRating
                    );

            tvReviewTitle =
                    itemView.findViewById(
                            R.id.tvReviewTitle
                    );

            tvReviewContent =
                    itemView.findViewById(
                            R.id.tvReviewContent
                    );
        }
    }
}