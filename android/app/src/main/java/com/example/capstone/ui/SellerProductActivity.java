package com.example.capstone.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.data.SellerApi;
import com.example.capstone.data.SellerFormRules;
import com.example.capstone.data.SellerStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매자 상품 등록 화면. 입력 항목은 서버(GET /api/seller/form)가 알려 주는 대로 그린다:
 * 종류에 따라 속성 양식(핏·기장·혼용률 …)과 치수 칸이 바뀌고, 선택지가 서버 규칙과 항상 같다.
 * 입력 검증과 태그 만들기는 서버(B 파트)가 하고, 여기서는 오류 문장을 그대로 보여 준다.
 */
public class SellerProductActivity extends AppCompatActivity {

    private static final int MAX_IMAGE_SIDE = 1600;
    private static final String NONE = "선택 안 함";
    private static final String PICK = "선택해 주세요";
    private static final String COMPOSITION_INPUT = "직접 입력";

    private LinearLayout page;
    private TextView status;
    private JSONObject form;

    private Spinner spinnerCategory;
    private EditText etBrand;
    private EditText etName;
    private EditText etPrice;
    private EditText etDescription;
    private final Map<String, CheckBox> seasonChecks = new LinkedHashMap<>();
    private final Map<String, CheckBox> sizeChecks = new LinkedHashMap<>();
    private final Map<String, CheckBox> colorChecks = new LinkedHashMap<>();
    private EditText etCustomColor;
    private LinearLayout colorBox;
    private LinearLayout measureBox;
    private LinearLayout attrBox;
    private ImageView preview;
    private Button btnRegister;

    // 속성: 단일 선택은 Spinner, 복수 선택은 CheckBox 목록
    private final Map<String, Spinner> attrSpinners = new LinkedHashMap<>();
    private final Map<String, List<String>> attrBaseOptions = new HashMap<>();   // 칸 키 → 선택지 (자리표시 문구 제외)
    private final Map<String, String> attrPlaceholder = new HashMap<>();
    // 서버 양식의 option_conflicts: 칸 키 → {선택지 → {다른 칸 키 → 함께 못 고르는 값들}} (예: 롱 카디건 ↔ 기장 기본·크롭)
    private final Map<String, JSONObject> conflictRules = new LinkedHashMap<>();
    private final Map<String, List<CheckBox>> attrChecks = new LinkedHashMap<>();
    private final List<Spinner> fiberSpinners = new ArrayList<>();
    private final List<EditText> fiberPercents = new ArrayList<>();
    private Spinner compositionMode;
    private LinearLayout fiberBox;
    private Button btnAddFiber;
    private final List<LinearLayout> fiberRows = new ArrayList<>();
    private List<String> fiberOptions = new ArrayList<>();
    private int maxFibers = 1;

    // 사이즈별 치수 입력칸 (사이즈 → 항목 → 칸). 다시 그려도 값이 남도록 문자열을 따로 보관한다
    private final Map<String, Map<String, EditText>> measureInputs = new HashMap<>();
    private final Map<String, String> measureCache = new HashMap<>();
    private String measureCategory = "";       // 지금 그려진 치수칸의 종류

    private byte[] imageBytes;
    private String imageUrl;
    private boolean busy;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        page = SellerUi.page(this, scroll);
        setContentView(scroll);

