
package com.example.capstone.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

public class SearchActivity extends AppCompatActivity {

    private static final int BLACK = 0xFF111111;
    private static final int GRAY = 0xFF888888;
    private static final int LIGHT = 0xFFF5F5F5;
    private static final int BORDER = 0xFFE8E8E8;
    private static final int WHITE = Color.WHITE;

    private static final String PREF_NAME = "search_history";
    private static final String PREF_KEY = "recent_terms";

    private static final String[] POPULAR_TERMS = {
            "와이드 팬츠",
            "니트",
            "후드집업",
            "셔츠",
            "데님",
            "가디건",
            "슬랙스",
            "원피스",
            "반팔",
            "자켓"
    };

    private EditText editSearch;
    private RecyclerView recyclerSearch;
    private TextView textResultCount;
    private TextView textEmpty;
    private TextView btnClearSearch;
    private TextView btnSearchFilter;

    private ScrollView discoveryScroll;

    private ProductAdapter adapter;

    private final List<Product> searchResults =
            new ArrayList<>();

    private final List<String> recentTerms =
            new ArrayList<>();

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

        setContentView(R.layout.activity_search);

        // 메인과 같은 하단 메뉴 유지
        BottomMenu.setup(this, BottomMenu.TAB_HOME);

        editSearch = findViewById(R.id.editSearch);
        recyclerSearch = findViewById(R.id.recyclerSearch);
        textResultCount = findViewById(R.id.textResultCount);
        textEmpty = findViewById(R.id.textSearchEmpty);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnSearchFilter = findViewById(R.id.btnSearchFilter);

        recyclerSearch.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        adapter = new ProductAdapter(searchResults);
        recyclerSearch.setAdapter(adapter);

        loadRecentTerms();
        createDiscoveryPanel();

