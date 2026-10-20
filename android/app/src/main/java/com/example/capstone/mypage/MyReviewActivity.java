package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.MyReview;
import com.example.capstone.data.MyPageStore.Order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** 마이페이지 > 내가 쓴 리뷰 (reviews) */
public class MyReviewActivity extends AppCompatActivity {

    private static final int TAB_WRITABLE = 0;
    private static final int TAB_WRITTEN = 1;

    private TextView tabWritable, tabWritten, tvEmpty;
    private View lineWritable, lineWritten;
    private LinearLayout containerWritable, containerWritten;
    private ScrollView scrollReview;

    /** 처음엔 "작성한 리뷰" 탭을 보여준다 (메뉴 이름이 '내가 쓴 리뷰'라서) */
    private int currentTab = TAB_WRITTEN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_review);

        MyPageUi.setupToolbar(this, "내가 쓴 리뷰");

        tabWritable = findViewById(R.id.tabWritable);
        tabWritten = findViewById(R.id.tabWritten);
        lineWritable = findViewById(R.id.lineWritable);
        lineWritten = findViewById(R.id.lineWritten);
        containerWritable = findViewById(R.id.containerWritable);
        containerWritten = findViewById(R.id.containerWritten);
        scrollReview = findViewById(R.id.scrollReview);
        tvEmpty = findViewById(R.id.tvEmpty);

        tabWritable.setOnClickListener(v -> showTab(TAB_WRITABLE));
        tabWritten.setOnClickListener(v -> showTab(TAB_WRITTEN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 작성/수정 화면에서 돌아오면 두 목록 모두 다시 그린다.
        showTab(currentTab);
    }

    private void showTab(int tab) {
        currentTab = tab;

        List<Order> writable = MyPageStore.getWritableReviewOrders();
        List<MyReview> written = MyPageStore.getMyReviews();

        tabWritable.setText("작성 가능한 리뷰 " + writable.size());
        tabWritten.setText("작성한 리뷰 " + written.size());
        MyPageUi.selectTab(new TextView[]{tabWritable, tabWritten},
                new View[]{lineWritable, lineWritten}, tab);

        containerWritable.setVisibility(tab == TAB_WRITABLE ? View.VISIBLE : View.GONE);
        containerWritten.setVisibility(tab == TAB_WRITTEN ? View.VISIBLE : View.GONE);

        if (tab == TAB_WRITABLE) {
            renderWritable(writable);
            tvEmpty.setText("작성 가능한 리뷰가 없습니다.");
            tvEmpty.setVisibility(writable.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            renderWritten(written);
            tvEmpty.setText("작성한 리뷰가 없습니다.");
            tvEmpty.setVisibility(written.isEmpty() ? View.VISIBLE : View.GONE);
        }

        scrollReview.post(() -> scrollReview.scrollTo(0, 0));
    }

    private void renderWritable(List<Order> orders) {
        containerWritable.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Order order : orders) {
            View view = inflater.inflate(R.layout.item_writable_review, containerWritable, false);
            ((TextView) view.findViewById(R.id.tvProductName)).setText(order.productName);
            ((TextView) view.findViewById(R.id.tvOption)).setText(order.option);
            ((TextView) view.findViewById(R.id.tvOrderDate)).setText(order.orderDate + " 구매");
            view.findViewById(R.id.btnWrite).setOnClickListener(v ->
                    startActivity(ReviewWriteActivity.newWriteIntent(this, order.orderNo)));
            containerWritable.addView(view);
        }
    }

    private void renderWritten(List<MyReview> reviews) {
        containerWritten.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (MyReview review : reviews) {
            View view = inflater.inflate(R.layout.item_my_review, containerWritten, false);
            ((TextView) view.findViewById(R.id.tvProductName)).setText(review.productName);
            ((TextView) view.findViewById(R.id.tvOption)).setText(review.option);
            ((RatingBar) view.findViewById(R.id.ratingBar)).setRating(review.rating);
            ((TextView) view.findViewById(R.id.tvDate)).setText(review.writtenDate);
            ((TextView) view.findViewById(R.id.tvContent)).setText(review.content);

            view.findViewById(R.id.btnEdit).setOnClickListener(v ->
                    startActivity(ReviewWriteActivity.newEditIntent(this, review.id)));

            view.findViewById(R.id.btnDelete).setOnClickListener(v ->
                    MyPageUi.confirm(this, "리뷰 삭제",
                            "삭제한 리뷰는 복구할 수 없습니다.\n삭제하시겠습니까?", () -> {
                                MyPageStore.deleteReview(review);
                                Toast.makeText(this, "리뷰가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                showTab(currentTab);
                            }));

            containerWritten.addView(view);
        }
    }
}
