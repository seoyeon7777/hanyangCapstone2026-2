package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.Product;
import com.example.capstone.data.ProductImages;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.SizeChart;
import com.example.capstone.data.SizeFitChecker;
import com.example.capstone.data.UserBodyProfile;

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

    private View layoutOptionSelect;
    private TextView textOptionSummary;
    private TextView textFitGuide;

    private Product currentProduct;
    private Product serverProduct;

    private String selectedSize = "";
    private String selectedColor = "";
    private int selectedQuantity = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_product_detail);

        initViews();
        loadProduct();
        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (btnDetailHeart != null) {
            updateHeart();
        }

        if (btnReview != null) {
            btnReview.setText("리뷰 보기  ›");
        }

        updateSizeRecommendation();
    }

    private void initViews() {

        textDetailBrand = findViewById(R.id.textDetailBrand);
        textDetailName = findViewById(R.id.textDetailName);
        textDetailPrice = findViewById(R.id.textDetailPrice);

        btnBack = findViewById(R.id.btnBack);
        btnDetailHeart = findViewById(R.id.btnDetailHeart);
        btnAddCart = findViewById(R.id.btnAddCart);
        btnFitting = findViewById(R.id.btnFitting);
        btnReview = findViewById(R.id.btnReview);

        layoutOptionSelect = findViewById(R.id.layoutOptionSelect);
        textOptionSummary = findViewById(R.id.textOptionSummary);

        // 기존 화면에 있을 때만 연결
        int fitGuideId = getResources().getIdentifier(
                "textFitGuide",
                "id",
                getPackageName()
        );

        if (fitGuideId != 0) {
            View view = findViewById(fitGuideId);

            if (view instanceof TextView) {
                textFitGuide = (TextView) view;
            }
        }
    }

    private void loadProduct() {

        int productId = getIntent().getIntExtra(
                "product_id",
                -1
        );

        String brand = getIntent().getStringExtra(
                "product_brand"
        );

        String name = getIntent().getStringExtra(
                "product_name"
        );

        int price = getIntent().getIntExtra(
                "product_price",
                0
        );

        String serverId = getIntent().getStringExtra(
                "server_product_id"
        );

        // 일반 상품 조회
        currentProduct = DummyData.getProductById(productId);

        // 서버 추천 상품 조회
        if (serverId != null && !serverId.isEmpty()) {

            serverProduct = RecommendApi.serverProduct(serverId);

            if (currentProduct == null) {
                currentProduct = serverProduct;
            }
        }

        Product displayProduct = currentProduct != null
                ? currentProduct
                : serverProduct;

        if (displayProduct != null) {

            if (brand == null || brand.isEmpty()) {
                brand = displayProduct.getBrand();
            }

            if (name == null || name.isEmpty()) {
                name = displayProduct.getName();
            }

            if (price == 0) {
                price = displayProduct.getPrice();
            }
        }

        textDetailBrand.setText(
                brand == null ? "CAPSTONE SELECT" : brand
        );

        textDetailName.setText(
                name == null ? "상품 정보" : name
        );

        if (price <= 0) {

            textDetailPrice.setText("가격 정보 준비 중");

        } else {

            textDetailPrice.setText(
                    NumberFormat.getNumberInstance(Locale.KOREA)
                            .format(price) + "원"
            );
        }

        // 팀원의 상품 이미지 기능.
        // 현재 XML에 이미지 뷰가 있으면 연결한다.
        bindProductImage();

        // 기존 장바구니에 저장된 옵션 복원
        if (currentProduct != null) {

            selectedSize = safe(
                    currentProduct.getSelectedSize()
            );

            selectedColor = safe(
                    currentProduct.getSelectedColor()
            );

            if (currentProduct.getCartQuantity() > 0) {
                selectedQuantity = currentProduct.getCartQuantity();
            }

            if (currentProduct.isInCart()) {
                btnAddCart.setText("장바구니에 담김");
            }
        }

        updateOptionSummary();
        updateHeart();
        updateSizeRecommendation();
    }

    private void bindProductImage() {

        int frameId = getResources().getIdentifier(
                "frameDetailImage",
                "id",
                getPackageName()
        );

        int labelId = getResources().getIdentifier(
                "textDetailImageLabel",
                "id",
                getPackageName()
        );

        int imageId = getResources().getIdentifier(
                "imgDetailProduct",
                "id",
                getPackageName()
        );

        if (frameId == 0 || labelId == 0 || imageId == 0) {
            return;
        }

        View frame = findViewById(frameId);
        View label = findViewById(labelId);
        View image = findViewById(imageId);

        if (frame != null
                && label instanceof TextView
                && image instanceof ImageView) {

            ProductImages.bind(
                    frame,
                    (TextView) label,
                    (ImageView) image,
                    currentProduct
            );
        }
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        // 찜 버튼
        btnDetailHeart.setOnClickListener(v -> {

            Product product = currentProduct != null
                    ? currentProduct
                    : serverProduct;

            if (product == null) {

                Toast.makeText(
                        this,
                        "상품 정보를 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            product.setLiked(!product.isLiked());

            RecommendApi.saveLike(this, product);

            updateHeart();
        });

        // 옵션 선택
        layoutOptionSelect.setOnClickListener(v -> {

            if (!canUseProductOptions()) {
                return;
            }

            showOptionSheet("선택 완료", null);
        });

        // 장바구니 담기
        btnAddCart.setOnClickListener(v -> {

            if (!canUseProductOptions()) {
                return;
            }

            // 이미 옵션을 골랐다면 그대로 담기
            if (isOptionSelected()) {

                checkFitAndAddToCart();

            } else {

                showOptionSheet(
                        "장바구니 담기",
                        this::checkFitAndAddToCart
                );
            }
        });

        // 기존 3D 피팅 기능 유지
        btnFitting.setOnClickListener(v -> {

            if (!canUseProductOptions()) {
                return;
            }

            if (!isOptionSelected()) {

                showOptionSheet(
                        "3D 가상피팅 하기",
                        this::goFitting
                );

            } else {

                goFitting();
            }
        });

        // 리뷰는 보기만 가능
        btnReview.setOnClickListener(v -> {

            Intent intent = new Intent(
                    this,
                    ReviewActivity.class
            );

            Product product = currentProduct != null
                    ? currentProduct
                    : serverProduct;

            if (product != null) {

                intent.putExtra(
                        "product_id",
                        product.getId()
                );

                intent.putExtra(
                        "server_product_id",
                        product.getServerId()
                );
            }

            startActivity(intent);
        });

        // 기존 사이즈 안내 + 팀원의 추천 기능
        if (textFitGuide != null) {

            textFitGuide.setOnClickListener(v -> {
                showSizeRecommendation();
            });
        }
    }

    private boolean canUseProductOptions() {

        if (currentProduct == null) {

            Toast.makeText(
                    this,
                    "상품 정보를 찾을 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        if (currentProduct.getPrice() <= 0) {

            Toast.makeText(
                    this,
                    "이 상품은 아직 구매할 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    private void showOptionSheet(
            String confirmText,
            Runnable afterSelect
    ) {

        ProductOptionSheet.show(
                this,
                currentProduct,
                selectedSize,
                selectedColor,
                selectedQuantity,
                confirmText,
                (size, color, quantity) -> {

                    selectedSize = safe(size);
                    selectedColor = safe(color);
                    selectedQuantity = Math.max(1, quantity);

                    updateOptionSummary();

                    if (afterSelect != null) {
                        afterSelect.run();
                    }
                }
        );
    }

    private void updateSizeRecommendation() {

        if (textFitGuide == null || currentProduct == null) {
            return;
        }

        UserBodyProfile profile = UserBodyProfile.getInstance();

        if (!profile.hasMeasurement()) {

            textFitGuide.setText(
                    "체형 측정 후 내 사이즈 확인하기  ›"
            );

            return;
        }

        String recommended = SizeFitChecker.recommendSize(
                SizeChart.categoryOf(currentProduct),
                profile
        );

        if (recommended == null) {

            textFitGuide.setText(
                    "내 체형에 맞는 사이즈 확인하기  ›"
            );

        } else {

            textFitGuide.setText(
                    "내 치수 기준 추천 사이즈: "
                            + recommended
                            + "  ›"
            );
        }
    }

    private void showSizeRecommendation() {

        if (!canUseProductOptions()) {
            return;
        }

        UserBodyProfile profile = UserBodyProfile.getInstance();

        if (!profile.hasMeasurement()) {

            // 측정 정보가 없으면 기존 사이즈 안내 표시
            if (!isOptionSelected()) {

                showOptionSheet(
                        "사이즈 확인",
                        this::showFitGuide
                );

            } else {

                showFitGuide();
            }

            return;
        }

        String recommended = SizeFitChecker.recommendSize(
                SizeChart.categoryOf(currentProduct),
                profile
        );

        if (recommended == null) {

            AppDialog.alert(
                    this,
                    "사이즈 추천",
                    "이 상품은 사이즈 비교 정보가 없어요.",
                    "확인"
            );

            return;
        }

        AppDialog.show(
                this,
                false,
                "사이즈 추천",
                "저장된 신체 치수를 기준으로 "
                        + recommended
                        + " 사이즈를 추천해요.",
                "닫기",
                null,
                recommended + " 선택",
                () -> {

                    selectedSize = recommended;
                    updateOptionSummary();

                    showOptionSheet(
                            "선택 완료",
                            null
                    );
                }
        );
    }

    private void showFitGuide() {

        if (currentProduct == null) {
            return;
        }

        FitResult fitResult = SizeFitChecker.check(
                currentProduct,
                selectedSize
        );

        SizeWarningDialog.show(
                this,
                fitResult,
                SizeWarningDialog.Action.CART,
                new SizeWarningDialog.OnActionListener() {

                    @Override
                    public void onContinue() {
                        // 사이즈 안내만 확인
                    }

                    @Override
                    public void onCancel() {

                        selectedSize = "";
                        updateOptionSummary();

                        showOptionSheet(
                                "사이즈 확인",
                                ProductDetailActivity.this::showFitGuide
                        );
                    }
                }
        );
    }

    private void checkFitAndAddToCart() {

        if (!isOptionSelected()) {

            showOptionSheet(
                    "장바구니 담기",
                    this::checkFitAndAddToCart
            );

            return;
        }

        FitResult fitResult = SizeFitChecker.check(
                currentProduct,
                selectedSize
        );

        if (fitResult.needsWarning()) {

            SizeWarningDialog.show(
                    this,
                    fitResult,
                    SizeWarningDialog.Action.CART,
                    new SizeWarningDialog.OnActionListener() {

                        @Override
                        public void onContinue() {
                            addToCart();
                        }

                        @Override
                        public void onCancel() {

                            selectedSize = "";
                            updateOptionSummary();

                            showOptionSheet(
                                    "장바구니 담기",
                                    ProductDetailActivity.this
                                            ::checkFitAndAddToCart
                            );
                        }
                    }
            );

            return;
        }

        addToCart();
    }

    private void addToCart() {

        if (currentProduct == null) {
            return;
        }

        boolean sameOption =
                currentProduct.isInCart()
                        && selectedSize.equals(
                        safe(currentProduct.getSelectedSize())
                )
                        && selectedColor.equals(
                        safe(currentProduct.getSelectedColor())
                );

        boolean optionChanged =
                currentProduct.isInCart() && !sameOption;

        int quantity;

        if (sameOption) {

            quantity = currentProduct.getCartQuantity()
                    + selectedQuantity;

        } else {

            quantity = selectedQuantity;
        }

        putInCart(quantity);

        btnAddCart.setText("장바구니에 담김");

        String message =
                selectedColor
                        + " / "
                        + selectedSize
                        + " / "
                        + quantity
                        + "개";

        if (sameOption) {

            message += "\n같은 옵션이 있어 수량을 추가했어요.";

        } else if (optionChanged) {

            message += "\n기존 장바구니 옵션을 변경했어요.";
        }

        AppDialog.show(
                this,
                false,
                "장바구니에 담았어요",
                message,
                "계속 쇼핑",
                null,
                "장바구니 보기",
                () -> startActivity(
                        new Intent(this, CartActivity.class)
                )
        );
    }

    private void putInCart(int quantity) {

        currentProduct.setInCart(true);
        currentProduct.setCartSelected(true);

        currentProduct.setSelectedSize(selectedSize);
        currentProduct.setSelectedColor(selectedColor);

        currentProduct.setCartQuantity(
                Math.max(1, quantity)
        );
    }

    // 기존 3D 피팅 이동 유지
    private void goFitting() {

        if (currentProduct == null) {
            return;
        }

        Intent intent = new Intent(
                this,
                FittingActivity.class
        );

        intent.putExtra(
                "product_id",
                currentProduct.getId()
        );

        intent.putExtra(
                "product_name",
                currentProduct.getName()
        );

        intent.putExtra(
                "product_brand",
                currentProduct.getBrand()
        );

        intent.putExtra(
                "product_price",
                currentProduct.getPrice()
        );

        intent.putExtra(
                "selected_size",
                selectedSize
        );

        intent.putExtra(
                "selected_color",
                selectedColor
        );

        intent.putExtra(
                "selected_quantity",
                selectedQuantity
        );

        startActivity(intent);
    }

    private boolean isOptionSelected() {

        return !safe(selectedSize).isEmpty()
                && !safe(selectedColor).isEmpty();
    }

    private void updateOptionSummary() {

        if (textOptionSummary == null) {
            return;
        }

        if (isOptionSelected()) {

            textOptionSummary.setText(
                    selectedColor
                            + " / "
                            + selectedSize
                            + " / "
                            + selectedQuantity
                            + "개"
            );

            textOptionSummary.setTextColor(0xFF111111);

        } else {

            textOptionSummary.setText(
                    "색상 · 사이즈 · 수량 선택"
            );

            textOptionSummary.setTextColor(0xFF888888);
        }
    }

    private void updateHeart() {

        if (btnDetailHeart == null) {
            return;
        }

        Product product = currentProduct != null
                ? currentProduct
                : serverProduct;

        if (product != null && product.isLiked()) {

            btnDetailHeart.setImageResource(
                    R.drawable.ic_heart_filled
            );

        } else {

            btnDetailHeart.setImageResource(
                    R.drawable.ic_heart_outline
            );
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
