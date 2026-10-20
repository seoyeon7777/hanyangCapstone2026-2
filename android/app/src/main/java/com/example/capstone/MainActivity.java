package com.example.capstone;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import androidx.core.widget.NestedScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.adapter.ProductAdapter;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.Product;
import com.example.capstone.ui.BottomMenu;
import com.example.capstone.ui.CategoryFilterDialog;
import com.example.capstone.ui.SearchActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int BLACK = Color.rgb(17, 17, 17);
    private static final int GRAY = Color.rgb(150, 150, 150);

    private static final String[] MENUS = {
            "홈", "신상", "베스트", "상의",
            "아우터", "원피스", "바지"
    };

    private final int[] TAB_IDS = {
            R.id.tabHome,
            R.id.tabNew,
            R.id.tabBest,
            R.id.tabTop,
            R.id.tabOuter,
            R.id.tabDress,
            R.id.tabPants
    };

    private final int[] INDICATOR_IDS = {
            R.id.indicatorHome,
            R.id.indicatorNew,
            R.id.indicatorBest,
            R.id.indicatorTop,
            R.id.indicatorOuter,
            R.id.indicatorDress,
            R.id.indicatorPants
    };

    private RecyclerView recyclerProducts;
    private NestedScrollView homeScrollView;

    private View homeBanner;
    private View homeRecommendationHeader;
    private View categoryResultHeader;

    private TextView textSelectedCategory;
    private TextView btnHomeFilter;
    private TextView textHomeEmpty;

    private ProductAdapter productAdapter;

    private String selectedMenu = "홈";

    private int minPrice = 0;
    private int maxPrice = 0;

    private String selectedColor = "";
    private String selectedStyles = "";

    private String selectedHeight = "";
    private String selectedWeight = "";
    private String selectedBodyType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        BodyProfileStore.syncActiveProfile(this);

        // XML 연결
        recyclerProducts = findViewById(
                R.id.recyclerProducts
        );

        homeScrollView = findViewById(
                R.id.homeScrollView
        );

        homeBanner = findViewById(
                R.id.homeBanner
        );

        homeRecommendationHeader = findViewById(
                R.id.homeRecommendationHeader
        );

        categoryResultHeader = findViewById(
                R.id.categoryResultHeader
        );

        textSelectedCategory = findViewById(
                R.id.textSelectedCategory
        );

        btnHomeFilter = findViewById(
                R.id.btnHomeFilter
        );

        textHomeEmpty = findViewById(
                R.id.textHomeEmpty
        );

        recyclerProducts.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        recyclerProducts.setNestedScrollingEnabled(false);

        // 하단 메뉴
        BottomMenu.setup(
                this,
                BottomMenu.TAB_HOME
        );

        // 검색창
        findViewById(R.id.editSearch)
                .setOnClickListener(v -> {
                    startActivity(
                            new Intent(
                                    this,
                                    SearchActivity.class
                            )
                    );
                });

        // 상단 메뉴
        setupTopMenus();

        // 필터 버튼
        btnHomeFilter.setOnClickListener(
                v -> showFilterDialog()
        );

        // 전체보기: 홈 안에서 전체 상품 보기
        findViewById(R.id.btnSeeAllProducts)
                .setOnClickListener(v -> {
                    selectedMenu = "베스트";
                    resetFilters();
                    updateScreen();
                    homeScrollView.smoothScrollTo(0, 0);
                });

        updateScreen();
    }

    // ====================================
    // 상단 메뉴 연결
    // ====================================

    private void setupTopMenus() {

        for (int i = 0; i < MENUS.length; i++) {

            final String menu = MENUS[i];

            TextView tab = findViewById(TAB_IDS[i]);

            tab.setOnClickListener(v -> {
                selectedMenu = menu;

                resetFilters();
                updateScreen();

                homeScrollView.smoothScrollTo(0, 0);
            });
        }
    }

    // ====================================
    // 전체 화면 갱신
    // ====================================

    private void updateScreen() {

        updateTabStyles();
        updateSections();
        updateProducts();
    }

    // 선택한 메뉴 검정 글씨 + 밑줄
    private void updateTabStyles() {

        for (int i = 0; i < MENUS.length; i++) {

            boolean selected =
                    MENUS[i].equals(selectedMenu);

            TextView tab = findViewById(TAB_IDS[i]);
            View indicator = findViewById(
                    INDICATOR_IDS[i]
            );

            tab.setTextColor(
                    selected ? BLACK : GRAY
            );

            tab.setTypeface(
                    null,
                    selected
                            ? Typeface.BOLD
                            : Typeface.NORMAL
            );

            indicator.setVisibility(
                    selected
                            ? View.VISIBLE
                            : View.INVISIBLE
            );
        }
    }

    // 홈 / 카테고리 화면 전환
    private void updateSections() {

        boolean isHome = "홈".equals(selectedMenu);

        homeBanner.setVisibility(
                isHome ? View.VISIBLE : View.GONE
        );

        homeRecommendationHeader.setVisibility(
                isHome ? View.VISIBLE : View.GONE
        );

        categoryResultHeader.setVisibility(
                isHome ? View.GONE : View.VISIBLE
        );
    }

    // ====================================
    // 상품 목록
    // ====================================

    private void updateProducts() {

        List<Product> products = new ArrayList<>();

        for (Product product : DummyData.getProducts()) {

            if (!matchesMenu(product)) {
                continue;
            }

            if (!matchesFilters(product)) {
                continue;
            }

            products.add(product);
        }

        // 신상은 ID가 높은 상품부터
        if ("신상".equals(selectedMenu)) {

            Collections.sort(
                    products,
                    (a, b) -> Integer.compare(
                            b.getId(),
                            a.getId()
                    )
            );
        }

        // 베스트는 현재 더미 데이터 순서 사용
        // 실제 판매량 데이터 연결 시 변경 가능

        productAdapter = new ProductAdapter(products);

        recyclerProducts.setAdapter(productAdapter);

        // 카테고리 제목
        if (!"홈".equals(selectedMenu)) {

            textSelectedCategory.setText(
                    selectedMenu + "  " +
                            products.size() + "개"
            );
        }

        // 상품 없을 때 안내
        boolean isEmpty = products.isEmpty();

        textHomeEmpty.setVisibility(
                isEmpty ? View.VISIBLE : View.GONE
        );

        recyclerProducts.setVisibility(
                isEmpty ? View.GONE : View.VISIBLE
        );
    }

    private boolean matchesMenu(Product product) {

        if ("홈".equals(selectedMenu)
                || "신상".equals(selectedMenu)
                || "베스트".equals(selectedMenu)) {
            return true;
        }

        String productCategory = product.getCategory();

        return selectedMenu.equals(productCategory);
    }

    // ====================================
    // 필터 적용
    // ====================================

    private boolean matchesFilters(Product product) {

        // 가격
        if (minPrice > 0
                && product.getPrice() < minPrice) {
            return false;
        }

        if (maxPrice > 0
                && product.getPrice() > maxPrice) {
            return false;
        }

        // 색상
        if (!selectedColor.isEmpty()) {

            List<String> colors = product.getColors();

            if (colors == null
                    || !colors.contains(selectedColor)) {
                return false;
            }
        }

        // 스타일
        if (!selectedStyles.isEmpty()) {

            List<String> styles = product.getStyles();

            if (styles == null || styles.isEmpty()) {
                return false;
            }

            boolean matched = false;

            for (String selected :
                    selectedStyles.split(",")) {

                if (styles.contains(selected.trim())) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false;
            }
        }

        // 키 / 몸무게 / 체형은 현재 입력값만 저장
        // 상품별 적합도 데이터 연결 후 적용

        return true;
    }

    // ====================================
    // 새 필터 팝업
    // ====================================

    private void showFilterDialog() {

        CategoryFilterDialog dialog =
                new CategoryFilterDialog(
                        this,
                        minPrice,
                        maxPrice,
                        selectedColor,
                        selectedStyles,
                        selectedHeight,
                        selectedWeight,
                        selectedBodyType,
                        (min, max, color, styles,
                         height, weight, bodyType) -> {

                            minPrice = min;
                            maxPrice = max;

                            selectedColor = color;
                            selectedStyles = styles;

                            selectedHeight = height;
                            selectedWeight = weight;
                            selectedBodyType = bodyType;

                            updateProducts();
                        }
                );

        dialog.show();
    }

    private void resetFilters() {

        minPrice = 0;
        maxPrice = 0;

        selectedColor = "";
        selectedStyles = "";

        selectedHeight = "";
        selectedWeight = "";
        selectedBodyType = "";
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (productAdapter != null) {
            productAdapter.notifyDataSetChanged();
        }
    }
}
