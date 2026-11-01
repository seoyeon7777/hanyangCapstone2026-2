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
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.FitResult;
import com.example.capstone.data.Product;
import com.example.capstone.data.SizeFitChecker;

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

    private Product currentProduct;

    // 추천 서버 상품 (DummyData에 없음). 찜만 된다 — 옵션·장바구니는 아직 DummyData 상품만
    private Product serverProduct;

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


        // =========================
        // DummyData에서 실제 Product 찾기
        // =========================

        for (Product product : DummyData.getProducts()) {

            if (product.getId() == productId) {

                currentProduct = product;
                break;
            }
        }

        if (currentProduct == null) {
            serverProduct = RecommendApi.serverProduct(
                    getIntent().getStringExtra("server_product_id")
            );
        }

        // 추천 서버 상품은 DB에 가격이 없으면 0으로 온다 → "0원" 대신 안내
        if (serverProduct != null && price == 0) {
            textDetailPrice.setText("가격 정보 준비 중");
        }


        // =========================
        // 이미 장바구니 옵션이 있으면
        // 다시 상세에 들어왔을 때 선택 복구
        // =========================

        if (currentProduct != null) {

            selectedSize =
                    currentProduct.getSelectedSize();

            selectedColor =
                    currentProduct.getSelectedColor();

            if (currentProduct.getCartQuantity() > 0) {

                selectedQuantity =
                        currentProduct.getCartQuantity();
            }
        }

        updateOptionSummary();


        // =========================
        // 옵션 선택 줄
        // =========================

        layoutOptionSelect.setOnClickListener(v -> {

            if (showServerProductNotice()) {
                return;
            }

            if (currentProduct == null) {
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

            // 서버 상품: 찜 상태 저장 + 추천 서버에 like
            if (currentProduct == null && serverProduct != null) {

                serverProduct.setLiked(
                        !serverProduct.isLiked()
                );

                RecommendApi.saveLike(this, serverProduct);
                updateHeart();
                return;
            }

            if (currentProduct == null) {
                return;
            }

            currentProduct.setLiked(
                    !currentProduct.isLiked()
            );

            updateHeart();
        });


        // =========================
        // 장바구니 버튼
        //
        // 옵션을 이미 골랐으면 바로 체형과 맞는지 확인하고,
        // 아직 안 골랐으면 옵션 선택 창부터 띄운다.
        // =========================

        btnAddCart.setOnClickListener(v -> {

            if (showServerProductNotice()) {
                return;
            }

            if (currentProduct == null) {

                Toast.makeText(
                        ProductDetailActivity.this,
                        "상품 정보를 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            if (isOptionSelected()) {

                checkFitAndAddToCart();

                return;
            }


            showOptionSheet(
                    "장바구니 담기",
                    this::checkFitAndAddToCart
            );
        });


        // =========================
        // 3D 가상 피팅
        //
        // 옵션을 아직 안 골랐으면 옵션 창부터 띄운다.
        // =========================

        btnFitting.setOnClickListener(v -> {

            if (showServerProductNotice()) {
                return;
            }

            if (currentProduct == null) {

                Toast.makeText(
                        ProductDetailActivity.this,
                        "상품 정보를 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            if (!isOptionSelected()) {

                showOptionSheet(
                        "3D 가상피팅 하기",
                        this::goFitting
                );

                return;
            }


            goFitting();
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

    private void checkFitAndAddToCart() {

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

                            addToCart();
                        }

                        @Override
                        public void onCancel() {

                            // 사이즈를 다시 고르도록
                            // 사이즈만 비우고 옵션 창을 다시 연다
                            selectedSize = "";

                            updateOptionSummary();

                            showOptionSheet(
                                    "장바구니 담기",
                                    ProductDetailActivity.this::checkFitAndAddToCart
                            );
                        }
                    }
            );

            return;
        }


        addToCart();
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


        // 장바구니 상태
        currentProduct.setInCart(true);
        currentProduct.setCartSelected(true);

        // ERD product_options 역할을 임시 저장
        currentProduct.setSelectedSize(
                selectedSize
        );

        currentProduct.setSelectedColor(
                selectedColor
        );

        currentProduct.setCartQuantity(
                selectedQuantity
        );


        btnAddCart.setText(
                "장바구니에 담김"
        );


        Toast.makeText(
                ProductDetailActivity.this,
                selectedSize
                        + " / "
                        + selectedColor
                        + " / "
                        + selectedQuantity
                        + "개를 장바구니에 담았습니다.",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =========================
    // 3D 가상 피팅으로 이동
    // =========================

    private void goFitting() {

        Intent intent = new Intent(
                ProductDetailActivity.this,
                FittingActivity.class
        );


        // =========================
        // products 정보 전달
        // =========================

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


        // =========================
        // product_options 정보 전달
        // =========================

        intent.putExtra(
                "selected_size",
                selectedSize
        );

        intent.putExtra(
                "selected_color",
                selectedColor
        );


        startActivity(intent);
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

    /** 추천 서버 상품이면 아직 안 되는 기능이라고 알리고 true (옵션·사이즈 정보가 DB에 들어오면 연결) */
    private boolean showServerProductNotice() {

        if (currentProduct != null || serverProduct == null) {
            return false;
        }

        Toast.makeText(
                this,
                "추천 상품은 아직 옵션 선택·장바구니·피팅이 안 돼요 (찜은 돼요)",
                Toast.LENGTH_SHORT
        ).show();

        return true;
    }

    private void updateHeart() {

        Product product = currentProduct != null ? currentProduct : serverProduct;

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
