package com.example.capstone.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.capstone.R;

import java.util.LinkedHashSet;
import java.util.Set;

public class CategoryFilterDialog {

    // 기존 CategoryActivity용
    public interface OnApplyListener {
        void onApply(
                int minPrice,
                int maxPrice,
                String color,
                String styles
        );
    }

    // MainActivity용
    public interface OnApplyAllListener {
        void onApply(
                int minPrice,
                int maxPrice,
                String color,
                String styles,
                String height,
                String weight,
                String bodyType
        );
    }

    private static final int BLACK = 0xFF111111;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF888888;
    private static final int BORDER = 0xFFE7E7E7;

    private static final String[] COLOR_NAMES = {
            "화이트", "블랙", "그레이", "베이지",
            "브라운", "블루", "핑크", "그린",
            "레드", "옐로우", "퍼플", "네이비"
    };

    private static final int[] COLOR_VALUES = {
            0xFFFFFFFF, 0xFF222222,
            0xFFA5A5A5, 0xFFDDCDB2,
            0xFF8B593B, 0xFF4C7DD1,
            0xFFF09ABA, 0xFF57976A,
            0xFFDF454B, 0xFFF5CC45,
            0xFFA36BC3, 0xFF283E69
    };

    private static final String[] STYLE_NAMES = {
            "캐주얼", "미니멀", "스트릿",
            "클래식", "페미닌", "러블리",
            "스포티"
    };

    private static final String[] BODY_TYPES = {
            "스트레이트", "웨이브", "내추럴"
    };

    private final Context context;
    private final Dialog dialog;

    private final OnApplyListener oldListener;
    private final OnApplyAllListener allListener;

    private String selectedColor;
    private String selectedBodyType;

    private final Set<String> selectedStyles =
            new LinkedHashSet<>();

    private EditText minInput;
    private EditText maxInput;
    private EditText heightInput;
    private EditText weightInput;

    private LinearLayout colorContainer;
    private LinearLayout styleContainer;
    private LinearLayout bodyTypeContainer;

    // CategoryActivity 기존 생성자 유지
    public CategoryFilterDialog(
            Context context,
            int minPrice,
            int maxPrice,
            String color,
            String styles,
            OnApplyListener listener
    ) {
        this(
                context,
                minPrice,
                maxPrice,
                color,
                styles,
                "",
                "",
                "",
                listener,
                null
        );
    }

    // MainActivity 새 생성자 유지
    public CategoryFilterDialog(
            Context context,
            int minPrice,
            int maxPrice,
            String color,
            String styles,
            String height,
            String weight,
            String bodyType,
            OnApplyAllListener listener
    ) {
        this(
                context,
                minPrice,
                maxPrice,
                color,
                styles,
                height,
                weight,
                bodyType,
                null,
                listener
        );
    }

