package com.example.capstone;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.adapter.ProductAdapter;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.Product;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.ui.BodyTypeActivity;
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

    private static final int LOAD_MORE_DISTANCE_DP = 600;
    private static final long HOME_RETRY_DELAY_MS = 3000L;

    private SwitchCompat switchBodyMode;
    private TextView textHomeRecTitle;

    private final List<Product> homeProducts = new ArrayList<>();

    private int homePage = 0;
    private boolean homeHasMore = true;
    private boolean homeLoading = false;
    private int homeRequestId = 0;
    private long homeRetryAt = 0L;
    private boolean homeErrorShown = false;
    // 홈을 불러올 때의 체형 정보 (설문 시각 + 대표 체형). 바뀌면 다시 불러온다
    private String loadedBodyKey = "";

    private final CompoundButton.OnCheckedChangeListener modeListener =
            (button, isChecked) -> reloadHomeFeed();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        BodyProfileStore.syncActiveProfile(this);

        recyclerProducts = findViewById(R.id.recyclerProducts);
        homeScrollView = findViewById(R.id.homeScrollView);
        homeBanner = findViewById(R.id.homeBanner);
        homeRecommendationHeader = findViewById(
                R.id.homeRecommendationHeader
        );
        categoryResultHeader = findViewById(
                R.id.categoryResultHeader
        );
        textSelectedCategory = findViewById(
                R.id.textSelectedCategory
        );
        btnHomeFilter = findViewById(R.id.btnHomeFilter);
        textHomeEmpty = findViewById(R.id.textHomeEmpty);
        switchBodyMode = findViewById(R.id.switchBodyMode);
        textHomeRecTitle = findViewById(R.id.textHomeRecTitle);

        // 홈 체형 맞춤 토글을 검정색으로 변경
        switchBodyMode.setThumbTintList(
                ColorStateList.valueOf(Color.BLACK)
        );
        switchBodyMode.setTrackTintList(
                ColorStateList.valueOf(Color.LTGRAY)
        );

        setModeSilently(
                RecommendApi.hasBodyInput(this)
        );

        homeScrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener)
                        (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                                loadMoreIfNearBottom()
        );

        recyclerProducts.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        recyclerProducts.setNestedScrollingEnabled(false);

        BottomMenu.setup(
                this,
                BottomMenu.TAB_HOME
        );

        findViewById(R.id.editSearch)
                .setOnClickListener(v -> {
                    startActivity(
                            new Intent(
                                    this,
                                    SearchActivity.class
                            )
                    );
                });

        findViewById(R.id.btnHomeCart)
                .setOnClickListener(v -> BottomMenu.openCart(this));

        // CAPSTONE 로고를 누르면 홈 화면으로 이동
        TextView capstoneLogo = findTextViewByText(
                findViewById(android.R.id.content),
                "Capstone"
        );

        if (capstoneLogo != null) {
            capstoneLogo.setClickable(true);
            capstoneLogo.setFocusable(true);

            capstoneLogo.setOnClickListener(v -> {
                selectedMenu = "홈";
                resetFilters();
                updateScreen();
                homeScrollView.smoothScrollTo(0, 0);
            });
        }

        setupTopMenus();

        btnHomeFilter.setOnClickListener(
                v -> showFilterDialog()
        );

        findViewById(R.id.btnSeeAllProducts)
                .setOnClickListener(v -> {
                    selectedMenu = "베스트";
                    resetFilters();
                    updateScreen();
                    homeScrollView.smoothScrollTo(0, 0);
                });

        updateScreen();
    }

    // XML을 수정하지 않고 Capstone 로고 찾기
    private TextView findTextViewByText(View root, String text) {

        if (root instanceof TextView) {
            TextView textView = (TextView) root;

            if (text.contentEquals(textView.getText())) {
                return textView;
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;

            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextViewByText(
                        group.getChildAt(i),
                        text
                );

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

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

    private void updateScreen() {
        updateTabStyles();
        updateSections();
        updateProducts();
    }

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

    private void updateProducts() {

        if ("홈".equals(selectedMenu)) {
            reloadHomeFeed();
            return;
        }

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

        if ("신상".equals(selectedMenu)) {
            Collections.sort(
                    products,
                    (a, b) -> Integer.compare(
                            b.getId(),
                            a.getId()
                    )
            );
        }

        productAdapter = new ProductAdapter(products);
        recyclerProducts.setAdapter(productAdapter);

        if (!"홈".equals(selectedMenu)) {
            textSelectedCategory.setText(
                    selectedMenu + "  " +
                            products.size() + "개"
            );
        }

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

    private boolean matchesFilters(Product product) {

        if (minPrice > 0
                && product.getPrice() < minPrice) {
            return false;
        }

        if (maxPrice > 0
                && product.getPrice() > maxPrice) {
            return false;
        }

        if (!selectedColor.isEmpty()) {

            List<String> colors = product.getColors();

            if (colors == null
                    || !colors.contains(selectedColor)) {
                return false;
            }
        }

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

        return true;
    }

    private void reloadHomeFeed() {

        homeRequestId++;

        homeProducts.clear();
        homePage = 0;
        homeHasMore = true;
        homeLoading = false;
        homeRetryAt = 0L;
        homeErrorShown = false;

        loadedBodyKey = RecommendApi.bodyInputKey(this);

        productAdapter = new ProductAdapter(homeProducts);
        recyclerProducts.setAdapter(productAdapter);

        textHomeEmpty.setVisibility(View.GONE);
        recyclerProducts.setVisibility(View.VISIBLE);

        updateHomeTitle("");

        loadNextHomePage();
    }

    private void loadNextHomePage() {

        if (homeLoading
                || !homeHasMore
                || !"홈".equals(selectedMenu)
                || SystemClock.uptimeMillis() < homeRetryAt) {
            return;
        }

        homeLoading = true;

        final int requestId = homeRequestId;
        final boolean bodyMode = switchBodyMode.isChecked();

        RecommendApi.loadFeed(
                this,
                bodyMode
                        ? RecommendApi.MODE_BODY
                        : RecommendApi.MODE_GENERAL,
                homePage,
                new RecommendApi.Callback<RecommendApi.FeedPage>() {

                    @Override
                    public void onSuccess(RecommendApi.FeedPage page) {

                        if (requestId != homeRequestId || isFinishing()) {
                            return;
                        }

                        homeLoading = false;
                        homeErrorShown = false;

                        int start = homeProducts.size();
                        homeProducts.addAll(page.items);
                        productAdapter.notifyItemRangeInserted(
                                start,
                                page.items.size()
                        );

                        homePage++;
                        homeHasMore = page.hasMore;

                        updateHomeTitle(page.primary);

                        boolean isEmpty = homeProducts.isEmpty();

                        textHomeEmpty.setVisibility(
                                isEmpty ? View.VISIBLE : View.GONE
                        );

                        homeScrollView.post(
                                MainActivity.this::loadMoreIfNearBottom
                        );
                    }

                    @Override
                    public void onError(int code, String message) {

                        if (requestId != homeRequestId || isFinishing()) {
                            return;
                        }

                        homeLoading = false;

                        boolean retryable = code == 0 || code >= 500;

                        if (!homeProducts.isEmpty() && retryable) {

                            homeRetryAt = SystemClock.uptimeMillis() + HOME_RETRY_DELAY_MS;

                            if (homeErrorShown) {
                                return;
                            }

                            homeErrorShown = true;

                            Toast.makeText(
                                    MainActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        homeHasMore = false;

                        if (code == 400 && bodyMode) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "체형 진단을 하면 골격 맞춤 추천을 볼 수 있어요. 지금은 일반 추천을 보여 줄게요",
                                    Toast.LENGTH_LONG
                            ).show();

                            setModeSilently(false);
                            reloadHomeFeed();
                            return;
                        }

                        if (homeProducts.isEmpty()) {

                            homeProducts.addAll(DummyData.getProducts());
                            productAdapter.notifyDataSetChanged();

                            message += " 기본 상품을 보여 줄게요";
                        }

                        Toast.makeText(
                                MainActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void loadMoreIfNearBottom() {

        View content = homeScrollView.getChildAt(0);

        if (content == null) {
            return;
        }

        int remaining = content.getBottom()
                - (homeScrollView.getHeight() + homeScrollView.getScrollY());

        float density = getResources().getDisplayMetrics().density;

        if (remaining < LOAD_MORE_DISTANCE_DP * density) {
            loadNextHomePage();
        }
    }

    private void updateHomeTitle(String primary) {

        if (!switchBodyMode.isChecked()) {
            textHomeRecTitle.setText("취향 저격 추천");
            return;
        }

        String typeName;

        switch (primary) {
            case "S":
                typeName = "스트레이트 ";
                break;
            case "W":
                typeName = "웨이브 ";
                break;
            case "N":
                typeName = "내추럴 ";
                break;
            default:
                typeName = "";
        }

        // 체형 이름이 있으면 "○○ 체형" / "맞춤 추천" 두 줄로
        textHomeRecTitle.setText(typeName.isEmpty()
                ? "체형 맞춤 추천"
                : typeName + "체형\n맞춤 추천");
    }

    private void setModeSilently(boolean bodyMode) {

        switchBodyMode.setOnCheckedChangeListener(null);
        switchBodyMode.setChecked(bodyMode);
        switchBodyMode.setOnCheckedChangeListener(modeListener);
    }

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

        if ("홈".equals(selectedMenu)
                && !RecommendApi.bodyInputKey(this).equals(loadedBodyKey)) {

            setModeSilently(
                    RecommendApi.hasBodyInput(this)
            );

            reloadHomeFeed();
        }
    }
}