        findViewById(R.id.btnSearchBack)
                .setOnClickListener(v -> finish());

        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
        });

        btnSearchFilter.setOnClickListener(v -> {
            showFilterDialog();
        });

        editSearch.setSingleLine(true);
        editSearch.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH
        );

        editSearch.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                        btnClearSearch.setVisibility(
                                s.length() > 0
                                        ? View.VISIBLE
                                        : View.GONE
                        );

                        searchProducts(s.toString());
                    }

                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {
                    }
                }
        );

        editSearch.setOnEditorActionListener(
                (v, actionId, event) -> {

                    if (actionId ==
                            EditorInfo.IME_ACTION_SEARCH) {

                        String keyword =
                                editSearch.getText()
                                        .toString()
                                        .trim();

                        if (!keyword.isEmpty()) {
                            saveRecentTerm(keyword);
                        }

                        hideKeyboard();
                        searchProducts(keyword);

                        return true;
                    }

                    return false;
                }
        );

        searchProducts("");
    }

    // =====================================
    // 최근 검색어 저장 및 불러오기
    // =====================================

    private void loadRecentTerms() {

        recentTerms.clear();

        SharedPreferences prefs =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                );

        String stored = prefs.getString(
                PREF_KEY,
                ""
        );

        if (stored == null || stored.isEmpty()) {
            return;
        }

        String[] values = stored.split("\n");

        for (String value : values) {

            String term = value.trim();

            if (!term.isEmpty()) {
                recentTerms.add(term);
            }
        }
    }

    private void saveRecentTerms() {

        StringBuilder builder =
                new StringBuilder();

        for (int i = 0; i < recentTerms.size(); i++) {

            if (i > 0) {
                builder.append("\n");
            }

            builder.append(recentTerms.get(i));
        }

        getSharedPreferences(
                PREF_NAME,
                MODE_PRIVATE
        ).edit()
                .putString(
                        PREF_KEY,
                        builder.toString()
                )
                .apply();
    }

    private void saveRecentTerm(String keyword) {

        String term = keyword
                .trim()
                .replace("\n", " ");

        if (term.isEmpty()) {
            return;
        }

        for (int i = recentTerms.size() - 1;
             i >= 0;
             i--) {

            if (recentTerms.get(i)
                    .equalsIgnoreCase(term)) {

                recentTerms.remove(i);
            }
        }

        recentTerms.add(0, term);

        while (recentTerms.size() > 10) {
            recentTerms.remove(
                    recentTerms.size() - 1
            );
        }

        saveRecentTerms();
        renderDiscoveryPanel();
    }

    private void deleteRecentTerm(String keyword) {

        recentTerms.remove(keyword);

        saveRecentTerms();
        renderDiscoveryPanel();
    }

    private void clearRecentTerms() {

        recentTerms.clear();

        saveRecentTerms();
        renderDiscoveryPanel();
    }

    // =====================================
    // 검색 전 화면 설치
    // =====================================

    private void createDiscoveryPanel() {

        ViewGroup parent =
                (ViewGroup) recyclerSearch.getParent();

        if (parent == null) {
            return;
        }

        int index =
                parent.indexOfChild(recyclerSearch);

        ViewGroup.LayoutParams originalParams =
                recyclerSearch.getLayoutParams();

        parent.removeView(recyclerSearch);

        FrameLayout frame =
                new FrameLayout(this);

        frame.setBackgroundColor(WHITE);

        parent.addView(
                frame,
                index,
                originalParams
        );

        frame.addView(
                recyclerSearch,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        discoveryScroll =
                new ScrollView(this);

        discoveryScroll.setBackgroundColor(WHITE);

        discoveryScroll.setFillViewport(false);

        discoveryScroll.setVerticalScrollBarEnabled(
                false
        );

        frame.addView(
                discoveryScroll,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        renderDiscoveryPanel();
    }

    // =====================================
    // 최근 검색어 + 인기 검색어 UI
    // =====================================

    private void renderDiscoveryPanel() {

        if (discoveryScroll == null) {
            return;
        }

        discoveryScroll.removeAllViews();

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setBackgroundColor(WHITE);

        root.setPadding(
                dp(20),
                dp(20),
                dp(20),
                dp(32)
        );

        discoveryScroll.addView(
                root,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // 최근 검색어 제목
        LinearLayout recentHeader =
                new LinearLayout(this);

        recentHeader.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView recentTitle = makeText(
                "최근 검색어",
                17,
                true,
                BLACK
        );

        recentHeader.addView(
                recentTitle,
                new LinearLayout.LayoutParams(
                        0,
                        dp(44),
                        1f
                )
        );

        recentTitle.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView clearAll = makeText(
                "전체 삭제",
                12,
                false,
                GRAY
        );

        clearAll.setGravity(Gravity.CENTER);

        clearAll.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );

        clearAll.setVisibility(
                recentTerms.isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        clearAll.setOnClickListener(
                v -> clearRecentTerms()
        );

        recentHeader.addView(clearAll);

        root.addView(recentHeader);

        if (recentTerms.isEmpty()) {

            TextView emptyRecent = makeText(
                    "최근 검색 내역이 없어요.",
                    13,
                    false,
                    GRAY
            );

            emptyRecent.setGravity(
                    Gravity.CENTER_VERTICAL
            );

            root.addView(
                    emptyRecent,
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(62)
                    )
            );

        } else {

            // 최근 검색어를 한 줄씩 표시
            for (String term :
                    new ArrayList<>(recentTerms)) {

                LinearLayout item =
                        new LinearLayout(this);

                item.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                TextView clock = makeText(
                        "◷",
                        19,
                        false,
                        GRAY
                );

                clock.setGravity(Gravity.CENTER);

                item.addView(
                        clock,
                        new LinearLayout.LayoutParams(
                                dp(30),
                                dp(48)
                        )
                );

                TextView keyword = makeText(
                        term,
                        14,
                        false,
                        BLACK
                );

                keyword.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                keyword.setPadding(
                        dp(8),
                        0,
                        0,
                        0
                );

                item.addView(
                        keyword,
                        new LinearLayout.LayoutParams(
                                0,
                                dp(48),
                                1f
                        )
                );

                TextView remove = makeText(
                        "×",
                        23,
                        false,
                        GRAY
                );

                remove.setGravity(Gravity.CENTER);

                item.addView(
                        remove,
                        new LinearLayout.LayoutParams(
                                dp(38),
                                dp(48)
                        )
                );

                keyword.setOnClickListener(
                        v -> selectKeyword(term)
                );

                clock.setOnClickListener(
                        v -> selectKeyword(term)
                );

                remove.setOnClickListener(
                        v -> deleteRecentTerm(term)
                );

                root.addView(item);
            }
        }

        View divider = new View(this);

        divider.setBackgroundColor(BORDER);

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(1)
                );

        dividerParams.topMargin = dp(20);
        dividerParams.bottomMargin = dp(20);

        root.addView(
                divider,
                dividerParams
        );

        // 인기 검색어 제목
        TextView popularTitle = makeText(
                "인기 검색어",
                17,
                true,
                BLACK
        );

        popularTitle.setGravity(
                Gravity.CENTER_VERTICAL
        );

        root.addView(
                popularTitle,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(48)
                )
        );

        // 인기 검색어 2열
        for (int row = 0; row < 5; row++) {

            LinearLayout rowLayout =
                    new LinearLayout(this);

            rowLayout.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            root.addView(
                    rowLayout,
                    new LinearLayout.LayoutParams(
                            -1,
                            dp(54)
                    )
            );

            for (int col = 0; col < 2; col++) {

                int index = row * 2 + col;

                String keyword =
                        POPULAR_TERMS[index];

                LinearLayout item =
                        new LinearLayout(this);

                item.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                rowLayout.addView(
                        item,
                        new LinearLayout.LayoutParams(
                                0,
                                -1,
                                1f
                        )
                );

                TextView rank = makeText(
                        String.valueOf(index + 1),
                        15,
                        true,
                        index < 3 ? BLACK : GRAY
                );

                rank.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                item.addView(
                        rank,
                        new LinearLayout.LayoutParams(
                                dp(28),
                                -1
                        )
                );

                TextView name = makeText(
                        keyword,
                        13,
                        false,
                        BLACK
                );

                name.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                item.addView(
                        name,
                        new LinearLayout.LayoutParams(
                                0,
                                -1,
                                1f
                        )
                );

                item.setOnClickListener(
                        v -> selectKeyword(keyword)
                );
            }
        }
    }

    // =====================================
    // 최근 / 인기 검색어 클릭
    // =====================================

    private void selectKeyword(String keyword) {

        editSearch.setText(keyword);

        editSearch.setSelection(
                editSearch.length()
        );

        saveRecentTerm(keyword);

        hideKeyboard();

        searchProducts(keyword);
    }

    // =====================================
    // 검색 기능
    // =====================================

    private void searchProducts(String keyword) {

        String query = keyword
                .trim()
                .toLowerCase(Locale.ROOT);

        searchResults.clear();

        // 검색어가 없으면 상품을 보여주지 않음
        if (query.isEmpty()) {

            adapter.notifyDataSetChanged();

            showDiscovery(true);

            return;
        }

        for (Product product :
                DummyData.getProducts()) {

            String name =
                    product.getName() == null
                            ? ""
                            : product.getName()
                            .toLowerCase(Locale.ROOT);

            String brand =
                    product.getBrand() == null
                            ? ""
                            : product.getBrand()
                            .toLowerCase(Locale.ROOT);

            String category =
                    product.getCategory() == null
                            ? ""
                            : product.getCategory()
                            .toLowerCase(Locale.ROOT);

            // 상품명 / 브랜드 / 카테고리 검색
            if (!name.contains(query)
                    && !brand.contains(query)
                    && !category.contains(query)) {

                continue;
            }

            // 가격 필터
            int price = product.getPrice();

            if (minPrice > 0 && price < minPrice) {
                continue;
            }

            if (maxPrice > 0 && price > maxPrice) {
                continue;
            }

            // 색상 필터
            if (!selectedColor.isEmpty()) {

                if (product.getColors() == null
                        || !product.getColors()
                        .contains(selectedColor)) {

                    continue;
                }
            }

            // 스타일 필터
            if (!selectedStyles.isEmpty()) {

                boolean matched = false;

                String[] styles =
                        selectedStyles.split(",");

                for (String style : styles) {

                    if (product.getStyles() != null
                            && product.getStyles()
                            .contains(style.trim())) {

                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    continue;
                }
            }

            searchResults.add(product);
        }

        adapter.notifyDataSetChanged();

        showDiscovery(false);

        textResultCount.setText(
                "검색 결과 " +
                        searchResults.size() +
                        "개"
        );

        boolean empty =
                searchResults.isEmpty();

        textEmpty.setVisibility(
                empty ? View.VISIBLE : View.GONE
        );

        recyclerSearch.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );

        btnSearchFilter.setText(
                hasActiveFilters()
                        ? "☷  필터 ●"
                        : "☷  필터"
        );

        btnSearchFilter.setTextColor(BLACK);
    }

    private boolean hasActiveFilters() {

        return minPrice > 0
                || maxPrice > 0
                || !selectedColor.isEmpty()
                || !selectedStyles.isEmpty()
                || !selectedHeight.isEmpty()
                || !selectedWeight.isEmpty()
                || !selectedBodyType.isEmpty();
    }

    // =====================================
    // 검색 전 / 검색 결과 화면 전환
    // =====================================

    private void showDiscovery(boolean visible) {

        if (discoveryScroll != null) {

            discoveryScroll.setVisibility(
                    visible
                            ? View.VISIBLE
                            : View.GONE
            );

            if (visible) {
                renderDiscoveryPanel();
            }
        }

        recyclerSearch.setVisibility(
                visible ? View.GONE : View.VISIBLE
        );

        textResultCount.setVisibility(
                visible ? View.GONE : View.VISIBLE
        );

        btnSearchFilter.setVisibility(
                visible ? View.GONE : View.VISIBLE
        );

        if (visible) {
            textEmpty.setVisibility(View.GONE);
        }
    }

    // =====================================
    // 통합 필터 팝업
    // =====================================

    private void showFilterDialog() {

        hideKeyboard();

        CategoryFilterDialog filterDialog =
                new CategoryFilterDialog(
                        this,
                        minPrice,
                        maxPrice,
                        selectedColor,
                        selectedStyles,
                        selectedHeight,
                        selectedWeight,
                        selectedBodyType,
                        (
                                newMin,
                                newMax,
                                newColor,
                                newStyles,
                                newHeight,
                                newWeight,
                                newBodyType
                        ) -> {

                            minPrice = newMin;
                            maxPrice = newMax;

                            selectedColor = newColor;
                            selectedStyles = newStyles;

                            selectedHeight = newHeight;
                            selectedWeight = newWeight;

                            selectedBodyType =
                                    newBodyType;

                            searchProducts(
                                    editSearch.getText()
                                            .toString()
                            );
                        }
                );

        filterDialog.show();
    }

    // =====================================
    // 공통 UI 함수
    // =====================================

    private TextView makeText(
            String value,
            int size,
            boolean bold,
            int color
    ) {

        TextView view = new TextView(this);

        view.setText(value);
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

    private void hideKeyboard() {

        InputMethodManager imm =
                (InputMethodManager)
                        getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );

        if (imm != null) {

            imm.hideSoftInputFromWindow(
                    editSearch.getWindowToken(),
                    0
            );
        }

        editSearch.clearFocus();
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
