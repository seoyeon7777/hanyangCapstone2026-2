package com.example.capstone.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.capstone.R;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.Product;
import com.example.capstone.data.SizeChart;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * =========================
 * 상품 옵션 선택 창
 * =========================
 *
 * 쇼핑몰에서 흔히 쓰는 아래에서 올라오는 옵션 창.
 * 사이즈 / 색상 / 수량을 고르고 확인 버튼을 누르면
 * 선택한 값을 돌려준다.
 */
public class ProductOptionSheet {

    // =========================
    // 선택 가능한 색상
    //
    // 상품에 색 목록(Product.getOptionColors)이 있으면 그것을 쓰고, 없으면 이 공통 목록을 쓴다.
    // 색 목록이 빈 상품은 색상 선택 없이 판다 (색상 값은 빈 문자열).
    // =========================

    public static final String[] COLORS = {
            "BLACK",
            "WHITE",
            "GRAY",
            "NAVY"
    };

    // 지금 떠 있는 옵션 창 (버튼을 연달아 눌러도 한 번만 뜨도록)
    private static BottomSheetDialog showingDialog;


    // =========================
    // 확인 버튼 콜백
    // =========================

    public interface OnConfirmListener {

        void onConfirm(
                String size,
                String color,
                int quantity
        );
    }


    // =========================
    // 옵션 창 띄우기
    //
    // initialSize / initialColor : 이미 고른 값 (없으면 null)
    // confirmText : 확인 버튼 문구 ("장바구니 담기" 등)
    // =========================

    public static void show(
            Context context,
            Product product,
            String initialSize,
            String initialColor,
            int initialQuantity,
            String confirmText,
            OnConfirmListener listener
    ) {

        show(context, product, initialSize, initialColor, initialQuantity,
                null, null, confirmText, listener);
    }

