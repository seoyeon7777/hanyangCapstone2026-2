package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.LocalLikes;
import com.example.capstone.data.ProductImages;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.Product;
import com.example.capstone.data.SizeChart;
import com.example.capstone.data.SizeFitChecker;
import com.example.capstone.data.UserBodyProfile;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

public class ProductDetailActivity extends AppCompatActivity {

    private TextView textDetailBrand;
    private TextView textDetailName;
    private TextView textDetailPrice;

    private TextView btnBack;
    private ImageView btnDetailHeart;
    private TextView btnAddCart;
    private TextView btnFitting;
    private TextView btnReview;
    private TextView textSizeRecommend;

    private View layoutOptionSelect;

    private Product currentProduct;

    private String selectedSize = "";
    private String selectedColor = "";
    private int selectedQuantity = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_product_detail);


        // =========================
        // XML 연결
        // =========================

        textDetailBrand = findViewById(R.id.textDetailBrand);
        textDetailName = findViewById(R.id.textDetailName);
        textDetailPrice = findViewById(R.id.textDetailPrice);

        btnBack = findViewById(R.id.btnBack);
        btnDetailHeart = findViewById(R.id.btnDetailHeart);
        btnAddCart = findViewById(R.id.btnAddCart);
        btnFitting = findViewById(R.id.btnFitting);
        btnReview = findViewById(R.id.btnReview);
        textSizeRecommend = findViewById(R.id.textSizeRecommend);

        layoutOptionSelect = findViewById(R.id.layoutOptionSelect);


        // =========================
        // 이전 화면에서 전달받은 상품 정보
        // =========================

        int productId =
                getIntent().getIntExtra(
                        "product_id",
                        -1
                );

        String brand =
                getIntent().getStringExtra(
                        "product_brand"
                );

        String name =
                getIntent().getStringExtra(
                        "product_name"
                );

        int price =
                getIntent().getIntExtra(
                        "product_price",
                        0
                );

        String serverId = getIntent().getStringExtra("server_product_id");


        // =========================
        // DummyData에서 실제 Product 찾기
        // =========================

        if (serverId != null && !serverId.isEmpty()) {

            currentProduct = RecommendApi.serverProduct(serverId);

            if (currentProduct == null && name != null) {

                currentProduct = new Product(productId, brand, name, price);

                currentProduct.setFilterInfo(
                        getIntent().getStringExtra("product_category"),
                        null,
                        null,
                        null
                );

                currentProduct.setServerInfo(serverId, "");

                currentProduct.setImageUrl(
                        getIntent().getStringExtra("product_image_url")
                );
            }

        } else {

            currentProduct = DummyData.getProductById(productId);
        }

        if (currentProduct != null) {
            brand = currentProduct.getBrand();
            name = currentProduct.getName();
            price = currentProduct.getPrice();
        }


        // =========================
        // 화면에 상품 정보 표시
        // =========================

        if (brand != null) {
            textDetailBrand.setText(brand);
        }

        if (name != null) {
            textDetailName.setText(name);
        }

        String formattedPrice =
                NumberFormat
                        .getNumberInstance(Locale.KOREA)
                        .format(price);

        textDetailPrice.setText(
                formattedPrice + "원"
        );

        // 추천 서버 상품은 DB에 가격이 없으면 0으로 온다 → "0원" 대신 안내
        if (price == 0) {
            textDetailPrice.setText("가격 정보 준비 중");
        }

        ProductImages.bind(
                findViewById(R.id.frameDetailImage),
                findViewById(R.id.textDetailImageLabel),
                findViewById(R.id.imgDetailProduct),
                currentProduct
        );

        // =========================
        // 옵션 선택 줄
        // =========================

        layoutOptionSelect.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }

            showFreshOptionSheet();
        });


        // =========================
        // 찜 상태 표시
        // =========================

        updateHeart();


        // =========================
        // 찜 버튼
        // =========================

        btnDetailHeart.setOnClickListener(v -> {

            if (currentProduct == null) {
                return;
            }

            if (!LoginSession.isLoggedIn(this)) {
                startActivity(new Intent(this, LoginMainActivity.class));
                return;
            }

            currentProduct.setLiked(
                    !currentProduct.isLiked()
            );

            LocalLikes.save(this, currentProduct);

            // 서버 상품: 찜 상태 저장 + 추천 서버에 like
            RecommendApi.saveLike(this, currentProduct);
            updateHeart();
        });


        // =========================
        // 장바구니 버튼
        //
        // 담을 때마다 옵션 선택 창을 처음 상태로 띄운다.
        // =========================

        btnAddCart.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }

            showFreshOptionSheet();
        });


        // =========================
        // 바로 구매 버튼
        // =========================

        btnFitting.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }

            showFreshOptionSheet();
        });


        // =========================
        // 리뷰
        // =========================

        btnReview.setOnClickListener(v -> {

            Intent intent = new Intent(
                    ProductDetailActivity.this,
                    ReviewActivity.class
            );

            if (currentProduct != null) {

                intent.putExtra(
                        "product_id",
                        currentProduct.getId()
                );

                intent.putExtra(
                        "server_product_id",
                        currentProduct.getServerId()
                );
            }

            startActivity(intent);
        });


        // =========================
        // 뒤로가기
        // =========================

        btnBack.setOnClickListener(
                v -> finish()
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        BodyProfileStore.syncActiveProfile(this);
        refreshServerLike();
        updateHeart();
        updateReviewCount();
        updateSizeRecommendText();
        updateBuyButtons();
    }

    private void refreshServerLike() {

        if (currentProduct == null || !currentProduct.isFromServer()) {
            return;
        }

        try {
            JSONObject liked = new JSONObject(
                    AccountPrefs.recommend(this).getString(
                            AccountPrefs.key(this, AccountPrefs.RECOMMEND_LIKED),
                            "{}"
                    )
            );

            currentProduct.setLiked(liked.has(currentProduct.getServerId()));

        } catch (JSONException e) {
            currentProduct.setLiked(false);
        }
    }

    private void updateBuyButtons() {

        boolean canBuy = currentProduct == null || currentProduct.getPrice() > 0;

        for (View button : new View[]{layoutOptionSelect, btnFitting, btnAddCart}) {
            button.setEnabled(canBuy);
            button.setAlpha(canBuy ? 1f : 0.4f);
        }
    }

    private boolean checkProduct() {
        if (currentProduct != null) {
            return true;
        }
        Toast.makeText(
                ProductDetailActivity.this,
                "상품 정보를 찾을 수 없습니다.",
                Toast.LENGTH_SHORT
        ).show();
        return false;
    }

    private void updateReviewCount() {
        if (currentProduct == null) {
            return;
        }
        btnReview.setText("리뷰 보기  ›");
    }

    private void updateSizeRecommendText() {
        if (currentProduct == null) {
            textSizeRecommend.setVisibility(View.GONE);
            return;
        }
        UserBodyProfile profile = UserBodyProfile.getInstance();
        if (!RecommendApi.hasDiagnosis(this) || !profile.hasMeasurement()) {
            textSizeRecommend.setText("내 치수 사이즈 확인하러 가기  ›");
            textSizeRecommend.setOnClickListener(v ->
                    startActivity(new Intent(this, FittingActivity.class)));
            return;
        }
        textSizeRecommend.setOnClickListener(null);
        textSizeRecommend.setClickable(false);
        String recommended = SizeFitChecker.recommendSize(SizeChart.categoryOf(currentProduct), profile);
        textSizeRecommend.setText(recommended == null
                ? "이 상품은 사이즈 비교 정보가 없어요"
                : "내 치수 기준 추천 사이즈: " + recommended);
    }


    // =========================
    // 옵션 선택 창 띄우기
    //
    // 고른 옵션을 저장한 뒤 바로 구매 또는 장바구니 담기를 진행한다.
    // =========================

    private void showOptionSheet() {

        ProductOptionSheet.show(
                this,
                currentProduct,
                selectedSize,
                selectedColor,
                selectedQuantity,
                "바로 구매",
                (size, color, quantity) -> {

                    saveSelection(size, color, quantity);
                    checkFitThen(this::buyNow, SizeWarningDialog.Action.ORDER);
                },
                "장바구니 담기",
                (size, color, quantity) -> {

                    saveSelection(size, color, quantity);
                    checkFitThen(this::addToCart, SizeWarningDialog.Action.CART);
                }
        );
    }

    private void showFreshOptionSheet() {

        saveSelection("", "", 1);
        showOptionSheet();
    }

    private void saveSelection(String size, String color, int quantity) {

        selectedSize = size;
        selectedColor = color;
        selectedQuantity = quantity;
    }


    // =========================
    // 사이즈 적합도 확인 후 장바구니에 담기
    // =========================

    private void checkFitThen(Runnable action, SizeWarningDialog.Action warningAction) {

        FitResult fitResult =
                SizeFitChecker.check(
                        currentProduct,
                        selectedSize
                );


        // 맞지 않거나 체형 측정을 안 했으면 주의 팝업
        if (fitResult.needsWarning()) {

            SizeWarningDialog.show(
                    ProductDetailActivity.this,
                    fitResult,
                    warningAction,
                    new SizeWarningDialog.OnActionListener() {

                        @Override
                        public void onContinue() {

                            action.run();
                        }

                        @Override
                        public void onCancel() {

                            // 사이즈를 다시 고르도록
                            // 사이즈만 비우고 옵션 창을 다시 연다
                            selectedSize = "";

                            showOptionSheet();
                        }
                    }
            );

            return;
        }


        action.run();
    }


    // =========================
    // 장바구니에 담기
    //
    // 사이즈 경고를 확인한 뒤에도
    // 같은 처리를 하므로 따로 분리
    // =========================

    private void addToCart() {

        if (currentProduct == null) {
            return;
        }

        if (CartStore.quantityOf(this, currentProduct, selectedSize, selectedColor) + selectedQuantity
                > CartStore.MAX_QUANTITY) {
            Toast.makeText(this, CartStore.MAX_QUANTITY_MESSAGE, Toast.LENGTH_SHORT).show();
            return;
        }

        CartStore.add(this, currentProduct, selectedSize, selectedColor, selectedQuantity);

        String message = selectedSize + " / " + selectedColor + " / " + selectedQuantity + "개";

        AppDialog.show(this, false,
                "장바구니에 담았어요",
                message,
                "계속 쇼핑", null,
                "장바구니 보기", () -> startActivity(new Intent(this, CartActivity.class)));
    }

    // 바로 구매: 장바구니는 건드리지 않고 이 상품·옵션만 결제 화면으로 넘긴다
    private void buyNow() {

        if (currentProduct == null) {
            return;
        }

        Intent intent = new Intent(this, OrderActivity.class);
        intent.putExtra("productNames", currentProduct.getName());
        intent.putExtra("productOption", selectedSize + " / " + selectedColor);
        intent.putExtra("totalQuantity", selectedQuantity);
        intent.putExtra("totalPrice", currentProduct.getPrice() * selectedQuantity);
        if (currentProduct.isFromServer()) {
            intent.putExtra(OrderActivity.EXTRA_BUY_NOW_SERVER_ID, currentProduct.getServerId());
        }
        startActivity(intent);
    }


    // =========================
    // 찜 하트 표시
    // =========================

    private void updateHeart() {

        Product product = currentProduct;

        if (product == null) {

            btnDetailHeart.setImageResource(R.drawable.ic_heart_outline);
            return;
        }


        if (product.isLiked()) {

            btnDetailHeart.setImageResource(R.drawable.ic_heart_filled);

        } else {

            btnDetailHeart.setImageResource(R.drawable.ic_heart_outline);
        }
    }
}
