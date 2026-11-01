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
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.ProductImages;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.Product;
import com.example.capstone.data.ShopStore;
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
    private TextView textSizeRecommend;

    private View layoutOptionSelect;
    private TextView textOptionSummary;

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
        textOptionSummary = findViewById(R.id.textOptionSummary);


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
        }

        if (currentProduct == null) {
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

        updateOptionSummary();


        // =========================
        // 옵션 선택 줄
        // =========================

        layoutOptionSelect.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }

            showOptionSheet(
                    "선택 완료",
                    null
            );
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

            currentProduct.setLiked(
                    !currentProduct.isLiked()
            );

            // 서버 상품: 찜 상태 저장 + 추천 서버에 like
            RecommendApi.saveLike(this, currentProduct);
            updateHeart();
        });


        // =========================
        // 장바구니 버튼
        //
        // 옵션을 이미 골랐으면 바로 체형과 맞는지 확인하고,
        // 아직 안 골랐으면 옵션 선택 창부터 띄운다.
        // =========================

        btnAddCart.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }


            if (isOptionSelected()) {

                checkFitThen(this::addToCart, "장바구니 담기");

                return;
            }


            showOptionSheet(
                    "장바구니 담기",
                    () -> checkFitThen(this::addToCart, "장바구니 담기")
            );
        });


        // =========================
        // 3D 가상 피팅
        //
        // 옵션을 아직 안 골랐으면 옵션 창부터 띄운다.
        // =========================

        btnFitting.setOnClickListener(v -> {

            if (!checkProduct()) {
                return;
            }


            if (!isOptionSelected()) {

                showOptionSheet(
                        "바로 구매",
                        () -> checkFitThen(this::buyNow, "바로 구매")
                );

                return;
            }


            checkFitThen(this::buyNow, "바로 구매");
        });


        textSizeRecommend.setOnClickListener(v -> showSizeRecommendation());


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
        updateHeart();
        updateReviewCount();
        updateSizeRecommendText();
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
        int count = MyPageStore.getProductReviews(
                currentProduct.getId(), currentProduct.getServerId()).size();
        btnReview.setText(count == 0 ? "리뷰 보기  ›" : "리뷰 보기 (" + count + ")  ›");
    }

    private void updateSizeRecommendText() {
        if (currentProduct == null) {
            textSizeRecommend.setVisibility(View.GONE);
            return;
        }
        UserBodyProfile profile = UserBodyProfile.getInstance();
        if (!profile.hasMeasurement()) {
            textSizeRecommend.setText("체형을 측정하면 내 사이즈를 추천해 드려요  ›");
            return;
        }
        String recommended = SizeFitChecker.recommendSize(SizeChart.categoryOf(currentProduct), profile);
        textSizeRecommend.setText(recommended == null
                ? "이 상품은 사이즈 비교 정보가 없어요"
                : "내 치수 기준 추천 사이즈: " + recommended + "  ›");
    }

    private void showSizeRecommendation() {
        UserBodyProfile profile = UserBodyProfile.getInstance();
        if (!profile.hasMeasurement()) {
            AppDialog.show(this, false, "체형 측정이 필요해요",
                    "피팅 메뉴에서 키·몸무게와 전신 사진으로 치수를 재면, 상품마다 맞는 사이즈를 알려 드려요.",
                    "닫기", null,
                    "측정하러 가기", () -> startActivity(new Intent(this, FittingActivity.class)));
            return;
        }
        String recommended = SizeFitChecker.recommendSize(SizeChart.categoryOf(currentProduct), profile);
        if (recommended == null) {
            AppDialog.alert(this, "사이즈 추천", "이 상품은 사이즈 비교 정보가 없어요.", "확인");
            return;
        }
        AppDialog.show(this, false, "사이즈 추천",
                "저장한 치수로 보면 " + recommended + " 사이즈가 가장 잘 맞아요.",
                "닫기", null,
                recommended + " 선택", () -> {
                    selectedSize = recommended;
                    updateOptionSummary();
                    showOptionSheet("선택 완료", null);
                });
    }


    // =========================
    // 옵션 선택 창 띄우기
    //
    // 고른 옵션을 저장한 뒤 afterSelect를 실행한다.
    // =========================

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

                    selectedSize = size;
                    selectedColor = color;
                    selectedQuantity = quantity;

                    updateOptionSummary();

                    if (afterSelect != null) {
                        afterSelect.run();
                    }
                }
        );
    }


    // =========================
    // 사이즈 적합도 확인 후 장바구니에 담기
    // =========================

    private void checkFitThen(Runnable action, String confirmText) {

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
                    SizeWarningDialog.Action.CART,
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

                            updateOptionSummary();

                            showOptionSheet(
                                    confirmText,
                                    () -> checkFitThen(action, confirmText)
                            );
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

        boolean added = ShopStore.add(
                currentProduct,
                selectedSize,
                selectedColor,
                selectedQuantity
        );

        AppDialog.show(this, false,
                "장바구니에 담았어요",
                selectedSize + " / " + selectedColor + " / " + selectedQuantity + "개"
                        + (added ? "" : "\n같은 옵션이 이미 있어서 수량을 더했어요."),
                "계속 쇼핑", null,
                "장바구니 보기", () -> startActivity(new Intent(this, CartActivity.class)));
    }

    private void buyNow() {

        if (currentProduct == null) {
            return;
        }

        startActivity(OrderActivity.buyNowIntent(
                this,
                currentProduct,
                selectedSize,
                selectedColor,
                selectedQuantity
        ));
    }


    // =========================
    // 옵션을 다 골랐는지 확인
    // =========================

    private boolean isOptionSelected() {

        return selectedSize != null
                && !selectedSize.isEmpty()
                && selectedColor != null
                && !selectedColor.isEmpty();
    }


    // =========================
    // 옵션 선택 줄 문구 갱신
    // =========================

    private void updateOptionSummary() {

        if (isOptionSelected()) {

            textOptionSummary.setText(
                    selectedSize
                            + " / "
                            + selectedColor
                            + " / "
                            + selectedQuantity
                            + "개"
            );

            textOptionSummary.setTextColor(0xFF111111);

        } else {

            textOptionSummary.setText(
                    "사이즈 / 색상을 선택해주세요"
            );

            textOptionSummary.setTextColor(0xFF888888);
        }
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
