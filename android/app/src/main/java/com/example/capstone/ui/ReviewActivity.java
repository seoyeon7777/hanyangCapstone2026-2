package com.example.capstone.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReviewActivity extends AppCompatActivity {

    private static final int BLACK = 0xFF111111;
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = 0xFF999999;
    private static final int LIGHT = 0xFFF5F5F5;
    private static final int BORDER = 0xFFE8E8E8;

    private int productId;
    private boolean satisfiedOnly = false;

    private final List<JSONObject> reviews =
            new ArrayList<>();

    private LinearLayout root;
    private FrameLayout contentFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        productId = getIntent().getIntExtra(
                "product_id",
                -1
        );

        createScreen();
        loadReviews();
        renderScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadReviews();
        renderScreen();
    }

    // =====================================
    // 전체 화면 생성
    // =====================================

    private void createScreen() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WHITE);

        setContentView(root);

        // 상단 헤더
        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(WHITE);

        header.setPadding(
                dp(12),
                0,
                dp(12),
                0
        );

        root.addView(
                header,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(56)
                )
        );

        TextView back = makeText(
                "‹",
                32,
                false,
                BLACK
        );

        back.setGravity(Gravity.CENTER);

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(44),
                        -1
                )
        );

        back.setOnClickListener(v -> finish());

        TextView title = makeText(
                "상품 리뷰",
                17,
                true,
                BLACK
        );

        title.setGravity(Gravity.CENTER);

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1f
                )
        );

        // 제목 중앙 정렬용 빈 공간
        header.addView(
                new View(this),
                new LinearLayout.LayoutParams(
                        dp(44),
                        -1
                )
        );

        root.addView(divider());

        // 핵심: 화면 나머지 공간을
        // FrameLayout이 정확히 차지하도록 구성
        contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundColor(WHITE);

        root.addView(
                contentFrame,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1f
                )
        );
    }

    // =====================================
    // 상품별 리뷰 불러오기
    // =====================================

    private void loadReviews() {

        reviews.clear();

        SharedPreferences prefs =
                getSharedPreferences(
                        "capstone_reviews",
                        MODE_PRIVATE
                );

        String json = prefs.getString(
                "reviews_product_" + productId,
                "[]"
        );

        try {
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {
                reviews.add(
                        array.getJSONObject(i)
                );
            }

        } catch (Exception ignored) {
        }
    }

    // =====================================
    // 리뷰 유무에 따라 화면 전환
    // =====================================

    private void renderScreen() {

        contentFrame.removeAllViews();

        if (reviews.isEmpty()) {
            renderEmptyReviews();
        } else {
            renderReviewList();
        }
    }

    // =====================================
    // 리뷰가 없을 때
    // =====================================

    private void renderEmptyReviews() {

        LinearLayout empty =
                new LinearLayout(this);

        empty.setOrientation(
                LinearLayout.VERTICAL
        );

        empty.setGravity(Gravity.CENTER);
        empty.setBackgroundColor(WHITE);

        // FrameLayout 전체 높이를 사용하므로
        // 기기 크기와 관계없이 정중앙에 표시됨
        contentFrame.addView(
                empty,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        TextView icon = makeText(
                "▤",
                46,
                false,
                0xFFCCCCCC
        );

        icon.setGravity(Gravity.CENTER);

        empty.addView(
                icon,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(72)
                )
        );

        TextView message = makeText(
                "아직 등록된 리뷰가 없어요",
                15,
                true,
                BLACK
        );

        message.setGravity(Gravity.CENTER);

        empty.addView(
                message,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(34)
                )
        );

        TextView description = makeText(
                "첫 번째 리뷰를 기다리고 있어요.",
                12,
                false,
                GRAY
        );

        description.setGravity(Gravity.CENTER);

        empty.addView(
                description,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(28)
                )
        );
    }

    // =====================================
    // 리뷰가 있을 때
    // =====================================

    private void renderReviewList() {

        ScrollView scroll =
                new ScrollView(this);

        scroll.setBackgroundColor(WHITE);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        contentFrame.addView(
                scroll,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setBackgroundColor(WHITE);

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        int total = reviews.size();
        int satisfiedCount = 0;
        float ratingTotal = 0;

        for (JSONObject review : reviews) {

            float rating = (float)
                    review.optDouble("rating", 0);

            ratingTotal += rating;

            if (rating >= 4) {
                satisfiedCount++;
            }
        }

        int percentage = Math.round(
                satisfiedCount * 100f / total
        );

        // =================================
        // 만족도 요약
        // =================================

        LinearLayout summary =
                new LinearLayout(this);

        summary.setOrientation(
                LinearLayout.VERTICAL
        );

        summary.setPadding(
                dp(20),
                dp(26),
                dp(20),
                dp(24)
        );

        content.addView(summary);

        TextView satisfaction = makeText(
                percentage + "%가 만족했어요",
                24,
                true,
                BLACK
        );

        summary.addView(satisfaction);

        TextView count = makeText(
                "리뷰 " + total + "개",
                13,
                false,
                GRAY
        );

        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        countParams.topMargin = dp(12);

        summary.addView(
                count,
                countParams
        );

        TextView average = makeText(
                String.format(
                        Locale.KOREA,
                        "평균 평점 %.1f / 5.0",
                        ratingTotal / total
                ),
                13,
                false,
                GRAY
        );

        LinearLayout.LayoutParams avgParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        avgParams.topMargin = dp(7);

        summary.addView(
                average,
                avgParams
        );

        // 리뷰 사진이나 색상/사이즈 통계는
        // 해당 데이터가 있을 때 추가 가능

        View sectionGap = new View(this);
        sectionGap.setBackgroundColor(LIGHT);

        content.addView(
                sectionGap,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(10)
                )
        );

        // =================================
        // 리뷰 필터
        // =================================

        LinearLayout filterRow =
                new LinearLayout(this);

        filterRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        filterRow.setPadding(
                dp(16),
                dp(18),
                dp(16),
                dp(18)
        );

        content.addView(filterRow);

        TextView allChip = createChip(
                "전체 리뷰",
                !satisfiedOnly
        );

        TextView satisfiedChip = createChip(
                "만족 리뷰",
                satisfiedOnly
        );

        LinearLayout.LayoutParams allParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                );

        allParams.rightMargin = dp(8);

        filterRow.addView(
                allChip,
                allParams
        );

        filterRow.addView(
                satisfiedChip,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f
                )
        );

        allChip.setOnClickListener(v -> {
            satisfiedOnly = false;
            renderScreen();
        });

        satisfiedChip.setOnClickListener(v -> {
            satisfiedOnly = true;
            renderScreen();
        });

        content.addView(divider());

        // =================================
        // 리뷰 카드 목록
        // =================================

        int visibleCount = 0;

        for (JSONObject review : reviews) {

            float rating = (float)
                    review.optDouble("rating", 0);

            if (satisfiedOnly && rating < 4) {
                continue;
            }

            addReviewCard(content, review);
            visibleCount++;
        }

        if (visibleCount == 0) {

            LinearLayout noMatch =
                    new LinearLayout(this);

            noMatch.setGravity(Gravity.CENTER);

            content.addView(
                    noMatch,
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(200)
                    )
            );

            TextView message = makeText(
                    "조건에 맞는 리뷰가 없어요",
                    14,
                    false,
                    GRAY
            );

            noMatch.addView(message);
        }
    }

    // =====================================
    // 개별 리뷰 카드
    // =====================================

    private void addReviewCard(
            LinearLayout parent,
            JSONObject review
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(20),
                dp(22),
                dp(20),
                dp(24)
        );

        parent.addView(
                card,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        String title = review.optString(
                "title",
                "구매 후기"
        );

        String content = review.optString(
                "content",
                ""
        );

        String date = review.optString(
                "date",
                ""
        );

        float rating = (float)
                review.optDouble("rating", 0);

        // 리뷰 제목 + BEST
        LinearLayout titleRow =
                new LinearLayout(this);

        titleRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        card.addView(titleRow);

        TextView reviewTitle = makeText(
                title,
                17,
                true,
                BLACK
        );

        titleRow.addView(reviewTitle);

        if (rating >= 4.5f) {

            TextView best = makeText(
                    "BEST",
                    11,
                    true,
                    BLACK
            );

            best.setGravity(Gravity.CENTER);

            best.setBackground(
                    rounded(
                            LIGHT,
                            LIGHT,
                            5
                    )
            );

            LinearLayout.LayoutParams bestParams =
                    new LinearLayout.LayoutParams(
                            dp(48),
                            dp(25)
                    );

            bestParams.leftMargin = dp(8);

            titleRow.addView(
                    best,
                    bestParams
            );
        }

        TextView metadata = makeText(
                "구매자 | " + date,
                12,
                false,
                GRAY
        );

        LinearLayout.LayoutParams metaParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        metaParams.topMargin = dp(10);

        card.addView(
                metadata,
                metaParams
        );

        // 별점은 입력이 아닌
        // 등록된 리뷰의 평점 표시
        TextView stars = makeText(
                createStars(rating),
                17,
                false,
                BLACK
        );

        LinearLayout.LayoutParams starParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        starParams.topMargin = dp(12);

        card.addView(
                stars,
                starParams
        );

        TextView reviewBody = makeText(
                content,
                14,
                false,
                BLACK
        );

        reviewBody.setLineSpacing(
                dp(5),
                1f
        );

        LinearLayout.LayoutParams bodyParams =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        bodyParams.topMargin = dp(18);

        card.addView(
                reviewBody,
                bodyParams
        );

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(1)
                );

        dividerParams.topMargin = dp(24);

        card.addView(
                divider(),
                dividerParams
        );
    }

    // =====================================
    // UI 공통 함수
    // =====================================

    private String createStars(float rating) {

        int filled = Math.round(rating);

        StringBuilder builder =
                new StringBuilder();

        for (int i = 0; i < 5; i++) {
            builder.append(
                    i < filled ? "★" : "☆"
            );
        }

        return builder.toString();
    }

    private TextView createChip(
            String label,
            boolean selected
    ) {

        TextView chip = makeText(
                label,
                13,
                selected,
                selected ? WHITE : BLACK
        );

        chip.setGravity(Gravity.CENTER);

        chip.setBackground(
                rounded(
                        selected ? BLACK : LIGHT,
                        selected ? BLACK : LIGHT,
                        22
                )
        );

        return chip;
    }

    private TextView makeText(
            String value,
            int size,
            boolean bold,
            int color
    ) {

        TextView text = new TextView(this);

        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);

        if (bold) {
            text.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return text;
    }

    private GradientDrawable rounded(
            int background,
            int stroke,
            int radius
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(background);

        drawable.setCornerRadius(
                dp(radius)
        );

        drawable.setStroke(
                dp(1),
                stroke
        );

        return drawable;
    }

    private View divider() {

        View line = new View(this);
        line.setBackgroundColor(BORDER);

        line.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1,
                        dp(1)
                )
        );

        return line;
    }

    private int dp(float value) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