    public static void show(
            Context context,
            Product product,
            String initialSize,
            String initialColor,
            int initialQuantity,
            String secondaryText,
            OnConfirmListener secondaryListener,
            String confirmText,
            OnConfirmListener listener
    ) {

        // 이미 옵션 창이 떠 있으면 새로 띄우지 않는다
        if (showingDialog != null
                && showingDialog.isShowing()) {

            return;
        }


        BottomSheetDialog dialog =
                new BottomSheetDialog(context);

        showingDialog = dialog;

        dialog.setOnDismissListener(d -> {

            if (showingDialog == dialog) {
                showingDialog = null;
            }
        });

        View view =
                LayoutInflater
                        .from(context)
                        .inflate(
                                R.layout.bottom_sheet_product_option,
                                null,
                                false
                        );

        dialog.setContentView(view);


        TextView textProductName =
                view.findViewById(R.id.textOptionProductName);

        ChipGroup groupSize =
                view.findViewById(R.id.groupOptionSize);

        ChipGroup groupColor =
                view.findViewById(R.id.groupOptionColor);

        TextView textSelected =
                view.findViewById(R.id.textOptionSelected);

        TextView btnMinus =
                view.findViewById(R.id.btnQtyMinus);

        TextView textQty =
                view.findViewById(R.id.textQty);

        TextView btnPlus =
                view.findViewById(R.id.btnQtyPlus);

        TextView textTotal =
                view.findViewById(R.id.textOptionTotal);

        TextView btnConfirm =
                view.findViewById(R.id.btnOptionConfirm);

        TextView btnSecondary =
                view.findViewById(R.id.btnOptionSecondary);


        int price =
                product != null
                        ? product.getPrice()
                        : 0;

        textProductName.setText(
                product != null
                        ? product.getName()
                        : ""
        );

        btnConfirm.setText(confirmText);


        String[] colors = colorsFor(product);
        boolean needsColor = colors.length > 0;

        // 선택 상태 (람다 안에서 바꾸기 위해 배열 사용)
        String[] selectedSize = {initialSize};
        String[] selectedColor = {startColor(colors, initialColor)};
        int[] quantity = {Math.max(1, initialQuantity)};


        // =========================
        // 화면 갱신
        // =========================

        Runnable refresh = () -> {

            boolean hasSize =
                    selectedSize[0] != null
                            && !selectedSize[0].isEmpty();

            boolean hasColor =
                    selectedColor[0] != null
                            && !selectedColor[0].isEmpty();


            if (hasSize && (hasColor || !needsColor)) {

                textSelected.setText(
                        Product.optionText(selectedSize[0], selectedColor[0])
                );

                textSelected.setTextColor(0xFF111111);

            } else {

                textSelected.setText(
                        !hasSize
                                ? "사이즈를 선택해 주세요"
                                : "색상을 선택해 주세요"
                );

                textSelected.setTextColor(0xFF888888);
            }


            textQty.setText(
                    String.valueOf(quantity[0])
            );

            textTotal.setText(
                    NumberFormat
                            .getNumberInstance(Locale.KOREA)
                            .format((long) price * quantity[0])
                            + "원"
            );
        };


        // =========================
        // 사이즈 / 색상 칩 만들기
        // =========================

        addChips(
                context,
                groupSize,
                SizeChart.SIZES,
                selectedSize,
                refresh
        );

        addChips(
                context,
                groupColor,
                colors,
                selectedColor,
                refresh
        );

        if (!needsColor) {
            view.findViewById(R.id.textOptionColorTitle).setVisibility(View.GONE);
            groupColor.setVisibility(View.GONE);
        }


        // =========================
        // 수량
        // =========================

        btnMinus.setOnClickListener(v -> {

            if (quantity[0] > 1) {

                quantity[0]--;
                refresh.run();
            }
        });

        btnPlus.setOnClickListener(v -> {

            if (quantity[0] < CartStore.MAX_QUANTITY) {

                quantity[0]++;
                refresh.run();

            } else {

                Toast.makeText(
                        context,
                        CartStore.MAX_QUANTITY_MESSAGE,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });


        refresh.run();


        // =========================
        // 확인
        // =========================

        bindConfirm(btnConfirm, context, dialog, selectedSize, selectedColor, needsColor, quantity, listener);

        if (secondaryText != null) {

            btnSecondary.setText(secondaryText);
            btnSecondary.setVisibility(View.VISIBLE);

            bindConfirm(btnSecondary, context, dialog, selectedSize, selectedColor, needsColor, quantity,
                    secondaryListener);
        }


        // 처음부터 전체 내용이 보이도록 펼친 상태로 연다
        dialog.getBehavior().setState(
                BottomSheetBehavior.STATE_EXPANDED
        );

        dialog.getBehavior().setSkipCollapsed(true);

        dialog.show();
    }


    static String[] colorsFor(Product product) {
        java.util.List<String> own = product == null ? null : product.getOptionColors();
        return own == null ? COLORS : own.toArray(new String[0]);
    }

    /**
     * 창을 열 때 골라 둘 색. 색이 하나뿐이면 미리 고른다.
     * 전에 고른 색이 지금 목록에 없으면(예전에 담은 장바구니) 다시 고르게 한다. 색상이 없는 상품은 빈 문자열
     */
    static String startColor(String[] colors, String initial) {
        if (colors.length == 0) {
            return "";
        }
        if (java.util.Arrays.asList(colors).contains(initial)) {
            return initial;
        }
        return colors.length == 1 ? colors[0] : null;
    }


    private static void bindConfirm(
            TextView button,
            Context context,
            BottomSheetDialog dialog,
            String[] selectedSize,
            String[] selectedColor,
            boolean needsColor,
            int[] quantity,
            OnConfirmListener listener
    ) {

        button.setOnClickListener(v -> {

            if (selectedSize[0] == null
                    || selectedSize[0].isEmpty()) {

                Toast.makeText(
                        context,
                        "사이즈를 선택해 주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (needsColor && (selectedColor[0] == null
                    || selectedColor[0].isEmpty())) {

                Toast.makeText(
                        context,
                        "색상을 선택해 주세요.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }


            dialog.dismiss();

            if (listener != null) {

                listener.onConfirm(
                        selectedSize[0],
                        selectedColor[0],
                        quantity[0]
                );
            }
        });
    }


    // =========================
    // 칩 목록 만들기
    //
    // 하나만 선택되도록 직접 관리한다.
    // =========================

    private static void addChips(
            Context context,
            ChipGroup group,
            String[] values,
            String[] selected,
            Runnable onChanged
    ) {

        float density =
                context.getResources()
                        .getDisplayMetrics()
                        .density;


        for (String value : values) {

            TextView chip = new TextView(context);

            chip.setText(value);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(14);
            chip.setMinWidth(Math.round(64 * density));

            chip.setPadding(
                    Math.round(16 * density),
                    0,
                    Math.round(16 * density),
                    0
            );

            chip.setBackgroundResource(
                    R.drawable.bg_option_chip_filled
            );

            chip.setTextColor(
                    ContextCompat.getColorStateList(
                            context,
                            R.color.option_chip_text_filled
                    )
            );

            chip.setSelected(
                    value.equals(selected[0])
            );


            chip.setOnClickListener(v -> {

                for (int i = 0; i < group.getChildCount(); i++) {

                    group.getChildAt(i).setSelected(false);
                }

                v.setSelected(true);

                selected[0] = value;

                onChanged.run();
            });


            group.addView(
                    chip,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Math.round(44 * density)
                    )
            );
        }
    }
}
