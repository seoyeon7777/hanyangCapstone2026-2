package com.example.capstone.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;
import com.example.capstone.data.ShopStore;
import com.example.capstone.ui.ProductOptionSheet;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartAdapter
        extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private final List<ShopStore.CartItem> cartList;
    private final Runnable onCartChanged;

    public CartAdapter(List<ShopStore.CartItem> cartList) {
        this(cartList, null);
    }

    public CartAdapter(
            List<ShopStore.CartItem> cartList,
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
        ShopStore.CartItem item = cartList.get(position);

        holder.cbSelect.setChecked(item.selected);

        holder.cbSelect.setOnClickListener(v -> {
            ShopStore.setSelected(item, holder.cbSelect.isChecked());
            notifyCartChanged();
        });

        holder.btnChangeOption.setOnClickListener(v ->
                ProductOptionSheet.show(
                        v.getContext(),
                        item.product(),
                        item.size,
                        item.color,
                        item.quantity,
                        "옵션 변경",
                        (size, color, quantity) -> {
                            boolean merged = ShopStore.changeOption(item, size, color, quantity);
                            Toast.makeText(
                                    v.getContext(),
                                    merged
                                            ? "같은 옵션이 이미 담겨 있어 수량을 합쳤습니다."
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
                item.brand
        );

        // 상품명
        holder.textProductName.setText(
                item.name
        );

        // 선택 옵션
        holder.textOption.setVisibility(View.VISIBLE);
        holder.textOption.setText(item.optionText());

        // 상품 가격
        updateItemPrice(holder, item);

        // 수량 표시
        holder.textQuantity.setText(
                String.valueOf(item.quantity)
        );

        // 수량 증가
        holder.btnPlus.setOnClickListener(v -> {
            if (item.quantity >= ShopStore.MAX_QUANTITY) {
                Toast.makeText(v.getContext(),
                        "한 번에 " + ShopStore.MAX_QUANTITY + "개까지 담을 수 있어요.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            ShopStore.setQuantity(item, item.quantity + 1);

            holder.textQuantity.setText(
                    String.valueOf(item.quantity)
            );

            updateItemPrice(holder, item);
            notifyCartChanged();
        });

        // 수량 감소
        holder.btnMinus.setOnClickListener(v -> {
            if (item.quantity > 1) {
                ShopStore.setQuantity(item, item.quantity - 1);

                holder.textQuantity.setText(
                        String.valueOf(item.quantity)
                );

                updateItemPrice(holder, item);
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

            ShopStore.remove(cartList.get(currentPosition));
            cartList.remove(currentPosition);

            notifyItemRemoved(currentPosition);
            notifyCartChanged();
        });
    }

    // 상품별 수량을 반영한 가격
    private void updateItemPrice(
            CartViewHolder holder,
            ShopStore.CartItem item
    ) {
        String formattedPrice = NumberFormat
                .getNumberInstance(Locale.KOREA)
                .format(item.totalPrice());

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

        public CartViewHolder(
                @NonNull View itemView
        ) {
            super(itemView);

            cbSelect = itemView.findViewById(
                    R.id.cbCartSelect
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
