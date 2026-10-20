package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.adapter.ProductAdapter;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.Product;

import java.util.ArrayList;
import java.util.List;

public class WishActivity extends AppCompatActivity {

    private static final int BLACK = 0xFF111111;
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = 0xFF999999;
    private static final int LIGHT = 0xFFF5F5F5;
    private static final int BORDER = 0xFFE8E8E8;

    private RecyclerView recyclerWish;
    private FrameLayout contentFrame;
    private LinearLayout emptyView;
    private LinearLayout categoryContainer;

    private TextView titleCount;
    private TextView editButton;

    private ProductAdapter adapter;

    private final List<Product> wishProducts =
            new ArrayList<>();

    private String selectedCategory = "전체";
    private boolean editMode = false;

    private final String[] categories = {
            "전체", "상의", "아우터",
            "원피스", "바지", "치마"   // 치마: 추천 서버 상품(skirt)용
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_wish);

        recyclerWish = findViewById(R.id.recyclerWish);

        recyclerWish.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        // 기존 RecyclerView를 유지하면서
        // 화면 상단과 빈 상태 UI를 추가
        setupWishScreen();

        BottomMenu.setup(
                this,
                BottomMenu.TAB_WISH
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        showWishProducts();
    }

    private void setupWishScreen() {

        ViewGroup parent =
                (ViewGroup) recyclerWish.getParent();

        if (parent == null) {
            return;
        }

        int index =
                parent.indexOfChild(recyclerWish);

        ViewGroup.LayoutParams oldParams =
                recyclerWish.getLayoutParams();

        parent.removeView(recyclerWish);

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(WHITE);

        parent.addView(
                root,
                index,
                oldParams
        );

        // 상단 제목
        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.setPadding(
                dp(16), 0,
                dp(16), 0
        );

        root.addView(
                header,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(54)
                )
        );

        titleCount = makeText(
                "찜 목록 0",
                18,
                true,
                BLACK
        );

        titleCount.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.addView(
                titleCount,
                new LinearLayout.LayoutParams(
                        0,
                        -1,
                        1f
                )
        );

        editButton = makeText(
                "편집",
                13,
                false,
                GRAY
        );

        editButton.setGravity(
                Gravity.CENTER
        );

        editButton.setPadding(
                dp(8), 0,
                dp(8), 0
        );

        header.addView(editButton);

        editButton.setOnClickListener(v -> {
            editMode = !editMode;

            editButton.setText(
                    editMode ? "완료" : "편집"
            );

            renderProducts();
        });

        // 카테고리 가로 스크롤
        HorizontalScrollView categoryScroll =
                new HorizontalScrollView(this);

        categoryScroll.setHorizontalScrollBarEnabled(
                false
        );

        categoryScroll.setFillViewport(false);

        root.addView(
                categoryScroll,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(54)
                )
        );

        categoryContainer =
                new LinearLayout(this);

        categoryContainer.setGravity(
                Gravity.CENTER_VERTICAL
        );

        categoryContainer.setPadding(
                dp(12), 0,
                dp(12), 0
        );

        categoryScroll.addView(
                categoryContainer
        );

        // 상품 영역
        contentFrame =
                new FrameLayout(this);

        root.addView(
                contentFrame,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1f
                )
        );

        contentFrame.addView(
                recyclerWish,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );

        createEmptyView();

        renderCategories();
    }

    private void createEmptyView() {

        emptyView =
                new LinearLayout(this);

        emptyView.setOrientation(
                LinearLayout.VERTICAL
        );

        emptyView.setGravity(Gravity.CENTER);

        emptyView.setBackgroundColor(WHITE);

        ImageView heart = new ImageView(this);

        heart.setImageResource(R.drawable.ic_wish_empty);

        LinearLayout.LayoutParams heartParams =
                new LinearLayout.LayoutParams(
                        dp(56),
                        dp(56)
                );

        heartParams.bottomMargin = dp(10);

        emptyView.addView(heart, heartParams);

        TextView message = makeText(
                "찜한 상품이 없어요",
                14,
                false,
                GRAY
        );

        message.setGravity(Gravity.CENTER);

        emptyView.addView(message);

        TextView browse = makeText(
                "상품 둘러보기",
                13,
                false,
                BLACK
        );

        browse.setGravity(Gravity.CENTER);

        browse.setBackground(
                rounded(
                        WHITE,
                        BORDER,
                        22
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(126),
                        dp(42)
                );

        params.topMargin = dp(18);

        emptyView.addView(
                browse,
                params
        );

        browse.setOnClickListener(v -> {
            Intent intent =
                    new Intent(
                            this,
                            MainActivity.class
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            );

            startActivity(intent);
        });

        contentFrame.addView(
                emptyView,
                new FrameLayout.LayoutParams(
                        -1,
                        -1
                )
        );
    }

    private void renderCategories() {

        categoryContainer.removeAllViews();

        for (String category : categories) {

            int count = getCategoryCount(category);

            boolean selected =
                    selectedCategory.equals(category);

            TextView chip = makeText(
                    category + " " + count,
                    12,
                    selected,
                    selected ? WHITE : GRAY
            );

            chip.setGravity(Gravity.CENTER);

            chip.setPadding(
                    dp(14), 0,
                    dp(14), 0
            );

            chip.setBackground(
                    rounded(
                            selected ? BLACK : WHITE,
                            selected ? BLACK : BORDER,
                            22
                    )
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            -2,
                            dp(34)
                    );

            params.setMargins(
                    dp(4), 0,
                    dp(4), 0
            );

            categoryContainer.addView(
                    chip,
                    params
            );

            chip.setOnClickListener(v -> {
                selectedCategory = category;
                renderCategories();
                renderProducts();
            });
        }
    }

    private int getCategoryCount(String category) {

        int count = 0;

        for (Product product : allProducts()) {

            if (!product.isLiked()) {
                continue;
            }

            if (category.equals("전체")
                    || category.equals(
                    product.getCategory())) {

                count++;
            }
        }

        return count;
    }

    /** 찜 후보: 기존 더미 상품 + 홈(추천 서버)에서 찜한 상품 */
    private List<Product> allProducts() {

        List<Product> products = new ArrayList<>(DummyData.getProducts());
        products.addAll(RecommendApi.likedServerProducts(this));
        return products;
    }

    private void showWishProducts() {

        int total = getCategoryCount("전체");

        titleCount.setText(
                "찜 목록 " + total
        );

        editButton.setVisibility(
                total == 0
                        ? View.INVISIBLE
                        : View.VISIBLE
        );

        if (total == 0) {
            editMode = false;
            editButton.setText("편집");
        }

        renderCategories();
        renderProducts();
    }

    private void renderProducts() {

        wishProducts.clear();

        for (Product product : allProducts()) {

            if (!product.isLiked()) {
                continue;
            }

            if (!selectedCategory.equals("전체")
                    && !selectedCategory.equals(
                    product.getCategory())) {

                continue;
            }

            wishProducts.add(product);
        }

        boolean empty = wishProducts.isEmpty();

        emptyView.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );

        recyclerWish.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );

        if (empty) {
            return;
        }

        // 기존 상품 카드의 찜 기능 유지
        adapter = new ProductAdapter(
                wishProducts,
                true
        );

        recyclerWish.setAdapter(adapter);

        // 편집 모드에서는 상품을 길게 눌러 찜 해제
        if (editMode) {

            recyclerWish.addOnItemTouchListener(
                    new RecyclerView.SimpleOnItemTouchListener() {
                    }
            );
        }
    }

    private TextView makeText(
            String text,
            int size,
            boolean bold,
            int color
    ) {

        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
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

    private int dp(float value) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