        page.addView(SellerUi.title(this, "상품 등록"));
        status = SellerUi.text(this, "", 13, SellerUi.GRAY, false);
        page.addView(status, SellerUi.matchWidth(this, 10));
        loadForm();
    }

    private void loadForm() {
        status.setText("양식을 불러오는 중이에요…");
        status.setTextColor(SellerUi.GRAY);
        SellerApi.loadForm(new SellerApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                form = result;
                status.setText("");
                try {
                    buildForm();
                } catch (JSONException e) {
                    status.setText("서버 양식을 읽지 못했어요. 서버와 앱 버전이 맞는지 확인해 주세요.");
                    status.setTextColor(SellerUi.RED);
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                status.setText(message + "\n화면을 다시 열어 주세요.");
                status.setTextColor(SellerUi.RED);
            }
        });
    }

    // ───────────── 화면 만들기 ─────────────

    private void buildForm() throws JSONException {
        page.addView(SellerUi.section(this, "기본 정보"));

        page.addView(SellerUi.label(this, "브랜드 이름 * (" + SellerStore.BRAND_MIN + "~" + SellerStore.BRAND_MAX + "자)"));
        etBrand = SellerUi.edit(this, "예: 모노데이", InputType.TYPE_CLASS_TEXT);
        etBrand.setText(SellerStore.getBrand(this));
        page.addView(etBrand, SellerUi.matchWidth(this, 0));

        page.addView(SellerUi.label(this, "상품 종류 *"));
        JSONArray categories = form.getJSONArray("categories");
        List<String> categoryLabels = new ArrayList<>();
        for (int i = 0; i < categories.length(); i++) {
            categoryLabels.add(categories.getJSONObject(i).getString("label"));
        }
        spinnerCategory = spinner(categoryLabels);
        page.addView(spinnerCategory, SellerUi.matchWidth(this, 0));

        page.addView(SellerUi.label(this, "상품 이름 *"));
        etName = SellerUi.edit(this, "예: 슬림 코튼 셔츠", InputType.TYPE_CLASS_TEXT);
        page.addView(etName, SellerUi.matchWidth(this, 0));

        page.addView(SellerUi.label(this, "가격 (원) *"));
        etPrice = SellerUi.edit(this, "예: 39000", InputType.TYPE_CLASS_NUMBER);
        page.addView(etPrice, SellerUi.matchWidth(this, 0));

        page.addView(SellerUi.label(this, "상품 설명"));
        etDescription = SellerUi.edit(this, "소재감, 착용감 등을 적어 주세요",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etDescription.setMinLines(3);
        etDescription.setGravity(android.view.Gravity.TOP);
        page.addView(etDescription, SellerUi.matchWidth(this, 0));

        page.addView(SellerUi.section(this, "상품 사진"));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(SellerUi.box(this, 0xFFF3F3F3, SellerUi.LINE));
        page.addView(preview, new LinearLayout.LayoutParams(-1, SellerUi.dp(this, 220)));
        Button pick = SellerUi.outlineButton(this, "사진 고르기");
        page.addView(pick, SellerUi.matchWidth(this, 8));
        pick.setOnClickListener(v -> imagePicker.launch("image/*"));

        page.addView(SellerUi.section(this, "계절 *"));
        JSONArray seasons = form.getJSONArray("seasons");
        List<CheckBox> seasonBoxes = new ArrayList<>();
        for (int i = 0; i < seasons.length(); i++) {
            JSONObject season = seasons.getJSONObject(i);
            CheckBox box = SellerUi.check(this, season.getString("label"));
            seasonChecks.put(season.getString("key"), box);
            seasonBoxes.add(box);
        }
        addGrid(page, seasonBoxes, 4);

        page.addView(SellerUi.section(this, "사이즈 *"));
        List<CheckBox> sizeBoxes = new ArrayList<>();
        JSONArray sizes = form.getJSONArray("sizes");
        for (int i = 0; i < sizes.length(); i++) {
            CheckBox box = SellerUi.check(this, sizes.getString(i));
            box.setOnCheckedChangeListener((b, checked) -> rebuildMeasurements());
            sizeChecks.put(sizes.getString(i), box);
            sizeBoxes.add(box);
        }
        addGrid(page, sizeBoxes, 3);

        page.addView(SellerUi.section(this, "색상 *"));
        colorBox = new LinearLayout(this);
        colorBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(colorBox);
        List<CheckBox> colorBoxes = new ArrayList<>();
        JSONArray colors = form.getJSONArray("colors");
        for (int i = 0; i < colors.length(); i++) {
            CheckBox box = SellerUi.check(this, colors.getString(i));
            colorChecks.put(colors.getString(i), box);
            colorBoxes.add(box);
        }
        addGrid(colorBox, colorBoxes, 3);
        LinearLayout customRow = SellerUi.row(this);
        etCustomColor = SellerUi.edit(this, "다른 색상 직접 입력", InputType.TYPE_CLASS_TEXT);
        customRow.addView(etCustomColor, SellerUi.weighted(this, 8));
        Button addColor = SellerUi.outlineButton(this, "추가");
        customRow.addView(addColor, new LinearLayout.LayoutParams(-2, -2));
        page.addView(customRow, SellerUi.matchWidth(this, 6));
        addColor.setOnClickListener(v -> addCustomColor());

        page.addView(SellerUi.section(this, "사이즈별 치수 (cm) *"));
        page.addView(SellerUi.text(this, "옷을 평평하게 놓고 잰 단면 길이예요. 사이즈를 고르면 입력칸이 나와요.",
                12, SellerUi.GRAY, false));
        measureBox = new LinearLayout(this);
        measureBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(measureBox);

        page.addView(SellerUi.section(this, "상품 특징"));
        page.addView(SellerUi.text(this, "추천에 쓰이는 정보예요. 정확히 고를수록 맞는 사람에게 보여요.",
                12, SellerUi.GRAY, false));
        attrBox = new LinearLayout(this);
        attrBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(attrBox);

        btnRegister = SellerUi.button(this, "상품 등록하기");
        page.addView(btnRegister, SellerUi.matchWidth(this, 28));
        btnRegister.setOnClickListener(v -> submit());

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                rebuildAttributes();
                rebuildMeasurements();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        rebuildAttributes();
        rebuildMeasurements();
    }

    private Spinner spinner(List<String> items) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        return spinner;
    }

    private void addGrid(LinearLayout parent, List<CheckBox> boxes, int columns) {
        LinearLayout row = null;
        for (int i = 0; i < boxes.size(); i++) {
            if (i % columns == 0) {
                row = SellerUi.row(this);
                parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            row.addView(boxes.get(i), new LinearLayout.LayoutParams(0, -2, 1f));
        }
    }

    private String selectedCategory() {
        try {
            return form.getJSONArray("categories").getJSONObject(spinnerCategory.getSelectedItemPosition()).getString("key");
        } catch (JSONException e) {
            return "";
        }
    }

    private void addCustomColor() {
        String color = SellerFormRules.normalizeColor(etCustomColor.getText().toString());
        if (color == null) {
            etCustomColor.setError("색상 이름을 1~30자로 입력해 주세요");
            return;
        }
        if (!colorChecks.containsKey(color)) {
            CheckBox box = SellerUi.check(this, color);
            colorChecks.put(color, box);
            colorBox.addView(box);
        }
        colorChecks.get(color).setChecked(true);
        etCustomColor.setText("");
    }

    // ───────────── 종류에 따라 바뀌는 칸 ─────────────

    private void rebuildAttributes() {
        attrBox.removeAllViews();
        attrSpinners.clear();
        attrBaseOptions.clear();
        attrPlaceholder.clear();
        conflictRules.clear();
        attrChecks.clear();
        fiberSpinners.clear();
        fiberPercents.clear();
        fiberRows.clear();
        String category = selectedCategory();
        try {
            JSONArray fields = form.getJSONArray("attributes");
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (!contains(field.getJSONArray("applies"), category)) {
                    continue;
                }
                String title = field.getString("label") + (field.optBoolean("required") ? " *" : "");
                if ("composition".equals(field.optString("type"))) {
                    buildComposition(field, title);
                    continue;
                }
                attrBox.addView(SellerUi.label(this, title));
                List<String> options = optionsFor(field, category);
                if (field.optBoolean("multi")) {
                    List<CheckBox> boxes = new ArrayList<>();
                    for (String option : options) {
                        boxes.add(SellerUi.check(this, option));
                    }
                    addGrid(attrBox, boxes, 2);
                    attrChecks.put(field.getString("key"), boxes);
                } else {
                    List<String> items = new ArrayList<>();
                    items.add(field.optBoolean("required") ? PICK : NONE);
                    items.addAll(options);
                    Spinner spinner = spinner(items);
                    attrBox.addView(spinner, SellerUi.matchWidth(this, 0));
                    String key = field.getString("key");
                    attrSpinners.put(key, spinner);
                    attrBaseOptions.put(key, options);
                    attrPlaceholder.put(key, items.get(0));
                    JSONObject conflicts = field.optJSONObject("option_conflicts");
                    if (conflicts != null) {
                        conflictRules.put(key, conflicts);
                        attrBox.addView(SellerUi.text(this, "기장과 어울리지 않는 조합은 고를 수 없어요. 먼저 고른 쪽에 맞춰 다른 쪽 선택지가 줄어요. (예: 롱 카디건은 기장이 롱일 때만)",
                                12, SellerUi.GRAY, false), SellerUi.matchWidth(this, 2));
                    }
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            refreshAttributeOptions();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                        }
                    });
                }
            }
        } catch (JSONException e) {
            attrBox.addView(SellerUi.text(this, "속성 양식을 읽지 못했어요.", 13, SellerUi.RED, false));
        }
    }

    /** 칸에서 고른 값. 고르지 않았으면 빈 문자열 */
    private String selectedValue(String key) {
        Spinner spinner = attrSpinners.get(key);
        if (spinner == null || spinner.getSelectedItem() == null) {
            return "";
        }
        String value = String.valueOf(spinner.getSelectedItem());
        return PICK.equals(value) || NONE.equals(value) ? "" : value;
    }

    /** 이 칸의 option이, 지금 다른 칸에서 고른 값과 함께 쓸 수 없는 조합인지 (서버 option_conflicts와 같은 규칙) */
    private boolean conflicts(String key, String option) {
        for (Map.Entry<String, JSONObject> owner : conflictRules.entrySet()) {
            JSONObject byOption = owner.getValue();
            if (owner.getKey().equals(key)) {
                // 이 칸이 규칙을 가진 칸(아이템 종류): option이 이미 고른 다른 칸의 값과 모순이면 막는다
                JSONObject targets = byOption.optJSONObject(option);
                if (targets == null) {
                    continue;
                }
                for (Iterator<String> it = targets.keys(); it.hasNext(); ) {
                    String target = it.next();
                    String chosen = selectedValue(target);
                    if (!chosen.isEmpty() && contains(targets.optJSONArray(target), chosen)) {
                        return true;
                    }
                }
            } else {
                // 규칙을 가진 칸에서 이미 고른 값이 이 칸의 option을 막는가
                String chosen = selectedValue(owner.getKey());
                JSONObject targets = chosen.isEmpty() ? null : byOption.optJSONObject(chosen);
                if (targets != null && contains(targets.optJSONArray(key), option)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 고른 값에 따라 맞지 않는 선택지를 각 칸의 목록에서 뺀다. 고른 값 자체는 항상 목록에 남는다 */
    private void refreshAttributeOptions() {
        for (Map.Entry<String, Spinner> entry : attrSpinners.entrySet()) {
            String key = entry.getKey();
            Spinner spinner = entry.getValue();
            List<String> base = attrBaseOptions.get(key);
            if (base == null) {
                continue;
            }
            String current = selectedValue(key);
            List<String> items = new ArrayList<>();
            items.add(attrPlaceholder.get(key));
            for (String option : base) {
                if (option.equals(current) || !conflicts(key, option)) {
                    items.add(option);
                }
            }
            if (sameItems(spinner, items)) {
                continue;
            }
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
            spinner.setSelection(Math.max(0, items.indexOf(current)));
        }
    }

    private static boolean sameItems(Spinner spinner, List<String> items) {
        if (spinner.getAdapter() == null || spinner.getAdapter().getCount() != items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).equals(String.valueOf(spinner.getAdapter().getItem(i)))) {
                return false;
            }
        }
        return true;
    }

    private List<String> optionsFor(JSONObject field, String category) throws JSONException {
        JSONObject only = field.optJSONObject("option_applies");
        List<String> out = new ArrayList<>();
        JSONArray options = field.getJSONArray("options");
        for (int i = 0; i < options.length(); i++) {
            String option = options.getString(i);
            JSONArray allowed = only == null ? null : only.optJSONArray(option);
            if (allowed == null || contains(allowed, category)) {
                out.add(option);
            }
        }
        return out;
    }

    private void buildComposition(JSONObject field, String title) throws JSONException {
        attrBox.addView(SellerUi.label(this, title));
        List<String> modes = new ArrayList<>();
        modes.add(COMPOSITION_INPUT);
        JSONArray statuses = form.getJSONArray("composition_statuses");
        for (int i = 0; i < statuses.length(); i++) {
            modes.add(statuses.getString(i));
        }
        compositionMode = spinner(modes);
        attrBox.addView(compositionMode, SellerUi.matchWidth(this, 0));

        fiberOptions = new ArrayList<>();
        fiberOptions.add(NONE);
        JSONArray fiberArray = field.getJSONArray("fibers");
        for (int i = 0; i < fiberArray.length(); i++) {
            fiberOptions.add(fiberArray.getString(i));
        }
        maxFibers = Math.max(1, fiberArray.length());

        // 섬유는 필요한 만큼 "섬유 추가"로 늘린다 (처음에는 한 줄)
        fiberBox = new LinearLayout(this);
        fiberBox.setOrientation(LinearLayout.VERTICAL);
        attrBox.addView(fiberBox);
        btnAddFiber = SellerUi.outlineButton(this, "+ 섬유 추가");
        attrBox.addView(btnAddFiber, SellerUi.matchWidth(this, 6));
        btnAddFiber.setOnClickListener(v -> addFiberRow());
        addFiberRow();
        TextView note = SellerUi.text(this, "겉감 기준이고 합이 100%가 되어야 해요. 섬유 이름은 영문이에요.",
                12, SellerUi.GRAY, false);
        attrBox.addView(note, SellerUi.matchWidth(this, 4));

        // "직접 입력"이 아닌 방식(확인 불가 등)을 고르면 섬유 입력칸을 숨긴다
        compositionMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int visible = position == 0 ? View.VISIBLE : View.GONE;
                fiberBox.setVisibility(visible);
                btnAddFiber.setVisibility(visible);
                note.setVisibility(visible);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void addFiberRow() {
        if (fiberRows.size() >= maxFibers) {
            Toast.makeText(this, "섬유는 최대 " + maxFibers + "개까지 넣을 수 있어요", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout row = SellerUi.row(this);
        Spinner fiber = spinner(fiberOptions);
        row.addView(fiber, SellerUi.weighted(this, 8));
        EditText percent = SellerUi.edit(this, "%", InputType.TYPE_CLASS_NUMBER);
        row.addView(percent, new LinearLayout.LayoutParams(SellerUi.dp(this, 80), -2));
        TextView remove = SellerUi.text(this, "삭제", 13, SellerUi.RED, false);
        remove.setPadding(SellerUi.dp(this, 12), SellerUi.dp(this, 10), SellerUi.dp(this, 4), SellerUi.dp(this, 10));
        row.addView(remove, new LinearLayout.LayoutParams(-2, -2));
        remove.setOnClickListener(v -> removeFiberRow(row));
        fiberBox.addView(row, SellerUi.matchWidth(this, 4));
        fiberRows.add(row);
        fiberSpinners.add(fiber);
        fiberPercents.add(percent);
        updateFiberButtons();
    }

    private void removeFiberRow(LinearLayout row) {
        int index = fiberRows.indexOf(row);
        if (index < 0 || fiberRows.size() <= 1) {
            return;       // 최소 한 줄은 남긴다
        }
        fiberBox.removeView(row);
        fiberRows.remove(index);
        fiberSpinners.remove(index);
        fiberPercents.remove(index);
        updateFiberButtons();
    }

    private void updateFiberButtons() {
        btnAddFiber.setText(fiberRows.size() >= maxFibers ? "섬유는 최대 " + maxFibers + "개예요" : "+ 섬유 추가");
        SellerUi.setEnabled(btnAddFiber, fiberRows.size() < maxFibers);
        // 한 줄만 남으면 삭제를 숨긴다
        for (LinearLayout row : fiberRows) {
            row.getChildAt(2).setVisibility(fiberRows.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void rebuildMeasurements() {
        if (measureBox == null) {
            return;
        }
        // 지금 칸에 적힌 값을 보관해 두었다가 다시 그릴 때 채운다
        for (Map.Entry<String, Map<String, EditText>> bySize : measureInputs.entrySet()) {
            for (Map.Entry<String, EditText> field : bySize.getValue().entrySet()) {
                measureCache.put(measureCategory + "|" + bySize.getKey() + "|" + field.getKey(),
                        field.getValue().getText().toString());
            }
        }
        measureBox.removeAllViews();
        measureInputs.clear();
        String category = selectedCategory();
        measureCategory = category;
        List<String> selected = selectedSizes();
        try {
            JSONArray fields = form.getJSONObject("measurements").getJSONArray(category);
            for (String size : selected) {
                measureBox.addView(SellerUi.label(this, size + " 사이즈"));
                Map<String, EditText> inputs = new LinkedHashMap<>();
                LinearLayout row = null;
                for (int i = 0; i < fields.length(); i++) {
                    if (i % 2 == 0) {
                        row = SellerUi.row(this);
                        measureBox.addView(row, SellerUi.matchWidth(this, 4));
                    }
                    JSONObject field = fields.getJSONObject(i);
                    EditText input = SellerUi.edit(this, field.getString("label"),
                            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    String cached = measureCache.get(category + "|" + size + "|" + field.getString("key"));
                    if (cached != null) {
                        input.setText(cached);
                    }
                    row.addView(input, SellerUi.weighted(this, i % 2 == 0 ? 8 : 0));
                    inputs.put(field.getString("key"), input);
                }
                measureInputs.put(size, inputs);
            }
        } catch (JSONException e) {
            measureBox.addView(SellerUi.text(this, "치수 양식을 읽지 못했어요.", 13, SellerUi.RED, false));
        }
    }

    private List<String> selectedSizes() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, CheckBox> e : sizeChecks.entrySet()) {
            if (e.getValue().isChecked()) {
                out.add(e.getKey());
            }
        }
        return SellerFormRules.sortSizes(out);
    }

    private static boolean contains(JSONArray array, String value) {
        if (array == null) {
            return false;
        }
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) {
                return true;
            }
        }
        return false;
    }

    // ───────────── 사진 ─────────────

    private void onImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        status.setText("사진을 준비하는 중이에요…");
        status.setTextColor(SellerUi.GRAY);
        new Thread(() -> {
            byte[] jpeg = null;
            Bitmap bitmap = null;
            try {
                bitmap = loadUpright(uri);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                jpeg = out.toByteArray();
            } catch (Exception | OutOfMemoryError e) {
                jpeg = null;
            }
            final byte[] result = jpeg;
            final Bitmap shown = bitmap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (result == null) {
                    status.setText("사진을 읽지 못했어요. 다른 사진을 골라 주세요.");
                    status.setTextColor(SellerUi.RED);
                    return;
                }
                imageBytes = result;
                imageUrl = null;            // 새 사진이니 등록할 때 다시 올린다
                preview.setImageBitmap(shown);
                status.setText("");
            });
        }, "seller-image").start();
    }

    private Bitmap loadUpright(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > MAX_IMAGE_SIDE || bounds.outHeight / sample > MAX_IMAGE_SIDE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IllegalStateException("사진을 읽지 못했어요");
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
            // 방향 정보가 없으면 그대로 쓴다
        }
        int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // ───────────── 등록 ─────────────

    private void submit() {
        if (busy) {
            return;
        }
        JSONObject body;
        try {
            body = buildBody();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        } catch (JSONException e) {
            Toast.makeText(this, "입력을 읽지 못했어요", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, imageBytes != null && imageUrl == null ? "사진을 올리는 중이에요…" : "등록하는 중이에요…");
        if (imageBytes != null && imageUrl == null) {
            SellerApi.uploadImage(imageBytes, new SellerApi.Callback() {
                @Override
                public void onSuccess(JSONObject result) {
                    imageUrl = result.optString("url");
                    send(body);
                }

                @Override
                public void onError(String message) {
                    fail(message);
                }
            });
        } else {
            send(body);
        }
    }

    private void send(JSONObject body) {
        try {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                body.put("image_url", imageUrl);
            }
        } catch (JSONException e) {
            fail("입력을 읽지 못했어요");
            return;
        }
        setBusy(true, "등록하는 중이에요…");
        SellerApi.register(body, new SellerApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                SellerStore.saveBrand(SellerProductActivity.this, body.optString("brand"));
                Toast.makeText(SellerProductActivity.this, "상품을 등록했어요. 추천에 바로 반영돼요.", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private void fail(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        setBusy(false, "");
        AppDialog.alert(this, "등록하지 못했어요", message, "확인");
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        SellerUi.setEnabled(btnRegister, !busy);
        status.setText(message);
        status.setTextColor(SellerUi.GRAY);
    }

    /** 화면의 입력을 서버 요청 본문으로 바꾼다. 빠뜨린 것이 있으면 IllegalArgumentException(한국어 문장). */
    private JSONObject buildBody() throws JSONException {
        String category = selectedCategory();
        String brand = SellerStore.normalizeBrand(etBrand.getText().toString());
        if (brand == null) {
            throw new IllegalArgumentException("브랜드 이름을 " + SellerStore.BRAND_MIN + "~" + SellerStore.BRAND_MAX + "자로 입력해 주세요");
        }
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("상품 이름을 입력해 주세요");
        }
        int price = SellerFormRules.parsePrice(etPrice.getText().toString());
        if (price < 0) {
            throw new IllegalArgumentException("가격을 1원~" + SellerFormRules.MAX_PRICE + "원 사이 숫자로 입력해 주세요");
        }
        JSONArray seasons = new JSONArray();
        for (Map.Entry<String, CheckBox> e : seasonChecks.entrySet()) {
            if (e.getValue().isChecked()) {
                seasons.put(e.getKey());
            }
        }
        if (seasons.length() == 0) {
            throw new IllegalArgumentException("계절을 하나 이상 골라 주세요");
        }
        List<String> sizes = selectedSizes();
        if (sizes.isEmpty()) {
            throw new IllegalArgumentException("사이즈를 하나 이상 골라 주세요");
        }
        JSONArray colors = new JSONArray();
        for (Map.Entry<String, CheckBox> e : colorChecks.entrySet()) {
            if (e.getValue().isChecked()) {
                colors.put(e.getKey());
            }
        }
        if (colors.length() == 0) {
            throw new IllegalArgumentException("색상을 하나 이상 골라 주세요");
        }

        JSONObject measurements = new JSONObject();
        for (String size : sizes) {
            JSONObject row = new JSONObject();
            Map<String, EditText> inputs = measureInputs.get(size);
            if (inputs == null) {
                throw new IllegalArgumentException(size + " 사이즈의 치수를 입력해 주세요");
            }
            for (Map.Entry<String, EditText> field : inputs.entrySet()) {
                double cm = SellerFormRules.parseCm(field.getValue().getText().toString());
                if (Double.isNaN(cm)) {
                    throw new IllegalArgumentException(size + " 사이즈의 \"" + field.getValue().getHint() + "\"를 숫자(cm)로 입력해 주세요");
                }
                row.put(field.getKey(), cm);
            }
            measurements.put(size, row);
        }

        JSONObject attrs = new JSONObject();
        for (Map.Entry<String, Spinner> e : attrSpinners.entrySet()) {
            String chosen = String.valueOf(e.getValue().getSelectedItem());
            if (!NONE.equals(chosen) && !PICK.equals(chosen)) {
                attrs.put(e.getKey(), chosen);
            } else if (PICK.equals(chosen)) {
                throw new IllegalArgumentException("필수 항목을 골라 주세요: " + labelOf(e.getKey()));
            }
        }
        for (Map.Entry<String, List<CheckBox>> e : attrChecks.entrySet()) {
            JSONArray chosen = new JSONArray();
            for (CheckBox box : e.getValue()) {
                if (box.isChecked()) {
                    chosen.put(box.getText().toString());
                }
            }
            if (chosen.length() > 0) {
                attrs.put(e.getKey(), chosen);
            }
        }

        JSONObject body = new JSONObject();
        body.put("brand", brand);
        body.put("product_name", name);
        body.put("clothing_type", category);
        body.put("price", price);
        body.put("description", etDescription.getText().toString().trim());
        body.put("seasons", seasons);
        body.put("sizes", new JSONArray(sizes));
        body.put("colors", colors);
        body.put("measurements", measurements);

        String mode = compositionMode == null ? COMPOSITION_INPUT : String.valueOf(compositionMode.getSelectedItem());
        if (COMPOSITION_INPUT.equals(mode)) {
            JSONArray composition = new JSONArray();
            List<Double> percents = new ArrayList<>();
            for (int i = 0; i < fiberSpinners.size(); i++) {
                String fiber = String.valueOf(fiberSpinners.get(i).getSelectedItem());
                String percentText = fiberPercents.get(i).getText().toString().trim();
                if (NONE.equals(fiber) && percentText.isEmpty()) {
                    continue;
                }
                double percent = SellerFormRules.parseCm(percentText);
                if (NONE.equals(fiber) || Double.isNaN(percent) || percent <= 0 || percent > 100) {
                    throw new IllegalArgumentException("혼용률은 섬유와 비율(1~100)을 함께 입력해 주세요");
                }
                composition.put(new JSONObject().put("fiber", fiber).put("percent", percent));
                percents.add(percent);
            }
            if (!SellerFormRules.compositionSumsTo100(percents)) {
                throw new IllegalArgumentException("혼용률의 합이 100%가 되어야 해요");
            }
            body.put("composition_percent", composition);
        } else {
            attrs.put("composition_status", mode);
        }
        body.put("attrs", attrs);
        return body;
    }

    private String labelOf(String key) {
        try {
            JSONArray fields = form.getJSONArray("attributes");
            for (int i = 0; i < fields.length(); i++) {
                if (key.equals(fields.getJSONObject(i).optString("key"))) {
                    return fields.getJSONObject(i).optString("label");
                }
            }
        } catch (JSONException ignored) {
            // 아래에서 키를 그대로 보여 준다
        }
        return key;
    }
}
