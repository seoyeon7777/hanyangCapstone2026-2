package com.example.capstone.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.Product;
import com.example.capstone.ui.ProductDetailActivity;
import com.example.capstone.ui.ProductOptionSheet;

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

        holder.cbSelect.setChecked(product.isCartSelected());

        holder.cbSelect.setOnClickListener(v -> {

            // 장바구니 값은 CartStore로 바꾼다 (그래야 앱을 꺼도 남는다)
            CartStore.setSelected(v.getContext(), product, holder.cbSelect.isChecked());
            notifyCartChanged();
        });

        holder.layoutProduct.setOnClickListener(v -> {

            Intent intent = new Intent(v.getContext(), ProductDetailActivity.class);
            intent.putExtra("product_id", product.getId());
            intent.putExtra("product_brand", product.getBrand());
            intent.putExtra("product_name", product.getName());
            intent.putExtra("product_price", product.getPrice());
            intent.putExtra("server_product_id", product.getServerId());
            intent.putExtra("product_category", product.getCategory());
            intent.putExtra("product_image_url", product.getImageUrl());
            v.getContext().startActivity(intent);
        });

        holder.btnChangeOption.setOnClickListener(v ->
                ProductOptionSheet.show(
                        v.getContext(),
                        product,
                        product.getSelectedSize(),
                        product.getSelectedColor(),
                        product.getCartQuantity(),
                        "옵션 변경",
                        (size, color, quantity) -> {

                            // 바꾼 옵션이 이미 다른 줄에 있으면 CartStore가 그 줄로 합친다
                            boolean sameAsNow = size.equals(product.getSelectedSize())
                                    && color.equals(product.getSelectedColor());
                            boolean merged = !sameAsNow
                                    && CartStore.containsOption(v.getContext(), product, size, color);

                            if (merged
                                    && CartStore.quantityOf(v.getContext(), product, size, color) + quantity
                                    > CartStore.MAX_QUANTITY) {

                                Toast.makeText(
                                        v.getContext(),
                                        CartStore.MAX_QUANTITY_MESSAGE,
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }

                            CartStore.changeOption(v.getContext(), product, size, color, quantity);

                            Toast.makeText(
                                    v.getContext(),
                                    merged
                                            ? "같은 옵션(" + size + " / " + color + ")이 이미 있어서 수량을 합쳤습니다."
                                            : "옵션을 " + size + " / " + color
                                            + " / " + quantity + "개로 변경했습니다.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            notifyCartChanged();
                        }
                )
        );

        // 브랜드명
        holder.textBrand.setText(
                product.getBrand()
        );

        // 상품명
        holder.textProductName.setText(
                product.getName()
        );

        // 선택 옵션
        String option = product.getOptionLabel();

        if (!option.isEmpty()) {

            holder.textOption.setVisibility(View.VISIBLE);
            holder.textOption.setText(option);

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

            if (quantity >= CartStore.MAX_QUANTITY) {

                Toast.makeText(
                        v.getContext(),
                        CartStore.MAX_QUANTITY_MESSAGE,
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            CartStore.setQuantity(v.getContext(), product, quantity + 1);

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

                CartStore.setQuantity(v.getContext(), product, quantity - 1);

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

            CartStore.remove(v.getContext(), currentProduct);

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
        TextView btnChangeOption;
        CheckBox cbSelect;
        View layoutProduct;

        public CartViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);

            cbSelect = itemView.findViewById(
                    R.id.cbCartSelect
            );

            layoutProduct = itemView.findViewById(
                    R.id.layoutCartProduct
            );

            btnChangeOption = itemView.findViewById(
                    R.id.btnChangeOption
            );

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
