package com.example.capstone.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.data.Product;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartAdapter
        extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private final List<Product> cartList;
    private final Runnable onCartChanged;

    public CartAdapter(List<Product> cartList) {
        this(cartList, null);
    }

    public CartAdapter(
            List<Product> cartList,
            Runnable onCartChanged
    ) {
        this.cartList = cartList;
        this.onCartChanged = onCartChanged;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(
                        R.layout.item_cart,
                        parent,
                        false
                );

        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull CartViewHolder holder,
            int position
    ) {

        Product product = cartList.get(position);

        // 브랜드명
        holder.textBrand.setText(
                product.getBrand()
        );

        // 상품명
        holder.textProductName.setText(
                product.getName()
        );

        // 선택 옵션
        String size = product.getSelectedSize();
        String color = product.getSelectedColor();

        StringBuilder option = new StringBuilder();

        if (size != null && !size.isEmpty()) {
            option.append(size);
        }

        if (color != null && !color.isEmpty()) {

            if (option.length() > 0) {
                option.append(" / ");
            }

            option.append(color);
        }

        if (option.length() > 0) {

            holder.textOption.setVisibility(View.VISIBLE);
            holder.textOption.setText(option.toString());

        } else {

            holder.textOption.setVisibility(View.GONE);
        }

        // 상품 가격
        updateItemPrice(holder, product);

        // 수량 표시
        holder.textQuantity.setText(
                String.valueOf(product.getCartQuantity())
        );

        // 수량 증가
        holder.btnPlus.setOnClickListener(v -> {

            int quantity = product.getCartQuantity();

            product.setCartQuantity(quantity + 1);

            holder.textQuantity.setText(
                    String.valueOf(product.getCartQuantity())
            );

            updateItemPrice(holder, product);
            notifyCartChanged();
        });

        // 수량 감소
        holder.btnMinus.setOnClickListener(v -> {

            int quantity = product.getCartQuantity();

            if (quantity > 1) {

                product.setCartQuantity(quantity - 1);

                holder.textQuantity.setText(
                        String.valueOf(product.getCartQuantity())
                );

                updateItemPrice(holder, product);
                notifyCartChanged();
            }
        });

        // 상품 삭제
        holder.btnDelete.setOnClickListener(v -> {

            int currentPosition =
                    holder.getBindingAdapterPosition();

            if (currentPosition ==
                    RecyclerView.NO_POSITION) {
                return;
            }

            Product currentProduct =
                    cartList.get(currentPosition);

            currentProduct.setInCart(false);
            currentProduct.setCartQuantity(1);

            cartList.remove(currentPosition);

            notifyItemRemoved(currentPosition);
            notifyCartChanged();
        });
    }

    // 상품별 수량을 반영한 가격
    private void updateItemPrice(
            CartViewHolder holder,
            Product product
    ) {

        int quantity = product.getCartQuantity();

        int price = product.getPrice() * quantity;

        String formattedPrice = NumberFormat
                .getNumberInstance(Locale.KOREA)
                .format(price);

        holder.textPrice.setText(
                formattedPrice + "원"
        );
    }

    // 장바구니 화면 전체 금액 갱신
    private void notifyCartChanged() {

        if (onCartChanged != null) {
            onCartChanged.run();
        }
    }

    @Override
    public int getItemCount() {
        return cartList.size();
    }

    public static class CartViewHolder
            extends RecyclerView.ViewHolder {

        TextView textBrand;
        TextView textProductName;
        TextView textOption;
        TextView textPrice;

        TextView btnMinus;
        TextView textQuantity;
        TextView btnPlus;
        TextView btnDelete;

        public CartViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            textBrand = itemView.findViewById(
                    R.id.textCartBrand
            );

            textProductName = itemView.findViewById(
                    R.id.textCartProductName
            );

            textOption = itemView.findViewById(
                    R.id.textCartOption
            );

            textPrice = itemView.findViewById(
                    R.id.textCartPrice
            );

            btnMinus = itemView.findViewById(
                    R.id.btnMinus
            );

            textQuantity = itemView.findViewById(
                    R.id.textQuantity
            );

            btnPlus = itemView.findViewById(
                    R.id.btnPlus
            );

            btnDelete = itemView.findViewById(
                    R.id.btnDeleteCart
            );
        }
    }
}
