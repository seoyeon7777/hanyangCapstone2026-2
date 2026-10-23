package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.adapter.ProductAdapter;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "CATEGORY";
    public static final String EXTRA_SUBCATEGORY = "SUBCATEGORY";
    public static final String EXTRA_SORT = "SORT";

    private static final int BLACK = Color.rgb(17, 17, 17);
    private static final int GRAY = Color.rgb(100, 100, 100);
    private static final int BORDER = Color.rgb(225, 225, 225);

    private static final String[] CATEGORIES = {
            "상의", "바지", "아우터", "원피스",
            "스커트", "수영복", "트레이닝",
            "신발", "가방·잡화"
    };

    private static final String[][] SUBCATEGORIES = {
            {
                    "전체", "반팔 티셔츠", "긴팔 티셔츠",
                    "셔츠·블라우스", "니트",
                    "후드", "맨투맨", "민소매"
            },
            {
                    "전체", "데님", "슬랙스",
                    "와이드 팬츠", "숏팬츠",
                    "트레이닝 팬츠", "코튼 팬츠"
            },
            {
                    "전체", "자켓", "가디건",
                    "집업·점퍼", "코트",
                    "패딩", "바람막이"
            },
            {
                    "전체", "미니 원피스",
                    "미디 원피스", "롱 원피스",
                    "니트 원피스"
            },
            {
                    "전체", "미니 스커트",
                    "미디 스커트", "롱 스커트",
                    "데님 스커트"
            },
            {
                    "전체", "비키니",
                    "원피스 수영복", "래시가드"
            },
            {
                    "전체", "트레이닝 상의",
                    "트레이닝 하의", "레깅스",
                    "스포츠웨어"
            },
            {
                    "전체", "운동화", "스니커즈",
                    "로퍼", "샌들", "부츠"
            },
            {
                    "전체", "가방", "모자",
                    "벨트", "양말"
            }
    };

    private static final String[] COLORS = {
            "화이트", "블랙", "그레이", "베이지",
            "브라운", "블루", "핑크", "그린",
            "레드", "옐로우", "퍼플", "네이비"
    };

    private static final String[] SIZES = {
            "FREE", "XS", "S", "M", "L", "XL", "XXL"
    };

    private static final String[] SORT_OPTIONS = {
            "추천순", "최신순", "낮은 가격순", "높은 가격순"
    };

    private LinearLayout categoryChipContainer;
    private LinearLayout filterContainer;

    private RecyclerView recyclerCategory;

    private TextView textCategoryTitle;
    private TextView textCategoryCount;
    private TextView textCategoryEmpty;
    private TextView btnCategorySort;

    private String category = "상의";
    private String selectedSubcategory = "전체";

    private String selectedColor = "";
    private String selectedSize = "";
    private String selectedStyles = "";
    private String selectedSeason = "";

    private String sortMode = "추천순";

    private int minPrice = 0;
    private int maxPrice = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_category);

        categoryChipContainer = findViewById(
                R.id.categoryChipContainer
        );

        filterContainer = findViewById(
                R.id.categoryFilterContainer
        );

        recyclerCategory = findViewById(
                R.id.recyclerCategory
        );

        textCategoryTitle = findViewById(
                R.id.textCategoryTitle
        );

        textCategoryCount = findViewById(
                R.id.textCategoryCount
        );

        textCategoryEmpty = findViewById(
                R.id.textCategoryEmpty
        );

        btnCategorySort = findViewById(
                R.id.btnCategorySort
        );

        BottomMenu.setup(
                this,
                BottomMenu.TAB_CATEGORY
        );

        findViewById(R.id.btnCategoryBack)
                .setOnClickListener(v -> finish());

        findViewById(R.id.btnCategorySearch)
                .setOnClickListener(v -> {
                    startActivity(
                            new Intent(
                                    this,
                                    SearchActivity.class
                            )
                    );
                });

        recyclerCategory.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        readIntent();

        textCategoryTitle.setText(category);

        createCategoryChips();
        createFilterChips();
        updateProducts();

        btnCategorySort.setOnClickListener(
                v -> showSortDialog()
        );
    }

    // 다른 화면에서 전달한 카테고리 정보
    private void readIntent() {
        Intent intent = getIntent();

        String requestedCategory =
                intent.getStringExtra(EXTRA_CATEGORY);

        String requestedSubcategory =
                intent.getStringExtra(EXTRA_SUBCATEGORY);

        String requestedSort =
                intent.getStringExtra(EXTRA_SORT);

        if (requestedCategory != null) {
            for (String item : CATEGORIES) {
                if (item.equals(requestedCategory)) {
                    category = item;
                    break;
                }
            }
        }

        if (requestedSubcategory != null
                && !requestedSubcategory.trim().isEmpty()) {

            selectedSubcategory =
                    normalizeSubcategory(requestedSubcategory);
        }

        if ("신상".equals(requestedSort)) {
            sortMode = "최신순";
        } else if ("베스트".equals(requestedSort)) {
            sortMode = "추천순";
        }
    }

    private String normalizeSubcategory(String value) {
        if (value.startsWith("전체")) {
            return "전체";
        }

        if ("셔츠".equals(value)
                || "블라우스".equals(value)) {
            return "셔츠·블라우스";
        }

        return value;
    }

    private int getCategoryIndex() {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) {
                return i;
            }
        }

        return 0;
    }

    // =====================================
    // 상단 세부 카테고리
    // =====================================

    private void createCategoryChips() {
        categoryChipContainer.removeAllViews();

        String[] subcategories =
                SUBCATEGORIES[getCategoryIndex()];

        for (String item : subcategories) {

            boolean selected =
                    item.equals(selectedSubcategory);

            TextView chip = createChip(
                    item,
                    selected,
                    true
            );

            categoryChipContainer.addView(chip);

            chip.setOnClickListener(v -> {
                selectedSubcategory = item;

                createCategoryChips();
                updateProducts();

                recyclerCategory.scrollToPosition(0);
            });
        }
    }

    // =====================================
    // 필터 버튼
    // =====================================

    private void createFilterChips() {
        filterContainer.removeAllViews();

        addFilterChip(
                "☰ 필터",
                hasActiveFilter(),
                this::showNewFilterDialog
        );

        addFilterChip(
                selectedColor.isEmpty()
                        ? "색상⌄"
                        : selectedColor + "⌄",
                !selectedColor.isEmpty(),
                this::showNewFilterDialog
        );

        addFilterChip(
                selectedSize.isEmpty()
                        ? "사이즈⌄"
                        : selectedSize + "⌄",
                !selectedSize.isEmpty(),
                this::showSizeDialog
        );

        addFilterChip(
                minPrice == 0 && maxPrice == 0
                        ? "가격⌄"
                        : "가격 적용 ✓",
                minPrice > 0 || maxPrice > 0,
                this::showNewFilterDialog
        );

        addFilterChip(
                selectedStyles.isEmpty()
                        && selectedSeason.isEmpty()
                        ? "상세옵션⌄"
                        : "상세옵션 ✓",
                !selectedStyles.isEmpty()
                        || !selectedSeason.isEmpty(),
                this::showNewFilterDialog
        );
    }

    private boolean hasActiveFilter() {
        return minPrice > 0
                || maxPrice > 0
                || !selectedColor.isEmpty()
                || !selectedSize.isEmpty()
                || !selectedStyles.isEmpty()
                || !selectedSeason.isEmpty();
    }

    private void addFilterChip(
            String label,
            boolean selected,
            Runnable action
    ) {
        TextView chip = createChip(
                label,
                selected,
                false
        );

        filterContainer.addView(chip);

        chip.setOnClickListener(v -> action.run());
    }

    private TextView createChip(
            String label,
            boolean selected,
            boolean isCategory
    ) {
        TextView chip = new TextView(this);

        chip.setText(label);
        chip.setTextSize(isCategory ? 12 : 11);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);

        chip.setTextColor(
                selected ? Color.WHITE : BLACK
        );

        chip.setTypeface(
                null,
                selected
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );

        chip.setPadding(
                dp(14), 0,
                dp(14), 0
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                selected ? BLACK : Color.WHITE
        );

        background.setCornerRadius(dp(20));

        background.setStroke(
                dp(1),
                selected ? BLACK : BORDER
        );

        chip.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        dp(isCategory ? 32 : 30)
                );

        params.setMargins(
                0, 0,
                dp(7), 0
        );

        chip.setLayoutParams(params);

        return chip;
    }

    // =====================================
    // 새 필터 화면 연결
    // =====================================

    private void showNewFilterDialog() {

        CategoryFilterDialog filterDialog =
                new CategoryFilterDialog(
                        this,
                        minPrice,
                        maxPrice,
                        selectedColor,
                        selectedStyles,
                        (newMinPrice,
                         newMaxPrice,
                         newColor,
                         newStyles) -> {

                            minPrice = newMinPrice;
                            maxPrice = newMaxPrice;

                            selectedColor = newColor;
                            selectedStyles = newStyles;

                            createFilterChips();
                            updateProducts();

                            recyclerCategory.scrollToPosition(0);
                        }
                );

        filterDialog.show();
    }

    // =====================================
    // 사이즈 선택
    // =====================================

    private void showSizeDialog() {

        String[] options =
                new String[SIZES.length + 1];

        options[0] = "전체";

        System.arraycopy(
                SIZES,
                0,
                options,
                1,
                SIZES.length
        );

        int selectedIndex = 0;

        for (int i = 1; i < options.length; i++) {
            if (options[i].equals(selectedSize)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("사이즈")
                        .setSingleChoiceItems(
                                options,
                                selectedIndex,
                                (d, which) -> {

                                    selectedSize =
                                            which == 0
                                                    ? ""
                                                    : options[which];

                                    createFilterChips();
                                    updateProducts();

                                    d.dismiss();

                                    if (!selectedSize.isEmpty()) {
                                        Toast.makeText(
                                                this,
                                                "사이즈별 상품 데이터는 아직 연결되지 않았습니다.",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                        )
                        .setNegativeButton("취소", null)
                        .create();

        dialog.setOnShowListener(unused -> {
            dialog.getButton(
                    AlertDialog.BUTTON_NEGATIVE
            ).setTextColor(BLACK);
        });

        dialog.show();

        dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
        ).setTextColor(BLACK);

        if (dialog.getListView() != null) {
            dialog.getListView().setChoiceMode(
                    android.widget.ListView.CHOICE_MODE_SINGLE
            );
        }
    }

    // =====================================
    // 상품 필터링
    // =====================================

    private void updateProducts() {

        List<Product> products = new ArrayList<>();

        for (Product product : DummyData.getProducts()) {

            if (!matchesCategory(product)) {
                continue;
            }

            if (!matchesSubcategory(product)) {
                continue;
            }

            int price = product.getPrice();

            if (minPrice > 0 && price < minPrice) {
                continue;
            }

            if (maxPrice > 0 && price > maxPrice) {
                continue;
            }

            if (!selectedColor.isEmpty()) {
                if (product.getColors() == null
                        || !product.getColors().contains(
                        selectedColor
                )) {
                    continue;
                }
            }

            if (!selectedStyles.isEmpty()) {
                if (!matchesSelectedStyles(product)) {
                    continue;
                }
            }

            if (!selectedSeason.isEmpty()) {
                if (product.getSeasons() == null
                        || !product.getSeasons().contains(
                        selectedSeason
                )) {
                    continue;
                }
            }

            // 사이즈는 실제 상품 옵션 정보가
            // 준비되면 여기에 연결

            products.add(product);
        }

        sortProducts(products);

        recyclerCategory.setAdapter(
                new ProductAdapter(products)
        );

        textCategoryCount.setText(
                "상품 " + products.size() + "개"
        );

        btnCategorySort.setText(
                sortMode + "⌄"
        );

        boolean empty = products.isEmpty();

        textCategoryEmpty.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );

        recyclerCategory.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );
    }

    private boolean matchesSelectedStyles(Product product) {

        List<String> productStyles =
                product.getStyles();

        if (productStyles == null
                || productStyles.isEmpty()) {
            return false;
        }

        String[] wantedStyles =
                selectedStyles.split(",");

        // 선택한 스타일 중 하나라도 해당하면 표시
        for (String style : wantedStyles) {
            if (productStyles.contains(style.trim())) {
                return true;
            }
        }

        return false;
    }

    private void sortProducts(List<Product> products) {

        switch (sortMode) {

            case "최신순":
                products.sort(
                        (a, b) -> Integer.compare(
                                b.getId(),
                                a.getId()
                        )
                );
                break;

            case "낮은 가격순":
                products.sort(
                        (a, b) -> Integer.compare(
                                a.getPrice(),
                                b.getPrice()
                        )
                );
                break;

            case "높은 가격순":
                products.sort(
                        (a, b) -> Integer.compare(
                                b.getPrice(),
                                a.getPrice()
                        )
                );
                break;

            default:
                // 추천순은 더미 데이터 기본 순서
                break;
        }
    }

    // =====================================
    // 대분류 판별
    // =====================================

    private boolean matchesCategory(Product product) {

        String productCategory =
                product.getCategory();

        if (productCategory != null
                && !productCategory.isEmpty()) {

            return category.equals(productCategory);
        }

        String name = product.getName()
                .toLowerCase(Locale.ROOT);

        switch (category) {

            case "상의":
                return containsAny(
                        name,
                        "티셔츠", "셔츠", "니트",
                        "블라우스", "맨투맨",
                        "후드티", "민소매", "반팔"
                );

            case "바지":
                return containsAny(
                        name,
                        "팬츠", "바지",
                        "데님", "슬랙스"
                );

            case "아우터":
                return containsAny(
                        name,
                        "집업", "자켓", "재킷",
                        "코트", "패딩", "가디건",
                        "점퍼", "바람막이"
                );

            case "원피스":
                return containsAny(
                        name, "원피스", "드레스"
                );

            case "스커트":
                return containsAny(
                        name, "스커트", "치마"
                );

            case "수영복":
                return containsAny(
                        name, "수영복", "비키니", "래시가드"
                );

            case "트레이닝":
                return containsAny(
                        name, "트레이닝", "레깅스", "운동복"
                );

            case "신발":
                return containsAny(
                        name,
                        "운동화", "스니커즈",
                        "로퍼", "샌들", "부츠"
                );

            case "가방·잡화":
                return containsAny(
                        name,
                        "가방", "모자", "벨트", "양말"
                );

            default:
                return false;
        }
    }

    // =====================================
    // 세부 카테고리 판별
    // =====================================

    private boolean matchesSubcategory(Product product) {

        if ("전체".equals(selectedSubcategory)) {
            return true;
        }

        String name = product.getName()
                .toLowerCase(Locale.ROOT);

        switch (selectedSubcategory) {

            case "반팔 티셔츠":
                return name.contains("반팔");

            case "긴팔 티셔츠":
                return name.contains("긴팔");

            case "셔츠·블라우스":
                return name.contains("셔츠")
                        || name.contains("블라우스");

            case "후드":
                return name.contains("후드")
                        && !name.contains("집업");

            case "집업·점퍼":
                return containsAny(
                        name, "집업", "점퍼"
                );

            case "데님":
                return containsAny(
                        name, "데님", "청바지"
                );

            case "와이드 팬츠":
                return name.contains("와이드");

            default:
                String keyword = selectedSubcategory
                        .replace(" 팬츠", "")
                        .replace(" 원피스", "")
                        .replace(" 스커트", "");

                return name.contains(
                        keyword.toLowerCase(Locale.ROOT)
                );
        }
    }

    private boolean containsAny(
            String text,
            String... keywords
    ) {

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // =====================================
    // 정렬
    // =====================================

    private void showSortDialog() {

        int selected = 0;

        for (int i = 0; i < SORT_OPTIONS.length; i++) {
            if (SORT_OPTIONS[i].equals(sortMode)) {
                selected = i;
                break;
            }
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("상품 정렬")
                        .setSingleChoiceItems(
                                SORT_OPTIONS,
                                selected,
                                (d, which) -> {

                                    sortMode =
                                            SORT_OPTIONS[which];

                                    updateProducts();
                                    d.dismiss();
                                }
                        )
                        .setNegativeButton("취소", null)
                        .create();

        dialog.show();

        dialog.getButton(
                AlertDialog.BUTTON_NEGATIVE
        ).setTextColor(BLACK);
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }
}
