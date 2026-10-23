package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.MyReview;
import com.example.capstone.data.MyPageStore.Order;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 리뷰 작성 / 수정 (reviews)
 * - 주문 내역, 내가 쓴 리뷰 > 작성 가능한 리뷰 에서 들어오면 새로 작성
 * - 내가 쓴 리뷰 > 수정 에서 들어오면 수정
 */
public class ReviewWriteActivity extends AppCompatActivity {

    private static final String EXTRA_ORDER_NO = "EXTRA_ORDER_NO";
    private static final String EXTRA_REVIEW_ID = "EXTRA_REVIEW_ID";

    private static final int CONTENT_MIN_LENGTH = 10;
    private static final int CONTENT_MAX_LENGTH = 500;

    private static final String[] RATING_LABELS = {
            "별점을 선택해주세요", "별로예요", "그저 그래요", "괜찮아요", "좋아요", "최고예요"
    };

    private RatingBar ratingBar;
    private TextView tvRatingLabel, tvContentCount;
    private EditText etContent;
    private Button btnSubmit;

    /** 새로 작성할 때의 주문 */
    private Order order;
    /** 수정할 때의 리뷰 */
    private MyReview review;

    public static Intent newWriteIntent(Context context, String orderNo) {
        Intent intent = new Intent(context, ReviewWriteActivity.class);
        intent.putExtra(EXTRA_ORDER_NO, orderNo);
        return intent;
    }

    public static Intent newEditIntent(Context context, long reviewId) {
        Intent intent = new Intent(context, ReviewWriteActivity.class);
        intent.putExtra(EXTRA_REVIEW_ID, reviewId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_write);

        long reviewId = getIntent().getLongExtra(EXTRA_REVIEW_ID, -1);
        if (reviewId != -1) {
            review = MyPageStore.findReview(reviewId);
        } else {
            order = MyPageStore.findOrder(getIntent().getStringExtra(EXTRA_ORDER_NO));
        }

        if (review == null && order == null) {
            Toast.makeText(this, "리뷰 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MyPageUi.setupToolbar(this, review != null ? "리뷰 수정" : "리뷰 작성");
        initViews();
        fillForm();
        setListeners();
    }

    private void initViews() {
        ratingBar = findViewById(R.id.ratingBar);
        tvRatingLabel = findViewById(R.id.tvRatingLabel);
        tvContentCount = findViewById(R.id.tvContentCount);
        etContent = findViewById(R.id.etContent);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void fillForm() {
        TextView tvProductName = findViewById(R.id.tvProductName);
        TextView tvOption = findViewById(R.id.tvOption);

        if (review != null) {
            tvProductName.setText(review.productName);
            tvOption.setText(review.option);
            ratingBar.setRating(review.rating);
            etContent.setText(review.content);
            btnSubmit.setText("수정하기");
        } else {
            tvProductName.setText(order.productName);
            tvOption.setText(order.option);
        }

        updateRatingLabel(ratingBar.getRating());
        updateContentCount(etContent.length());
    }

    private void setListeners() {
        ratingBar.setOnRatingBarChangeListener((bar, rating, fromUser) -> updateRatingLabel(rating));

        etContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContentCount(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSubmit.setOnClickListener(v -> submit());
    }

    private void updateRatingLabel(float rating) {
        tvRatingLabel.setText(RATING_LABELS[Math.round(rating)]);
    }

    private void updateContentCount(int length) {
        tvContentCount.setText(length + " / " + CONTENT_MAX_LENGTH);
    }

    private void submit() {
        float rating = ratingBar.getRating();
        String content = etContent.getText().toString().trim();

        if (rating < 1) {
            Toast.makeText(this, "별점을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() < CONTENT_MIN_LENGTH) {
            Toast.makeText(this, "리뷰를 " + CONTENT_MIN_LENGTH + "자 이상 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (review != null) {
            MyPageStore.updateReview(review, rating, content);
            Toast.makeText(this, "리뷰가 수정되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            MyPageStore.addReview(order, rating, content);
            Toast.makeText(this, "리뷰가 등록되었습니다.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