    private CategoryFilterDialog(
            Context context,
            int minPrice,
            int maxPrice,
            String color,
            String styles,
            String height,
            String weight,
            String bodyType,
            OnApplyListener oldListener,
            OnApplyAllListener allListener
    ) {
        this.context = context;
        this.oldListener = oldListener;
        this.allListener = allListener;

        selectedColor = safe(color);
        selectedBodyType = safe(bodyType);

        if (!safe(styles).isEmpty()) {
            for (String style : styles.split(",")) {
                String value = style.trim();

                if (!value.isEmpty()) {
                    selectedStyles.add(value);
                }
            }
        }

        dialog = new Dialog(context);
        dialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        // Java에서 레이아웃을 직접 만들지 않고
        // XML 파일을 불러옴
        dialog.setContentView(
                R.layout.dialog_category_filter
        );

        bindViews();
        setupInputs(
                minPrice,
                maxPrice,
                safe(height),
                safe(weight)
        );

        renderColors();
        renderStyles();
        renderBodyTypes();

        setupButtons();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    // =====================================
    // XML 요소 연결
    // =====================================

    private void bindViews() {

        minInput = dialog.findViewById(
                R.id.editFilterMinPrice
        );

        maxInput = dialog.findViewById(
                R.id.editFilterMaxPrice
        );

        heightInput = dialog.findViewById(
                R.id.editFilterHeight
        );

        weightInput = dialog.findViewById(
                R.id.editFilterWeight
        );

        colorContainer = dialog.findViewById(
                R.id.filterColorContainer
        );

        styleContainer = dialog.findViewById(
                R.id.filterStyleContainer
        );

        bodyTypeContainer = dialog.findViewById(
                R.id.filterBodyTypeContainer
        );
    }

    private void setupInputs(
            int minPrice,
            int maxPrice,
            String height,
            String weight
    ) {
        if (minPrice > 0) {
            minInput.setText(
                    String.valueOf(minPrice)
            );
        }

        if (maxPrice > 0) {
            maxInput.setText(
                    String.valueOf(maxPrice)
            );
        }

        heightInput.setText(height);
        weightInput.setText(weight);
    }

    // =====================================
    // 버튼 연결
    // =====================================

    private void setupButtons() {

        dialog.findViewById(
                R.id.btnFilterClose
        ).setOnClickListener(
                v -> dialog.dismiss()
        );

        dialog.findViewById(
                R.id.btnFilterReset
        ).setOnClickListener(v -> {

            minInput.setText("");
            maxInput.setText("");
            heightInput.setText("");
            weightInput.setText("");

            selectedColor = "";
            selectedBodyType = "";
            selectedStyles.clear();

            renderColors();
            renderStyles();
            renderBodyTypes();
        });

        dialog.findViewById(
                R.id.btnFilterApply
        ).setOnClickListener(
                v -> applyFilters()
        );
    }

    // =====================================
    // 색상 선택 (4열)
    // =====================================

    private void renderColors() {
        colorContainer.removeAllViews();

        for (int start = 0;
             start < COLOR_NAMES.length;
             start += 4) {

            LinearLayout row =
                    new LinearLayout(context);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            for (int i = start;
                 i < Math.min(start + 4, COLOR_NAMES.length);
                 i++) {

                final String name = COLOR_NAMES[i];
                final int colorValue = COLOR_VALUES[i];

                boolean selected =
                        name.equals(selectedColor);

                LinearLayout item =
                        new LinearLayout(context);

                item.setOrientation(
                        LinearLayout.VERTICAL
                );

                item.setGravity(Gravity.CENTER);

                row.addView(
                        item,
                        new LinearLayout.LayoutParams(
                                0, dp(76), 1f
                        )
                );

                LinearLayout ring =
                        new LinearLayout(context);

                ring.setGravity(Gravity.CENTER);

                GradientDrawable ringBg =
                        new GradientDrawable();

                ringBg.setShape(
                        GradientDrawable.OVAL
                );

                ringBg.setColor(WHITE);

                if (selected) {
                    ringBg.setStroke(
                            dp(2), BLACK
                    );
                }

                ring.setBackground(ringBg);

                item.addView(
                        ring,
                        new LinearLayout.LayoutParams(
                                dp(40), dp(40)
                        )
                );

                View circle = new View(context);

                GradientDrawable circleBg =
                        new GradientDrawable();

                circleBg.setShape(
                        GradientDrawable.OVAL
                );

                circleBg.setColor(colorValue);

                if (colorValue == WHITE) {
                    circleBg.setStroke(
                            dp(1), BORDER
                    );
                }

                circle.setBackground(circleBg);

                ring.addView(
                        circle,
                        new LinearLayout.LayoutParams(
                                dp(30), dp(30)
                        )
                );

                TextView label = text(
                        name, 11, selected
                );

                label.setGravity(Gravity.CENTER);

                LinearLayout.LayoutParams labelParams =
                        new LinearLayout.LayoutParams(
                                -1, -2
                        );

                labelParams.topMargin = dp(5);

                item.addView(label, labelParams);

                item.setOnClickListener(v -> {
                    selectedColor =
                            name.equals(selectedColor)
                                    ? "" : name;

                    renderColors();
                });
            }

            colorContainer.addView(row);
        }
    }

    // =====================================
    // 스타일 다중 선택
    // =====================================

    private void renderStyles() {
        styleContainer.removeAllViews();

        for (int start = 0;
             start < STYLE_NAMES.length;
             start += 3) {

            LinearLayout row =
                    new LinearLayout(context);

            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            for (int i = start;
                 i < Math.min(start + 3, STYLE_NAMES.length);
                 i++) {

                final String style = STYLE_NAMES[i];

                boolean selected =
                        selectedStyles.contains(style);

                TextView option = option(
                        style,
                        selected
                );

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                0, dp(42), 1f
                        );

                params.setMargins(
                        dp(3), dp(4),
                        dp(3), dp(4)
                );

                row.addView(option, params);

                option.setOnClickListener(v -> {
                    if (selectedStyles.contains(style)) {
                        selectedStyles.remove(style);
                    } else {
                        selectedStyles.add(style);
                    }

                    renderStyles();
                });
            }

            int count = Math.min(
                    3,
                    STYLE_NAMES.length - start
            );

            for (int i = count; i < 3; i++) {
                View spacer = new View(context);

                row.addView(
                        spacer,
                        new LinearLayout.LayoutParams(
                                0, dp(42), 1f
                        )
                );
            }

            styleContainer.addView(row);
        }
    }

