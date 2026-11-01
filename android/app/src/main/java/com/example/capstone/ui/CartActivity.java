package com.example.capstone.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.adapter.CartAdapter;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.Product;
import com.example.capstone.data.SizeFitChecker;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity {

    private static final int BLACK = Color.rgb(17, 17, 17);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(150, 150, 150);
    private static final int LIGHT = Color.rgb(246, 246, 246);
    private static final int BORDER = Color.rgb(232, 232, 232);

    private RecyclerView recyclerCart;
    private Button btnGoOrder;
    private TextView titleCount;
    private TextView totalPriceText;
    private TextView totalQuantityText;

    private FrameLayout cartFrame;
    private LinearLayout emptyLayout;
    private LinearLayout recommendationSection;
    private LinearLayout recommendationRow;
    private LinearLayout summaryLayout;
    private LinearLayout selectAllRow;
    private View selectAllDivider;
    private CheckBox selectAllCheck;

    private CartAdapter cartAdapter;

    private final List<Product> cartProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        recyclerCart = findViewById(R.id.recyclerCart);
        btnGoOrder = findViewById(R.id.btnGoOrder);

        recyclerCart.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerCart.setNestedScrollingEnabled(true);

        cartAdapter = new CartAdapter(
                cartProducts,
                this::showCartProducts
        );

        recyclerCart.setAdapter(cartAdapter);

        setupCartScreen();
        setupOrderButton();

        BottomMenu.setup(this, BottomMenu.TAB_CART);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCartProducts();
    }

    // 장바구니 화면 구성
    private void setupCartScreen() {

        ViewGroup parent =
                (ViewGroup) recyclerCart.getParent();

        if (parent == null) return;

        int index = parent.indexOfChild(recyclerCart);

        ViewGroup.LayoutParams oldParams =
                recyclerCart.getLayoutParams();

        parent.removeView(recyclerCart);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(WHITE);

        parent.addView(root, index, oldParams);

        titleCount = findViewById(R.id.textCartTitle);

        root.addView(divider());

        createSelectAllRow(root);

        cartFrame = new FrameLayout(this);

        root.addView(
                cartFrame,
                new LinearLayout.LayoutParams(
                        -1, 0, 1f
                )
        );

        cartFrame.addView(
                recyclerCart,
                new FrameLayout.LayoutParams(
                        -1, -1
                )
        );

        createEmptyView();
        createRecommendationSection(root);
        createSummarySection(root);
    }

    private void createSelectAllRow(LinearLayout root) {

        selectAllRow = new LinearLayout(this);
        selectAllRow.setOrientation(LinearLayout.HORIZONTAL);
        selectAllRow.setGravity(Gravity.CENTER_VERTICAL);
        selectAllRow.setPadding(dp(16), 0, dp(16), 0);

        root.addView(
                selectAllRow,
                new LinearLayout.LayoutParams(
                        -1, dp(48)
                )
        );

        selectAllCheck = new CheckBox(this);
        selectAllCheck.setButtonTintList(
                ColorStateList.valueOf(BLACK)
        );
        selectAllCheck.setMinWidth(0);
        selectAllCheck.setMinHeight(0);
        selectAllCheck.setTextSize(14);
        selectAllCheck.setTextColor(BLACK);
        selectAllCheck.setText("전체 선택");

        selectAllCheck.setOnClickListener(v -> {

            boolean checked = selectAllCheck.isChecked();

            for (Product product : cartProducts) {
                product.setCartSelected(checked);
            }

            showCartProducts();
        });

        selectAllRow.addView(selectAllCheck);

        selectAllDivider = divider();
        root.addView(selectAllDivider);
    }

    // 장바구니가 비어 있을 때
    private void createEmptyView() {

        emptyLayout = new LinearLayout(this);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundColor(WHITE);

        ImageView icon = new ImageView(this);

        icon.setImageResource(R.drawable.ic_cart_empty);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(56), dp(56)
                );

        iconParams.bottomMargin = dp(12);

        emptyLayout.addView(icon, iconParams);

        TextView message = makeText(
                "장바구니에 담긴 상품이 없어요",
                15, true, BLACK
        );

        message.setGravity(Gravity.CENTER);
        emptyLayout.addView(message);

        TextView subMessage = makeText(
                "마음에 드는 상품을 담아보세요",
                12, false, GRAY
        );

        subMessage.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        -1, dp(32)
                );

        emptyLayout.addView(subMessage, subParams);

        TextView browseButton = makeText(
                "상품 둘러보기",
                13, false, BLACK
        );

        browseButton.setGravity(Gravity.CENTER);
        browseButton.setBackground(
                rounded(WHITE, BORDER, 24)
        );

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        dp(132), dp(42)
                );

        buttonParams.topMargin = dp(18);

        emptyLayout.addView(
                browseButton, buttonParams
        );

        browseButton.setOnClickListener(v -> goHome());

        cartFrame.addView(
                emptyLayout,
                new FrameLayout.LayoutParams(
                        -1, -1
                )
        );
    }

    // 추천 상품 영역
    private void createRecommendationSection(
            LinearLayout root
    ) {

        recommendationSection = new LinearLayout(this);
        recommendationSection.setOrientation(
                LinearLayout.VERTICAL
        );
        recommendationSection.setBackgroundColor(WHITE);

        root.addView(
                recommendationSection,
                new LinearLayout.LayoutParams(
                        -1, dp(238)
                )
        );

        View topGap = new View(this);
        topGap.setBackgroundColor(LIGHT);

        recommendationSection.addView(
                topGap,
                new LinearLayout.LayoutParams(
                        -1, dp(6)
                )
        );

        TextView title = makeText(
                "이런 상품은 어때요?",
                16, true, BLACK
        );

        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);

        recommendationSection.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1, dp(52)
                )
        );

        HorizontalScrollView scroll =
                new HorizontalScrollView(this);

        scroll.setHorizontalScrollBarEnabled(false);

        recommendationSection.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1, 0, 1f
                )
        );

        recommendationRow = new LinearLayout(this);
        recommendationRow.setOrientation(
                LinearLayout.HORIZONTAL
        );
        recommendationRow.setPadding(
                dp(12), 0, dp(12), 0
        );

        scroll.addView(recommendationRow);

        renderRecommendations();
    }

    private void renderRecommendations() {

        if (recommendationRow == null) return;

        recommendationRow.removeAllViews();

        int added = 0;

        for (Product product : DummyData.getProducts()) {

            if (product.isInCart()) continue;
            if (added >= 6) break;

            added++;
            createRecommendationCard(product);
        }
    }

    private void createRecommendationCard(Product product) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        dp(126), -2
                );

        cardParams.setMargins(
                dp(4), 0, dp(4), 0
        );

        recommendationRow.addView(card, cardParams);

        FrameLayout imageFrame = new FrameLayout(this);

        imageFrame.setBackgroundColor(
                product.getId() % 2 == 0
                        ? 0xFFE7E7E7
                        : 0xFFD8DCE2
        );

        card.addView(
                imageFrame,
                new LinearLayout.LayoutParams(
                        -1, dp(105)
                )
        );

        TextView category = makeText(
                product.getCategory() == null
                        ? "패션"
                        : product.getCategory(),
                12, false, GRAY
        );

        category.setGravity(Gravity.CENTER);

        imageFrame.addView(
                category,
                new FrameLayout.LayoutParams(
                        -1, -1
                )
        );

        TextView brand = makeText(
                product.getBrand(),
                10, false, GRAY
        );

        brand.setPadding(0, dp(7), 0, 0);
        card.addView(brand);

        TextView name = makeText(
                product.getName(),
                12, false, BLACK
        );

        name.setSingleLine(true);
        name.setEllipsize(
                android.text.TextUtils.TruncateAt.END
        );

        card.addView(name);

        TextView price = makeText(
                formatPrice(product.getPrice()),
                12, true, BLACK
        );

        card.addView(price);

        card.setOnClickListener(v -> openDetail(product));
    }

    private void openDetail(Product product) {

        Intent intent = new Intent(
                this, ProductDetailActivity.class
        );

        intent.putExtra("product_id", product.getId());
        intent.putExtra("product_brand", product.getBrand());
        intent.putExtra("product_name", product.getName());
        intent.putExtra("product_price", product.getPrice());
        intent.putExtra("server_product_id", product.getServerId());

        startActivity(intent);
    }

    // 총 상품 금액 영역
    private void createSummarySection(LinearLayout root) {

        summaryLayout = new LinearLayout(this);
        summaryLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        summaryLayout.setPadding(
                dp(18), dp(12), dp(18), dp(12)
        );

        summaryLayout.setBackgroundColor(WHITE);

        root.addView(
                summaryLayout,
                new LinearLayout.LayoutParams(
                        -1, dp(82)
                )
        );

        LinearLayout quantityRow = new LinearLayout(this);
        quantityRow.setGravity(Gravity.CENTER_VERTICAL);

        summaryLayout.addView(
                quantityRow,
                new LinearLayout.LayoutParams(
                        -1, dp(26)
                )
        );

        TextView quantityLabel = makeText(
                "선택 상품", 13, false, GRAY
        );

        quantityRow.addView(
                quantityLabel,
                new LinearLayout.LayoutParams(
                        0, -2, 1f
                )
        );

        totalQuantityText = makeText(
                "0개", 13, false, BLACK
        );

        quantityRow.addView(totalQuantityText);

        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);

        summaryLayout.addView(
                priceRow,
                new LinearLayout.LayoutParams(
                        -1, dp(32)
                )
        );

        TextView totalLabel = makeText(
                "총 상품 금액", 15, true, BLACK
        );

        priceRow.addView(
                totalLabel,
                new LinearLayout.LayoutParams(
                        0, -2, 1f
                )
        );

        totalPriceText = makeText(
                "0원", 17, true, BLACK
        );

        priceRow.addView(totalPriceText);
    }

    // 검은색 주문 버튼
    private void setupOrderButton() {

        if (btnGoOrder == null) return;

        btnGoOrder.setBackgroundTintList(
                ColorStateList.valueOf(BLACK)
        );

        btnGoOrder.setTextColor(WHITE);
        btnGoOrder.setTextSize(16);
        btnGoOrder.setAllCaps(false);
        btnGoOrder.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        btnGoOrder.setOnClickListener(v -> {

            StringBuilder productNames =
                    new StringBuilder();

            int totalQuantity = 0;
            int totalPrice = 0;

            for (Product product : DummyData.getProducts()) {

                if (!product.isInCart()) continue;
                if (!product.isCartSelected()) continue;

                int quantity = product.getCartQuantity();

                if (quantity <= 0) continue;

                if (productNames.length() > 0) {
                    productNames.append(", ");
                }

                productNames.append(product.getName());

                totalQuantity += quantity;
                totalPrice += product.getPrice() * quantity;
            }

            if (totalQuantity == 0) {

                Toast.makeText(
                        this,
                        cartProducts.isEmpty()
                                ? "장바구니에 상품이 없습니다."
                                : "주문할 상품을 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            final String names = productNames.toString();
            final int quantity = totalQuantity;
            final int price = totalPrice;

            // 기존 체형 적합도 경고 유지
            List<FitResult> warnings =
                    SizeFitChecker.checkCart();

            if (!warnings.isEmpty()) {

                SizeWarningDialog.show(
                        CartActivity.this,
                        warnings,
                        SizeWarningDialog.Action.ORDER,
                        () -> goOrder(
                                names, quantity, price
                        )
                );

                return;
            }

            goOrder(names, quantity, price);
        });
    }

    // 주문 화면으로 이동
    private void goOrder(
            String productNames,
            int totalQuantity,
            int totalPrice
    ) {

        Intent intent = new Intent(
                CartActivity.this,
                OrderActivity.class
        );

        intent.putExtra(
                "productNames", productNames
        );

        intent.putExtra(
                "totalQuantity", totalQuantity
        );

        intent.putExtra(
                "totalPrice", totalPrice
        );

        startActivity(intent);
    }

    // 장바구니 상품과 금액 갱신
    private void showCartProducts() {

        cartProducts.clear();

        int cartQuantity = 0;
        int totalQuantity = 0;
        int totalPrice = 0;
        int selectedCount = 0;

        for (Product product : DummyData.getProducts()) {

            if (!product.isInCart()) continue;

            int quantity = product.getCartQuantity();

            if (quantity <= 0) continue;

            cartProducts.add(product);
            cartQuantity += quantity;

            if (!product.isCartSelected()) continue;

            selectedCount++;
            totalQuantity += quantity;
            totalPrice += product.getPrice() * quantity;
        }

        if (titleCount != null) {
            titleCount.setText(
                    "장바구니 " + cartQuantity
            );
        }

        if (totalQuantityText != null) {
            totalQuantityText.setText(
                    totalQuantity + "개"
            );
        }

        if (selectAllCheck != null) {
            selectAllCheck.setChecked(
                    !cartProducts.isEmpty()
                            && selectedCount == cartProducts.size()
            );
            selectAllCheck.setText(
                    "전체 선택 (" + selectedCount + "/" + cartProducts.size() + ")"
            );
        }

        if (totalPriceText != null) {
            totalPriceText.setText(
                    formatPrice(totalPrice)
            );
        }

        boolean empty = cartProducts.isEmpty();

        if (emptyLayout != null) {
            emptyLayout.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );
        }

        recyclerCart.setVisibility(
                empty ? View.GONE : View.VISIBLE
        );

        if (selectAllRow != null) {
            selectAllRow.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        }

        if (selectAllDivider != null) {
            selectAllDivider.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        }

        if (recommendationSection != null) {
            recommendationSection.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );
        }

        if (summaryLayout != null) {
            summaryLayout.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        }

        if (btnGoOrder != null) {

            btnGoOrder.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );

            btnGoOrder.setText(
                    selectedCount == 0
                            ? "주문할 상품을 선택해주세요"
                            : formatPrice(totalPrice) + " 주문하기"
            );
        }

        if (cartAdapter != null) {
            cartAdapter.notifyDataSetChanged();
        }

        renderRecommendations();
    }

    // 홈 이동
    private void goHome() {

        Intent intent = new Intent(
                this, MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        startActivity(intent);
    }

    // 공통 UI 함수
    private TextView makeText(
            String text,
            int size,
            boolean bold,
            int color
    ) {

        TextView view = new TextView(this);

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
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);

        return drawable;
    }

    private View divider() {

        View line = new View(this);
        line.setBackgroundColor(BORDER);

        line.setLayoutParams(
                new LinearLayout.LayoutParams(
                        -1, dp(1)
                )
        );

        return line;
    }

    private String formatPrice(int price) {

        return NumberFormat
                .getNumberInstance(Locale.KOREA)
                .format(price) + "원";
    }

    private int dp(float value) {

        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }
}
