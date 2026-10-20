package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

public class CategoryMenuActivity extends AppCompatActivity {

    private static final int BLACK = Color.rgb(17, 17, 17);
    private static final int GRAY = Color.rgb(120, 120, 120);
    private static final int BORDER = Color.rgb(235, 235, 235);
    private static final int SIDE_BG = Color.rgb(246, 246, 246);

    private static final String[] CATEGORIES = {
            "상의", "바지", "아우터", "원피스",
            "스커트", "수영복", "트레이닝",
            "신발", "가방·잡화"
    };

    private static final String[][] SUBCATEGORIES = {
            {
                    "전체 상의", "반팔 티셔츠",
                    "긴팔 티셔츠", "셔츠·블라우스",
                    "니트", "후드", "맨투맨", "민소매"
            },
            {
                    "전체 바지", "데님", "슬랙스",
                    "와이드 팬츠", "숏팬츠",
                    "트레이닝 팬츠", "코튼 팬츠"
            },
            {
                    "전체 아우터", "자켓", "가디건",
                    "집업·점퍼", "코트",
                    "패딩", "바람막이"
            },
            {
                    "전체 원피스", "미니 원피스",
                    "미디 원피스", "롱 원피스",
                    "니트 원피스"
            },
            {
                    "전체 스커트", "미니 스커트",
                    "미디 스커트", "롱 스커트",
                    "데님 스커트"
            },
            {
                    "전체 수영복", "비키니",
                    "원피스 수영복", "래시가드"
            },
            {
                    "전체 트레이닝", "트레이닝 상의",
                    "트레이닝 하의", "레깅스",
                    "스포츠웨어"
            },
            {
                    "전체 신발", "운동화",
                    "스니커즈", "로퍼",
                    "샌들", "부츠"
            },
            {
                    "전체 가방·잡화", "가방",
                    "모자", "벨트", "양말"
            }
    };

    private LinearLayout leftMenu;
    private LinearLayout rightMenu;
    private TextView rightTitle;

    private int selectedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createScreen();

        BottomMenu.setup(this, BottomMenu.TAB_CATEGORY);
        showCategory(0);
    }

    private void createScreen() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        setContentView(root);

        // 상단 제목
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), 0, dp(16), 0);

        root.addView(
                toolbar,
                new LinearLayout.LayoutParams(-1, dp(52))
        );

        TextView title = makeText(
                "카테고리", 19, true, BLACK
        );

        toolbar.addView(
                title,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView search = makeText(
                "⌕", 28, false, BLACK
        );

        search.setGravity(Gravity.CENTER);

        toolbar.addView(
                search,
                new LinearLayout.LayoutParams(dp(42), -1)
        );

        search.setOnClickListener(v ->
                startActivity(
                        new Intent(this, SearchActivity.class)
                )
        );

        root.addView(divider());

        // 가운데 카테고리 영역
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);

        root.addView(
                content,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        // 왼쪽 카테고리
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        leftScroll.setVerticalScrollBarEnabled(false);
        leftScroll.setBackgroundColor(SIDE_BG);

        content.addView(
                leftScroll,
                new LinearLayout.LayoutParams(dp(108), -1)
        );

        leftMenu = new LinearLayout(this);
        leftMenu.setOrientation(LinearLayout.VERTICAL);

        leftScroll.addView(leftMenu);

        // 오른쪽 소분류
        ScrollView rightScroll = new ScrollView(this);
        rightScroll.setFillViewport(true);
        rightScroll.setVerticalScrollBarEnabled(false);

        content.addView(
                rightScroll,
                new LinearLayout.LayoutParams(0, -1, 1)
        );

        rightMenu = new LinearLayout(this);
        rightMenu.setOrientation(LinearLayout.VERTICAL);
        rightMenu.setPadding(dp(14), dp(12), dp(14), dp(20));

        rightScroll.addView(rightMenu);

        for (int i = 0; i < CATEGORIES.length; i++) {

            final int index = i;

            TextView item = makeText(
                    CATEGORIES[i], 14, false, GRAY
            );

            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), 0, dp(4), 0);

            leftMenu.addView(
                    item,
                    new LinearLayout.LayoutParams(-1, dp(58))
            );

            item.setOnClickListener(v -> showCategory(index));
        }

        // 기존 하단 메뉴 사용
        getLayoutInflater().inflate(
                R.layout.view_bottom_menu,
                root,
                true
        );
    }

    private void showCategory(int index) {

        selectedIndex = index;

        // 왼쪽 선택 상태 변경
        for (int i = 0; i < leftMenu.getChildCount(); i++) {

            TextView item = (TextView) leftMenu.getChildAt(i);

            boolean selected = i == index;

            item.setBackgroundColor(
                    selected ? Color.WHITE : SIDE_BG
            );

            item.setTextColor(
                    selected ? BLACK : GRAY
            );

            item.setTypeface(
                    null,
                    selected ? Typeface.BOLD : Typeface.NORMAL
            );
        }

        rightMenu.removeAllViews();

        String category = CATEGORIES[index];

        // 오른쪽 대분류 제목
        rightTitle = makeText(
                category + " ›", 20, true, BLACK
        );

        rightTitle.setPadding(0, dp(8), 0, dp(22));

        rightMenu.addView(rightTitle);

        rightTitle.setOnClickListener(v ->
                openProducts(category, "전체")
        );

        // 소분류 2열 배치
        String[] items = SUBCATEGORIES[index];

        for (int i = 0; i < items.length; i += 2) {

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            rightMenu.addView(
                    row,
                    new LinearLayout.LayoutParams(-1, dp(58))
            );

            addSubcategory(row, category, items[i]);

            if (i + 1 < items.length) {
                addSubcategory(row, category, items[i + 1]);
            } else {
                View blank = new View(this);
                row.addView(
                        blank,
                        new LinearLayout.LayoutParams(0, -1, 1)
                );
            }
        }
    }

    private void addSubcategory(
            LinearLayout row,
            String category,
            String subcategory
    ) {

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams cellParams =
                new LinearLayout.LayoutParams(0, -1, 1);

        cellParams.setMargins(0, 0, dp(6), 0);

        row.addView(cell, cellParams);

        TextView text = makeText(
                subcategory + " ›", 12, false, BLACK
        );

        text.setGravity(Gravity.CENTER_VERTICAL);

        cell.addView(
                text,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        cell.addView(divider());

        cell.setOnClickListener(v -> {

            String selectedSubcategory = subcategory;

            if (subcategory.startsWith("전체")) {
                selectedSubcategory = "전체";
            }

            openProducts(category, selectedSubcategory);
        });
    }

    private void openProducts(
            String category,
            String subcategory
    ) {

        Intent intent = new Intent(
                this,
                CategoryActivity.class
        );

        intent.putExtra(
                CategoryActivity.EXTRA_CATEGORY,
                category
        );

        intent.putExtra(
                CategoryActivity.EXTRA_SUBCATEGORY,
                subcategory
        );

        startActivity(intent);
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

    private View divider() {

        View view = new View(this);
        view.setBackgroundColor(BORDER);

        view.setLayoutParams(
                new LinearLayout.LayoutParams(-1, dp(1))
        );

        return view;
    }

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }
}