    // =====================================
    // 체형 선택
    // =====================================

    private void renderBodyTypes() {
        bodyTypeContainer.removeAllViews();

        for (String bodyType : BODY_TYPES) {

            boolean selected =
                    bodyType.equals(selectedBodyType);

            TextView option = option(
                    bodyType,
                    selected
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            0, dp(46), 1f
                    );

            params.setMargins(
                    dp(3), 0,
                    dp(3), 0
            );

            bodyTypeContainer.addView(
                    option,
                    params
            );

            option.setOnClickListener(v -> {
                selectedBodyType =
                        bodyType.equals(selectedBodyType)
                                ? "" : bodyType;

                renderBodyTypes();
            });
        }
    }

    // =====================================
    // 필터 적용
    // =====================================

    private void applyFilters() {
        try {
            String minText =
                    minInput.getText()
                            .toString().trim();

            String maxText =
                    maxInput.getText()
                            .toString().trim();

            int min = minText.isEmpty()
                    ? 0
                    : Integer.parseInt(minText);

            int max = maxText.isEmpty()
                    ? 0
                    : Integer.parseInt(maxText);

            if (min < 0 || max < 0
                    || (max > 0 && min > max)) {
                toast("가격 범위를 확인해주세요.");
                return;
            }

            String height =
                    heightInput.getText()
                            .toString().trim();

            String weight =
                    weightInput.getText()
                            .toString().trim();

            if (!height.isEmpty()) {
                float value =
                        Float.parseFloat(height);

                if (value < 100 || value > 230) {
                    toast("키는 100~230cm로 입력해주세요.");
                    return;
                }
            }

            if (!weight.isEmpty()) {
                float value =
                        Float.parseFloat(weight);

                if (value <= 0 || value > 400) {
                    toast("몸무게를 확인해주세요.");
                    return;
                }
            }

            String styles = TextUtils.join(
                    ",",
                    selectedStyles
            );

            if (allListener != null) {
                allListener.onApply(
                        min,
                        max,
                        selectedColor,
                        styles,
                        height,
                        weight,
                        selectedBodyType
                );
            } else if (oldListener != null) {
                oldListener.onApply(
                        min,
                        max,
                        selectedColor,
                        styles
                );
            }

            dialog.dismiss();

        } catch (NumberFormatException e) {
            toast("숫자를 올바르게 입력해주세요.");
        }
    }

    // =====================================
    // 디자인 공통 함수
    // =====================================

    private TextView text(
            String value,
            int size,
            boolean bold
    ) {
        TextView view = new TextView(context);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(BLACK);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private TextView option(
            String label,
            boolean selected
    ) {
        TextView view = text(
                label, 12, selected
        );

        view.setGravity(Gravity.CENTER);

        view.setTextColor(
                selected ? WHITE : BLACK
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setColor(
                selected ? BLACK : WHITE
        );

        bg.setCornerRadius(dp(9));

        bg.setStroke(
                dp(1),
                selected ? BLACK : BORDER
        );

        view.setBackground(bg);

        return view;
    }

    private void toast(String message) {
        Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(float value) {
        return Math.round(
                value * context.getResources()
                        .getDisplayMetrics().density
        );
    }

    // =====================================
    // 팝업 표시
    // =====================================

    public void show() {
        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );

            WindowManager.LayoutParams params =
                    window.getAttributes();

            int screenWidth =
                    context.getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            int screenHeight =
                    context.getResources()
                            .getDisplayMetrics()
                            .heightPixels;

            params.width =
                    (int) (screenWidth * 0.92f);

            params.height =
                    (int) (screenHeight * 0.78f);

            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.55f;

            window.addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
            );

            window.setAttributes(params);
        }
    }
}
