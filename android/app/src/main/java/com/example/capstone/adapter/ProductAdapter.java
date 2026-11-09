package com.example.capstone.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.LocalLikes;
import com.example.capstone.data.Product;
import com.example.capstone.data.ProductImages;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.login.LoginGate;
import com.example.capstone.ui.ProductDetailActivity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter
        extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final List<Product> productList;
    private final boolean isWishMode;
    private Runnable onWishRemovedListener;

    public ProductAdapter(List<Product> productList) {
        this(productList, false);
    }

    public ProductAdapter(
            List<Product> productList,
            boolean isWishMode
    ) {
        this.productList = productList;
        this.isWishMode = isWishMode;
    }

    public void setOnWishRemovedListener(Runnable listener) {
        onWishRemovedListener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_product,
                        parent,
                        false
                );

        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ProductViewHolder holder,
            int position
    ) {

        Product product = productList.get(position);

        // 브랜드
        holder.textBrand.setText(
                product.getBrand()
        );

        // 상품명
        holder.textProductName.setText(
                product.getName()
        );

        // 가격
        String price = NumberFormat
                .getNumberInstance(Locale.KOREA)
                .format(product.getPrice());

        holder.textPrice.setText(price + "원");

        // 추천 서버 상품은 아직 가격이 없어서 가격 대신 추천 이유를 보여 준다
        boolean fromServer = product.isFromServer();

        holder.textPrice.setVisibility(
                fromServer && product.getPrice() == 0
                        ? View.GONE
                        : View.VISIBLE
        );

        holder.textReason.setText(product.getReason());

        holder.textReason.setVisibility(
                product.getReason().isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        // NEW 배지
        // 임시 기준: 상품 ID가 5 이상이면 NEW (추천 서버 상품은 표시 안 함)
        boolean isNew = DummyData.isNew(product);

        holder.textNewBadge.setVisibility(
                isNew ? View.VISIBLE : View.GONE
        );

        ProductImages.bind(holder.frameImage, holder.textImageLabel, holder.imgProduct, product);

        // 찜 상태
        updateHeart(holder, product);

        // 찜 버튼 클릭
        holder.textHeart.setOnClickListener(v -> {

            if (!LoginSession.isLoggedIn(v.getContext())) {
                v.getContext().startActivity(
                        new Intent(v.getContext(), LoginMainActivity.class)
                );
                return;
            }

            int currentPosition =
                    holder.getBindingAdapterPosition();

            if (currentPosition ==
                    RecyclerView.NO_POSITION) {
                return;
            }

            Product currentProduct =
                    productList.get(currentPosition);

            // 찜은 로그인해야 할 수 있다 (비회원이면 로그인 안내 → 로그인 뒤 이어서 찜)
            LoginGate.require(v.getContext(), () -> toggleLike(v.getContext(), currentProduct));
        });

        // 상품 클릭 → 상세페이지 이동
        holder.itemView.setOnClickListener(v -> {

            int currentPosition =
                    holder.getBindingAdapterPosition();

            if (currentPosition ==
                    RecyclerView.NO_POSITION) {
                return;
            }

            Product selectedProduct =
                    productList.get(currentPosition);

            // 상품을 눌러 본 기록도 추천에 반영
            RecommendApi.sendEvent(
                    v.getContext(),
                    selectedProduct,
                    "click"
            );

            Intent intent = new Intent(
                    v.getContext(),
                    ProductDetailActivity.class
            );

            intent.putExtra(
                    "product_id",
                    selectedProduct.getId()
            );

            intent.putExtra(
                    "product_brand",
                    selectedProduct.getBrand()
            );

            intent.putExtra(
                    "product_name",
                    selectedProduct.getName()
            );

            intent.putExtra(
                    "product_price",
                    selectedProduct.getPrice()
            );

            // 서버 상품이면 상세 화면에서 찜할 수 있게 서버 id도 넘긴다
            intent.putExtra(
                    "server_product_id",
                    selectedProduct.getServerId()
            );

            v.getContext().startActivity(intent);
        });
    }

    private void updateHeart(
            ProductViewHolder holder,
            Product product
    ) {

        if (product.isLiked()) {
            holder.textHeart.setImageResource(R.drawable.ic_heart_filled);
        } else {
            holder.textHeart.setImageResource(R.drawable.ic_heart_outline);
        }
    }

    /** 찜을 켜고 끈다 (로그인한 뒤에만 부른다). 로그인하고 돌아온 뒤에도 쓰므로 목록에서 위치를 다시 찾는다 */
    private void toggleLike(Context context, Product product) {

        int position = productList.indexOf(product);

        product.setLiked(
                !product.isLiked()
        );

        // 내장 상품 찜은 계정별로 저장 (혜림, LocalLikes)
        LocalLikes.save(
                context,
                product
        );

        // 서버 상품이면 찜 상태를 저장하고, 찜할 때 추천 서버가 취향을 배운다
        RecommendApi.saveLike(
                context,
                product
        );

        if (position == RecyclerView.NO_POSITION || position < 0) {
            notifyDataSetChanged();
            return;
        }

        if (isWishMode && !product.isLiked()) {

            productList.remove(position);

            notifyItemRemoved(position);

            notifyItemRangeChanged(
                    position,
                    productList.size() - position
            );

            if (onWishRemovedListener != null) {
                onWishRemovedListener.run();
            }

        } else {

            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder
            extends RecyclerView.ViewHolder {

        TextView textBrand;
        TextView textProductName;
        TextView textPrice;
        ImageView textHeart;
        TextView textNewBadge;
        TextView textReason;
        View frameImage;
        TextView textImageLabel;
        ImageView imgProduct;

        public ProductViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            textBrand = itemView.findViewById(
                    R.id.textBrand
            );

            textProductName = itemView.findViewById(
                    R.id.textProductName
            );

            textPrice = itemView.findViewById(
                    R.id.textPrice
            );

            textHeart = itemView.findViewById(
                    R.id.textHeart
            );

            textNewBadge = itemView.findViewById(
                    R.id.textNewBadge
            );

            textReason = itemView.findViewById(
                    R.id.textReason
            );

            frameImage = itemView.findViewById(R.id.frameProductImage);
            textImageLabel = itemView.findViewById(R.id.textImageLabel);
            imgProduct = itemView.findViewById(R.id.imgProduct);
        }
    }
}
