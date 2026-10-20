package com.example.capstone.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.data.Product;
import com.example.capstone.ui.ProductDetailActivity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductAdapter
        extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private final List<Product> productList;
    private final boolean isWishMode;

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

        // NEW 배지
        // 임시 기준: 상품 ID가 5 이상이면 NEW
        boolean isNew = product.getId() >= 5;

        holder.textNewBadge.setVisibility(
                isNew ? View.VISIBLE : View.GONE
        );

        // 찜 상태
        updateHeart(holder, product);

        // 찜 버튼 클릭
        holder.textHeart.setOnClickListener(v -> {

            int currentPosition =
                    holder.getBindingAdapterPosition();

            if (currentPosition ==
                    RecyclerView.NO_POSITION) {
                return;
            }

            Product currentProduct =
                    productList.get(currentPosition);

            currentProduct.setLiked(
                    !currentProduct.isLiked()
            );

            if (isWishMode && !currentProduct.isLiked()) {

                productList.remove(currentPosition);

                notifyItemRemoved(currentPosition);

                notifyItemRangeChanged(
                        currentPosition,
                        productList.size() - currentPosition
                );

            } else {

                notifyItemChanged(currentPosition);
            }
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
        }
    }
}
