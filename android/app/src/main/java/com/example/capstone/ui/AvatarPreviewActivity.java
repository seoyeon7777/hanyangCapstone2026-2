package com.example.capstone.ui;

import android.content.Intent;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.avatar.BodyMeasurementContract;
import com.example.capstone.avatar.DemoBodySamples;
import com.example.capstone.avatar.MorphCmLut;
import com.example.capstone.avatar.MorphTargetMap;
import com.example.capstone.avatar.PartCoupling;
import com.example.capstone.avatar.PopulationCmRanges;
import com.example.capstone.avatar.SmplHybridSolver;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.LightManager;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.TransformManager;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.Manipulator;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Utils;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvatarPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_HEIGHT_CM = "extra_height_cm";
    /** Optional JSON map of BodyPart name → cm, e.g. {"CHEST":84.6,"ARM_LENGTH":56.0}. */
    public static final String EXTRA_PARTS_JSON = "extra_parts_json";
    /** Optional short label shown in the info line (demo / QA). */
    public static final String EXTRA_SAMPLE_LABEL = "extra_sample_label";
    /** Demo body id — see {@link com.example.capstone.avatar.DemoBodySamples}. */
    public static final String EXTRA_SAMPLE_ID = "extra_sample_id";
    public static final String EXTRA_BODY_FAT = "extra_body_fat";
    public static final String EXTRA_EDIT_PROFILE_ID = "extra_edit_profile_id";

    private static final float BASE_HEIGHT_CM = 170f;
    private static final float FULL_FOCAL_LENGTH = 28f;
    /** Extra scale after unit-cube fit. */
    private static final float PREVIEW_EXTRA_SCALE = 1.55f;
    /** How much of the viewport height the body should fill. */
    private static final float PREVIEW_FRAME_FILL = 0.92f;
    private static final float FILM_HALF_HEIGHT_MM = 12f;
    private static final float LOOK_AT_X = 0f;
    private static final float LOOK_AT_Y = 0f;
    private static final float LOOK_AT_Z = -4f;
    private static final float CLOSE_UP_NDC_SHIFT = -0.22f;
    /** 허벅지·종아리는 패널이 잘 가리므로 더 옆으로 민다. */
    private static final float CLOSE_UP_NDC_SHIFT_LEG = -0.36f;

    private TextureView viewAvatar;
    private View avatarTouch;
    private View avatarStage;
    private View panelMeasure;
    private TextView tvPartHint;
    private TextView tvPartName;
    private LinearLayout partRows;

    private ModelViewer modelViewer;
    /** Sole orbit controller — ModelViewer's copy is detached so render cannot desync. */
    private Manipulator cameraManipulator;
    private Choreographer choreographer;
    private boolean orbiting;
    private boolean modelFitted;
    private boolean updatingSlider;
    /** Filament animator clears morphs; re-push only when weights change or bones update. */
    private boolean morphWeightsDirty = true;
    private boolean animationPlaying;
    private int panelFrameCounter;
    private float heightCm = BASE_HEIGHT_CM;
    private float[] fittedRootTransform;
    private float[] currentRootTransform;
    private float orbitTargetX = LOOK_AT_X;
    private float orbitTargetY = LOOK_AT_Y;
    private float orbitTargetZ = LOOK_AT_Z;
    /** Camera pan for close-up. Do not translate the skinned root — that jerks for one frame. */
    private boolean closeUpActive;
    private float closeUpOffsetX;
    private float closeUpOffsetY;
    private float closeUpOffsetZ;
    private final double[] cameraEye = new double[3];
    private final double[] cameraTarget = new double[3];
    private final double[] cameraUp = new double[3];

    private Region selectedRegion;
    private float selectedSide = 1f;

    private float downX;
    private float downY;
    private boolean dragged;
    private int touchSlop;

    private final Map<Integer, float[]> originalTransforms = new HashMap<>();
    private final Map<Integer, float[]> boneScales = new HashMap<>();
    private final Map<BodyPart, Float> partValues = new HashMap<>();
    private final Map<Region, TextView> regionChips = new EnumMap<>(Region.class);
    private final Map<BodyPart, MeasureRow> activeRows = new EnumMap<>(BodyPart.class);

    /** 몸 + 옷/머리/눈 등 morph가 있는 모든 렌더러블. 같은 weight를 같이 넣는다. */
    private final List<Integer> morphRenderables = new ArrayList<>();
    /** Logical morph name -> glTF name (commercial body swap). */
    private final MorphTargetMap morphTargetMap = new MorphTargetMap();
    /** BodyPart.ordinal() -> morph index on GLB (-1 if missing). */
    private final int[] partMorphIndex = new int[BodyPart.values().length];
    private int morphIndexSlim = -1;
    private int morphIndexHeavy = -1;
    /** Combined 팔길이 drives these two keys. They are not separate sliders. */
    private int morphIndexUpperArmLength = -1;
    private int morphIndexForearmLength = -1;
    /** Combined 다리길이 drives thigh + calf length keys. */
    private int morphIndexThighLength = -1;
    private int morphIndexCalfLength = -1;

    private enum BodyType { SLIM, STANDARD, HEAVY }

    private int morphTargetCount = 0;
    private float[] morphWeights = new float[0];
    private final MorphCmLut morphCmLut = new MorphCmLut();
    private final SmplHybridSolver hybridSolver = new SmplHybridSolver();
    private final PartCoupling partCoupling = new PartCoupling();
    private final PopulationCmRanges populationCmRanges = new PopulationCmRanges();
    /** Shape000.. indices when hybrid GLB is loaded. */
    private int[] betaMorphIndex = new int[0];
    private boolean hybridMode;
    private boolean couplingLocked;
    private boolean updatingBodyFat;
    /** -1 마름 ~ 0 표준(Basis) ~ +1 통통. 진입 시 0이어야 메시가 안 깨짐. */
    private static final float STANDARD_BODY_FAT = 0f;
    private float bodyFat = STANDARD_BODY_FAT;
    private BodyType bodyType = BodyType.STANDARD;
    private String sampleLabel;
    private SeekBar seekBodyFat;
    private final Map<BodyType, TextView> bodyTypeChips = new EnumMap<>(BodyType.class);

    /** 카드에 올라간 치수 한 줄. 부위를 바꿀 때마다 다시 만든다. */
    private static final class MeasureRow {
        final TextView value;
        final SeekBar seek;
        int minTenths;

        MeasureRow(TextView value, SeekBar seek) {
            this.value = value;
            this.seek = seek;
        }
    }

    private final Choreographer.FrameCallback frameCallback =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    choreographer.postFrameCallback(this);

                    if (modelViewer == null) {
                        return;
                    }

                    if (!modelFitted && modelViewer.getProgress() >= 1.0f
                            && modelViewer.getAsset() != null
                            && viewAvatar.getWidth() > 0
                            && viewAvatar.getHeight() > 0) {
                        applyHeightTransform();
                        modelFitted = true;
                        morphWeightsDirty = true;
                    }

                    // Static T-pose preview: skip bone updates. Filament's animator
                    // clears morph weights every updateBoneMatrices() call.
                    if (animationPlaying && modelViewer.getAnimator() != null) {
                        modelViewer.getAnimator().updateBoneMatrices();
                        morphWeightsDirty = true;
                    }
                    if (modelFitted && morphWeightsDirty && morphWeights.length > 0) {
                        applyMorphWeightsToAll();
                        morphWeightsDirty = false;
                    }

                    // Drive the camera from our manipulator only (MV's is detached).
                    syncCameraFromManipulator();
                    modelViewer.render(frameTimeNanos);

                    // Panel projection is expensive — only while orbiting or every 3rd frame.
                    if (selectedRegion != null) {
                        if (orbiting || (panelFrameCounter++ % 3) == 0) {
                            updatePanelPosition();
                        }
                    }
                }
            };

    /** 확대 단위. 한 부위에 걸린 치수는 카드에 함께 띄운다. */
    /**
     * bodyFraction / sideFraction 은 현재 avatar.glb 바운딩 박스 기준 실측값.
     * (발=0 … 머리=1, side 는 halfExtent.x 비율. 팔·다리는 좌우 ±side)
     */
    private enum Region {
        // closeUpFocal: full body is 28mm — keep mild zoom so morph edits stay visible.
        NECK("목", 40f, 0.875f, 0f),
        SHOULDER("어깨", 38f, 0.825f, 0f),
        CHEST("가슴", 36f, 0.755f, 0f),
        WAIST("허리", 38f, 0.630f, 0f),
        UPPER_ARM("팔", 38f, 0.780f, 0.44f),
        HIP("엉덩이", 38f, 0.510f, 0f),
        LEG("다리", 34f, 0.300f, 0.28f);

        final String label;
        final float closeUpFocal;
        final float bodyFraction;
        final float sideFraction;

        Region(String label, float closeUpFocal, float bodyFraction, float sideFraction) {
            this.label = label;
            this.closeUpFocal = closeUpFocal;
            this.bodyFraction = bodyFraction;
            this.sideFraction = sideFraction;
        }

        List<BodyPart> parts() {
            List<BodyPart> list = new ArrayList<>();
            for (BodyPart part : BodyPart.values()) {
                if (part.region == this) {
                    list.add(part);
                }
            }
            return list;
        }
    }

    private enum BodyPart {
        NECK("목둘레", "둘레", Region.NECK, 0.195f, Kind.GIRTH, new String[]{
                "mixamorig:Neck"
        }),
        SHOULDER("어깨너비", "너비", Region.SHOULDER, 0.225f, Kind.GIRTH, new String[]{
                "mixamorig:LeftShoulder", "mixamorig:RightShoulder"
        }),
        CHEST("가슴둘레", "둘레", Region.CHEST, 0.52f, Kind.GIRTH, new String[]{
                "mixamorig:Spine2"
        }),
        BACK_LENGTH("상체길이", "상체길이", Region.CHEST, 0.22f, Kind.LENGTH, new String[]{
                "mixamorig:Spine", "mixamorig:Spine1", "mixamorig:Spine2"
        }),
        WAIST("허리둘레", "둘레", Region.WAIST, 0.45f, Kind.GIRTH, new String[]{
                "mixamorig:Spine", "mixamorig:Spine1"
        }),
        UPPER_ARM("상완둘레", "상완둘레", Region.UPPER_ARM, 0.175f, Kind.GIRTH, new String[]{
                "mixamorig:LeftArm", "mixamorig:RightArm"
        }),
        FOREARM("전완둘레", "전완둘레", Region.UPPER_ARM, 0.138f, Kind.GIRTH, new String[]{
                "mixamorig:LeftForeArm", "mixamorig:RightForeArm"
        }),
        ARM_LENGTH("팔길이", "팔길이", Region.UPPER_ARM, 0.30f, Kind.LENGTH, new String[]{
                "mixamorig:LeftArm", "mixamorig:RightArm",
                "mixamorig:LeftForeArm", "mixamorig:RightForeArm"
        }),
        HIP("엉덩이둘레", "둘레", Region.HIP, 0.55f, Kind.GIRTH, new String[]{
                "mixamorig:Hips"
        }),
        THIGH("허벅지둘레", "허벅지둘레", Region.LEG, 0.32f, Kind.GIRTH, new String[]{
                "mixamorig:LeftUpLeg", "mixamorig:RightUpLeg"
        }),
        CALF("종아리둘레", "종아리둘레", Region.LEG, 0.21f, Kind.GIRTH, new String[]{
                "mixamorig:LeftLeg", "mixamorig:RightLeg"
        }),
        LEG_LENGTH("다리길이", "다리길이", Region.LEG, 0.48f, Kind.LENGTH, new String[]{
                "mixamorig:LeftUpLeg", "mixamorig:RightUpLeg",
                "mixamorig:LeftLeg", "mixamorig:RightLeg"
        });

        /** 둘레·너비는 굵기, 길이는 뼈 방향, SIZE는 S/M/L 이산. */
        enum Kind { GIRTH, LENGTH, SIZE }

        final String label;
        final String rowLabel;
        final Region region;
        final float heightRatio;
        final Kind kind;
        final String[] scaleBones;

        BodyPart(String label, String rowLabel, Region region, float heightRatio,
                 Kind kind, String[] scaleBones) {
            this.label = label;
            this.rowLabel = rowLabel;
            this.region = region;
            this.heightRatio = heightRatio;
            this.kind = kind;
            this.scaleBones = scaleBones;
        }

        float defaultCm(float height) {
            return Math.round(height * heightRatio * 10f) / 10f;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_preview);

        Utils.INSTANCE.init();
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        heightCm = BodyMeasurementContract.heightFromIntent(getIntent(), 0f);
        if (heightCm <= 0f) {
            heightCm = getSharedPreferences("avatar_input", MODE_PRIVATE)
                    .getFloat("height_cm", BASE_HEIGHT_CM);
        }

        // 미리보기 치수는 세션 전용. GLB 원본/저장값을 바꾸지 않는다.
        // LUT 절대 cm: w=0 인구 줄자 스케일(여성 median 캘리브)이 슬라이더 기본값.
        morphCmLut.load(this);
        hybridSolver.load(this);
        morphTargetMap.load(this);
        partCoupling.load(this);
        populationCmRanges.load(this);
        morphCmLut.applyRealTape(populationCmRanges, heightCm);
        for (BodyPart part : BodyPart.values()) {
            float base = defaultCmFor(part);
            float[] range = morphCmRange(part);
            float start = Math.max(range[0], Math.min(range[1], base));
            if (base < range[0] || base > range[1]) {
                start = (range[0] + range[1]) * 0.5f;
            }
            partValues.put(part, start);
        }
        sampleLabel = getIntent().getStringExtra(EXTRA_SAMPLE_LABEL);
        applyDemoSample(getIntent().getStringExtra(EXTRA_SAMPLE_ID));
        String partsJson = BodyMeasurementContract.partsJsonFromIntent(getIntent());
        if (partsJson == null) {
            partsJson = getIntent().getStringExtra(EXTRA_PARTS_JSON);
        }
        applyPartsJsonExtra(partsJson);

        viewAvatar = findViewById(R.id.viewAvatar);
        avatarTouch = findViewById(R.id.avatarTouch);
        avatarStage = findViewById(R.id.avatarStage);
        panelMeasure = findViewById(R.id.panelMeasure);
        tvPartHint = findViewById(R.id.tvPartHint);
        tvPartName = findViewById(R.id.tvPartName);
        partRows = findViewById(R.id.partRows);

        TextView tvAvatarInfo = findViewById(R.id.tvAvatarInfo);
        tvAvatarInfo.setText(buildAvatarInfoLine());

        buildBodyTypeChips();
        setupBodyFatSeek();
        if (getIntent().hasExtra(EXTRA_BODY_FAT)) {
            restoreBodyFat(getIntent().getFloatExtra(EXTRA_BODY_FAT, STANDARD_BODY_FAT));
        }
        buildRegionChips();

        choreographer = Choreographer.getInstance();
        setupViewer();
        setListeners();
    }

    private void buildBodyTypeChips() {
        LinearLayout row = findViewById(R.id.bodyTypeRow);
        if (row == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int paddingH = Math.round(14f * density);
        int paddingV = Math.round(7f * density);
        int gap = Math.round(6f * density);

        for (BodyType type : BodyType.values()) {
            TextView chip = new TextView(this);
            chip.setText(bodyTypeLabel(type));
            chip.setTextSize(12f);
            chip.setTextColor(androidx.core.content.ContextCompat
                    .getColorStateList(this, R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip_state);
            chip.setPadding(paddingH, paddingV, paddingH, paddingV);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> applyBodyType(type));
            row.addView(chip);
            bodyTypeChips.put(type, chip);
        }
        syncBodyTypeChips();
    }

    private static String bodyTypeLabel(BodyType type) {
        switch (type) {
            case SLIM:
                return "마름";
            case HEAVY:
                return "통통";
            case STANDARD:
            default:
                return "표준";
        }
    }

    private void syncBodyTypeChips() {
        for (Map.Entry<BodyType, TextView> entry : bodyTypeChips.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == bodyType);
        }
    }

    private void setupBodyFatSeek() {
        seekBodyFat = findViewById(R.id.seekBodyFat);
        if (seekBodyFat == null) {
            return;
        }
        seekBodyFat.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingBodyFat) {
                    return;
                }
                // progress 0..200 -> bodyFat -1..+1
                applyBodyFat((progress - 100) / 100f, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void applyBodyType(BodyType type) {
        float fat = STANDARD_BODY_FAT; // 표준은 0보다 약간 슬림
        if (type == BodyType.SLIM) {
            fat = -1f;
        } else if (type == BodyType.HEAVY) {
            fat = 1f;
        }
        applyBodyFat(fat, true);
    }

    /**
     * 전체 체형: MakeHuman weight macro + 둘레 부위 cm를 같이 이동.
     * 길이는 그대로 두고, 둘레만 실루엣에 맞게 움직여 "살만 단독" 느낌을 줄인다.
     * 표준은 morph 0 (Basis). 예전의 약간 슬림 기본값은 진입 직 전신을 왜곡함.
     */
    private void applyBodyFat(float fat, boolean syncUi) {
        bodyFat = Math.max(-1f, Math.min(1f, fat));
        if (bodyFat <= -0.55f) {
            bodyType = BodyType.SLIM;
        } else if (bodyFat >= 0.33f) {
            bodyType = BodyType.HEAVY;
        } else {
            bodyType = BodyType.STANDARD;
        }
        syncBodyTypeChips();
        if (syncUi && seekBodyFat != null) {
            updatingBodyFat = true;
            seekBodyFat.setProgress(Math.round(bodyFat * 100f) + 100);
            updatingBodyFat = false;
        }

        if (morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
            morphWeights[morphIndexSlim] = 0f;
        }
        if (morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
            morphWeights[morphIndexHeavy] = 0f;
        }
        if (morphIndexSlim >= 0 || morphIndexHeavy >= 0) {
            // 통통/마름을 더 또렷하게 (1.0 풀은 깨질 수 있어 0.92 상한)
            float strength = Math.abs(bodyFat) * 0.92f;
            if (bodyFat < 0f && morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
                morphWeights[morphIndexSlim] = strength;
            }
            if (bodyFat > 0f && morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
                morphWeights[morphIndexHeavy] = strength;
            }
        }

        // 둘레 치수도 전체적으로 같이 이동 (길이·등길이는 유지)
        // 기준은 범위 중앙이 아니라 LUT 기본(w=0) — 평균 Basis에서 mid≠baseline이면 깨짐
        couplingLocked = true;
        try {
            for (BodyPart part : BodyPart.values()) {
                if (part.kind != BodyPart.Kind.GIRTH) {
                    continue;
                }
                float base = defaultCmFor(part);
                float[] range = morphCmRange(part);
                float half = (range[1] - range[0]) * 0.5f;
                float next = base + bodyFat * half * 0.55f;
                next = Math.max(range[0], Math.min(range[1], next));
                partValues.put(part, next);
                if (!hybridMode) {
                    applyPartDeform(part, next, base, false);
                }
                syncMeasureRow(part, next, true);
            }
        } finally {
            couplingLocked = false;
        }
        if (hybridMode) {
            applyHybridDeformAll();
            // Re-apply body macros on top of beta/residual (hybrid clears weights).
            if (morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
                morphWeights[morphIndexSlim] = 0f;
            }
            if (morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
                morphWeights[morphIndexHeavy] = 0f;
            }
            float strength = Math.abs(bodyFat) * 0.92f;
            if (bodyFat < 0f && morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
                morphWeights[morphIndexSlim] = strength;
            }
            if (bodyFat > 0f && morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
                morphWeights[morphIndexHeavy] = strength;
            }
        }
        applyMorphWeightsToAll();

        TextView tvAvatarInfo = findViewById(R.id.tvAvatarInfo);
        if (tvAvatarInfo != null) {
            tvAvatarInfo.setText(buildAvatarInfoLine()
                    + " · 체형 " + bodyTypeLabel(bodyType));
        }
    }

    /** 골반과 엉덩이처럼 붙어 있는 부위는 탭만으로 고르기 어려워 칩으로도 고를 수 있게 한다. */
    private void buildRegionChips() {
        LinearLayout chipRow = findViewById(R.id.chipRow);
        float density = getResources().getDisplayMetrics().density;
        int paddingH = Math.round(14f * density);
        int paddingV = Math.round(7f * density);
        int gap = Math.round(6f * density);

        for (Region region : Region.values()) {
            TextView chip = new TextView(this);
            chip.setText(region.label);
            chip.setTextSize(12f);
            chip.setTextColor(androidx.core.content.ContextCompat
                    .getColorStateList(this, R.color.chip_text));
            chip.setBackgroundResource(R.drawable.bg_chip_state);
            chip.setPadding(paddingH, paddingV, paddingH, paddingV);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                // 팔다리는 치수 카드 반대쪽 것을 잡아야 가려지지 않는다.
                selectedSide = -1f;
                selectRegion(region);
            });
            chipRow.addView(chip);
            regionChips.put(region, chip);
        }
    }

    private void syncChipSelection() {
        for (Map.Entry<Region, TextView> entry : regionChips.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == selectedRegion);
        }
    }

    private void setupViewer() {
        Engine engine = Engine.create();
        UiHelper uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        uiHelper.setOpaque(true);

        cameraManipulator = new Manipulator.Builder()
                .targetPosition(LOOK_AT_X, LOOK_AT_Y, LOOK_AT_Z)
                .orbitHomePosition(LOOK_AT_X, LOOK_AT_Y, LOOK_AT_Z + 4f)
                .viewport(Math.max(1, viewAvatar.getWidth()), Math.max(1, viewAvatar.getHeight()))
                .build(Manipulator.Mode.ORBIT);
        modelViewer = new ModelViewer(viewAvatar, engine, uiHelper, cameraManipulator);
        modelViewer.setCameraFocalLength(FULL_FOCAL_LENGTH);
        // Prevent ModelViewer.render() from using a second, stale manipulator copy.
        detachModelViewerManipulator();

        avatarTouch.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = v.getWidth();
            int height = v.getHeight();
            if (width > 0 && height > 0 && cameraManipulator != null) {
                cameraManipulator.setViewport(width, height);
            }
        });

        setupClearAndLighting();
        loadModel();
    }

    /**
     * ModelViewer keeps its own private manipulator and overwrites the camera each
     * frame from it. Touch updates only Activity.cameraManipulator — if those two
     * diverge, the model appears to swing off-center. Null MV's copy and push
     * look-at ourselves via {@link #syncCameraFromManipulator()}.
     */
    private void detachModelViewerManipulator() {
        try {
            java.lang.reflect.Field manipField =
                    ModelViewer.class.getDeclaredField("cameraManipulator");
            manipField.setAccessible(true);
            manipField.set(modelViewer, null);
            java.lang.reflect.Field gestureField =
                    ModelViewer.class.getDeclaredField("gestureDetector");
            gestureField.setAccessible(true);
            gestureField.set(modelViewer, null);
        } catch (ReflectiveOperationException e) {
            android.util.Log.e("AvatarPreview", "Failed to detach ModelViewer manipulator", e);
        }
    }

    private void syncCameraFromManipulator() {
        if (cameraManipulator == null || modelViewer == null) {
            return;
        }
        cameraManipulator.getLookAt(cameraEye, cameraTarget, cameraUp);
        if (closeUpActive) {
            cameraEye[0] += closeUpOffsetX;
            cameraEye[1] += closeUpOffsetY;
            cameraEye[2] += closeUpOffsetZ;
            cameraTarget[0] += closeUpOffsetX;
            cameraTarget[1] += closeUpOffsetY;
            cameraTarget[2] += closeUpOffsetZ;
        }
        modelViewer.getCamera().lookAt(
                cameraEye[0], cameraEye[1], cameraEye[2],
                cameraTarget[0], cameraTarget[1], cameraTarget[2],
                cameraUp[0], cameraUp[1], cameraUp[2]);
    }

    private void setupClearAndLighting() {
        Renderer renderer = modelViewer.getRenderer();
        Renderer.ClearOptions clearOptions = renderer.getClearOptions();
        clearOptions.clear = true;
        // Slightly darker stage so a pale body doesn't wash out.
        clearOptions.clearColor[0] = 0.38f;
        clearOptions.clearColor[1] = 0.39f;
        clearOptions.clearColor[2] = 0.42f;
        clearOptions.clearColor[3] = 1.0f;
        renderer.setClearOptions(clearOptions);

        modelViewer.getView().setBlendMode(
                com.google.android.filament.View.BlendMode.OPAQUE
        );
        modelViewer.getView().setAntiAliasing(
                com.google.android.filament.View.AntiAliasing.NONE
        );

        // Default ModelViewer exposure is outdoor-ish; stop clipping the key light.
        modelViewer.getCamera().setExposure(16.0f, 1.0f / 125.0f, 80.0f);

        Engine engine = modelViewer.getEngine();
        LightManager lightManager = engine.getLightManager();
        int sun = lightManager.getInstance(modelViewer.getLight());
        lightManager.setDirection(sun, 0.2f, -0.55f, -0.85f);
        lightManager.setIntensity(sun, 45_000.0f);
        lightManager.setShadowCaster(sun, false);

        int fillLight = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1.0f, 0.98f, 0.95f)
                .intensity(18_000.0f)
                .direction(0.65f, -0.2f, -0.55f)
                .castShadows(false)
                .build(engine, fillLight);
        modelViewer.getScene().addEntity(fillLight);

        // Back / rim — keep silhouette readable when orbiting.
        int rimLight = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.92f, 0.95f, 1.0f)
                .intensity(28_000.0f)
                .direction(-0.2f, -0.1f, 1.0f)
                .castShadows(false)
                .build(engine, rimLight);
        modelViewer.getScene().addEntity(rimLight);

        int backFill = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(0.95f, 0.97f, 1.0f)
                .intensity(22_000.0f)
                .direction(0.4f, -0.3f, 0.85f)
                .castShadows(false)
                .build(engine, backFill);
        modelViewer.getScene().addEntity(backFill);

        // Soft ambient from all sides (band-0 SH) so the back isn't pitch black.
        float[] sh = new float[27];
        sh[0] = 1.0f;
        sh[1] = 1.0f;
        sh[2] = 1.0f;
        IndirectLight ibl = new IndirectLight.Builder()
                .irradiance(1, sh)
                .intensity(35_000.0f)
                .build(engine);
        modelViewer.getScene().setIndirectLight(ibl);
    }

    private void loadModel() {
        // Read off the UI thread — avatar.glb is multi-MB and was stalling first frame.
        new Thread(() -> {
            try (InputStream input = getAssets().open("models/avatar.glb")) {
                byte[] bytes = readAllBytes(input);
                if (bytes.length == 0) {
                    throw new IOException("empty model");
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.order(ByteOrder.nativeOrder());
                buffer.put(bytes);
                buffer.rewind();
                runOnUiThread(() -> {
                    if (modelViewer == null || isFinishing()) {
                        return;
                    }
                    modelViewer.loadModelGlb(buffer);
                    morphWeightsDirty = true;
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(
                        this, "3D 모델을 불러오지 못했습니다.", Toast.LENGTH_LONG).show());
            }
        }, "avatar-glb-load").start();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(4 * 1024 * 1024);
        while ((n = input.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private void applyHeightTransform() {
        // Do not call animator.updateBoneMatrices() here — it clears morph weights
        // and hitchs the first fitted frame on a heavy GLB.

        // With a normalized GLB (origin = mesh center, no Mixamo root X-rot),
        // unit-cube fit matches the visible body.
        modelViewer.transformToUnitCube(new Float3(LOOK_AT_X, LOOK_AT_Y, LOOK_AT_Z));

        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null) {
            return;
        }

        TransformManager tm = modelViewer.getEngine().getTransformManager();
        int root = tm.getInstance(asset.getRoot());
        float[] transform = new float[16];
        tm.getTransform(root, transform);

        // Do NOT uniform-scale by heightCm. Vertical size comes from BACK_LENGTH +
        // LEG_LENGTH morphs (실측). heightCm is only for population bins / UI label.
        // Scaling by height then lengthening legs was making short+leggy look taller
        // than tall+average.
        float previewScale = PREVIEW_EXTRA_SCALE;
        scaleAroundLookAt(transform, previewScale);
        tm.setTransform(root, transform);

        fittedRootTransform = Arrays.copyOf(transform, 16);
        currentRootTransform = Arrays.copyOf(transform, 16);

        orbitTargetX = LOOK_AT_X;
        orbitTargetY = LOOK_AT_Y;
        orbitTargetZ = LOOK_AT_Z;

        // transformToUnitCube fits the longest AABB axis into [-1,1], so half-extent ≈ 1
        // before PREVIEW_EXTRA_SCALE.
        float worldHalfH = previewScale;

        int vw = Math.max(1, viewAvatar.getWidth());
        int vh = Math.max(1, viewAvatar.getHeight());
        float vFov = 2f * (float) Math.atan(FILM_HALF_HEIGHT_MM / FULL_FOCAL_LENGTH);
        float distance = worldHalfH / Math.max(1e-4f, (float) Math.tan(vFov * 0.5f)) / PREVIEW_FRAME_FILL;
        distance = Math.max(1.8f, Math.min(distance, 8f));

        cameraManipulator = new Manipulator.Builder()
                .targetPosition(orbitTargetX, orbitTargetY, orbitTargetZ)
                .orbitHomePosition(orbitTargetX, orbitTargetY, orbitTargetZ + distance)
                .viewport(vw, vh)
                .build(Manipulator.Mode.ORBIT);
        // Keep MV manipulator null — we sync lookAt every frame ourselves.
        detachModelViewerManipulator();
        syncCameraFromManipulator();

        bindMorphTargets(asset);
        applyPreviewShading(asset);
    }

    /**
     * 몸·옷·머리·눈 등 morph가 있는 모든 메시에 같은 weight를 넣는다.
     * 인덱스는 glTF morph **이름** + morph_target_map.json 으로 해석한다.
     */
    private void bindMorphTargets(FilamentAsset asset) {
        morphRenderables.clear();
        morphTargetCount = 0;
        morphIndexSlim = -1;
        morphIndexHeavy = -1;
        morphIndexUpperArmLength = -1;
        morphIndexForearmLength = -1;
        morphIndexThighLength = -1;
        morphIndexCalfLength = -1;
        Arrays.fill(partMorphIndex, -1);
        morphWeights = new float[0];

        RenderableManager renderables = modelViewer.getEngine().getRenderableManager();
        String[] nameList = null;
        for (int entity : asset.getEntities()) {
            int instance = renderables.getInstance(entity);
            if (instance == 0) {
                continue;
            }
            int count = renderables.getMorphTargetCount(instance);
            if (count <= 0) {
                continue;
            }
            String[] names = asset.getMorphTargetNames(entity);
            if (names == null || names.length == 0) {
                continue;
            }
            morphRenderables.add(instance);
            if (nameList == null || names.length > nameList.length) {
                nameList = names;
                morphTargetCount = names.length;
            }
        }

        android.util.Log.i("AvatarMorph",
                "morph renderables=" + morphRenderables.size()
                        + " targetCount=" + morphTargetCount);

        if (morphRenderables.isEmpty() || nameList == null) {
            return;
        }

        morphWeights = new float[morphTargetCount];
        Map<String, Integer> resolved = morphTargetMap.resolveIndices(nameList);
        for (BodyPart part : BodyPart.values()) {
            Integer idx = resolved.get(part.name());
            partMorphIndex[part.ordinal()] = idx != null ? idx : -1;
        }
        Integer slim = resolved.get("BODY_SLIM");
        Integer heavy = resolved.get("BODY_HEAVY");
        morphIndexSlim = slim != null ? slim : -1;
        morphIndexHeavy = heavy != null ? heavy : -1;
        Integer upperLen = resolved.get("UPPER_ARM_LENGTH");
        morphIndexUpperArmLength = upperLen != null ? upperLen : -1;
        Integer foreLen = resolved.get("FOREARM_LENGTH");
        morphIndexForearmLength = foreLen != null ? foreLen : -1;
        Integer thighLen = resolved.get("THIGH_LENGTH");
        morphIndexThighLength = thighLen != null ? thighLen : -1;
        Integer calfLen = resolved.get("CALF_LENGTH");
        morphIndexCalfLength = calfLen != null ? calfLen : -1;

        // Hybrid: Shape000–009 (or BETA_i mapped)
        List<String> betaNames = hybridSolver.isLoaded()
                ? hybridSolver.betaNames()
                : Collections.emptyList();
        betaMorphIndex = new int[betaNames.size()];
        Arrays.fill(betaMorphIndex, -1);
        int betaMapped = 0;
        for (int i = 0; i < betaNames.size(); i++) {
            String bn = betaNames.get(i);
            Integer idx = resolved.get("BETA_" + i);
            if (idx == null || idx < 0) {
                // direct Shape000 name on mesh
                for (int n = 0; n < nameList.length; n++) {
                    if (bn.equals(nameList[n])) {
                        idx = n;
                        break;
                    }
                }
            }
            betaMorphIndex[i] = idx != null ? idx : -1;
            if (betaMorphIndex[i] >= 0) {
                betaMapped++;
            }
        }
        // Per-part sliders need independent morphs. Joint beta solve moves the whole body.
        hybridMode = false;
        android.util.Log.i("AvatarMorph",
                "per-part morph mode; betaMapped=" + betaMapped
                        + "/" + betaNames.size());

        int mapped = 0;
        for (int idx : partMorphIndex) {
            if (idx >= 0) {
                mapped++;
            }
        }
        android.util.Log.i("AvatarMorph",
                "name-mapped parts=" + mapped + "/" + BodyPart.values().length
                        + " slim=" + morphIndexSlim + " heavy=" + morphIndexHeavy
                        + " names=" + Arrays.toString(nameList));

        if (hybridMode) {
            applyHybridDeformAll();
        } else {
            for (BodyPart part : BodyPart.values()) {
                float base = defaultCmFor(part);
                float value = partValues.containsKey(part) ? partValues.get(part) : base;
                applyPartDeform(part, value, base, false);
            }
        }
        // Slim/heavy macros only. Do NOT call applyBodyFat here — that resets every
        // girth to baseline+fat and would wipe survey/demo cm (lengths were kept).
        applyBodyTypeMacrosOnly();
        TextView tvAvatarInfo = findViewById(R.id.tvAvatarInfo);
        if (tvAvatarInfo != null) {
            tvAvatarInfo.setText(buildAvatarInfoLine()
                    + " · 체형 " + bodyTypeLabel(bodyType));
        }
    }

    /** BODY_SLIM / BODY_HEAVY weights only. Leaves partValues alone. */
    private void applyBodyTypeMacrosOnly() {
        if (morphWeights.length == 0) {
            return;
        }
        if (morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
            morphWeights[morphIndexSlim] = 0f;
        }
        if (morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
            morphWeights[morphIndexHeavy] = 0f;
        }
        float strength = Math.abs(bodyFat) * 0.92f;
        if (bodyFat < 0f && morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
            morphWeights[morphIndexSlim] = strength;
        }
        if (bodyFat > 0f && morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
            morphWeights[morphIndexHeavy] = strength;
        }
        applyMorphWeightsToAll();
    }

    private int morphIndexFor(BodyPart part) {
        int i = part.ordinal();
        if (i < 0 || i >= partMorphIndex.length) {
            return -1;
        }
        return partMorphIndex[i];
    }

    private void applyMorphWeightsToAll() {
        if (modelViewer == null || morphRenderables.isEmpty() || morphWeights.length == 0) {
            return;
        }
        RenderableManager renderables = modelViewer.getEngine().getRenderableManager();
        for (int instance : morphRenderables) {
            int count = renderables.getMorphTargetCount(instance);
            if (count <= 0) {
                continue;
            }
            if (count == morphWeights.length) {
                renderables.setMorphWeights(instance, morphWeights, 0);
            } else {
                // Mesh morph count mismatch: pad/truncate to this instance.
                float[] buf = new float[count];
                System.arraycopy(morphWeights, 0, buf, 0,
                        Math.min(count, morphWeights.length));
                renderables.setMorphWeights(instance, buf, 0);
            }
        }
        morphWeightsDirty = false;
    }

    private void markMorphWeightsDirty() {
        morphWeightsDirty = true;
    }

    /** scale 0.7~1.4 → morph weight -1~+1 (LUT 없을 때 폴백). */
    private static float morphWeightFromScale(float scale) {
        float weight = (scale - 1f) / 0.4f;
        return Math.max(-1f, Math.min(1f, weight));
    }

    private PopulationCmRanges.Range populationRange(BodyPart part) {
        if (!populationCmRanges.isLoaded()) {
            return null;
        }
        return populationCmRanges.rangeFor(part.name(), heightCm);
    }

    /** 슬라이더 기본값: 인구 p50 우선, 없으면 LUT/mesh, 마지막으로 키×비율. */
    private float defaultCmFor(BodyPart part) {
        if (part.kind == BodyPart.Kind.SIZE) {
            return 1f; // M
        }
        if (part == BodyPart.ARM_LENGTH) {
            float upper = namedBaselineCm("UPPER_ARM_LENGTH");
            float fore = namedBaselineCm("FOREARM_LENGTH");
            if (!Float.isNaN(upper) && !Float.isNaN(fore)) {
                return Math.round((upper + fore) * 10f) / 10f;
            }
        }
        if (part == BodyPart.LEG_LENGTH) {
            float thigh = namedBaselineCm("THIGH_LENGTH");
            float calf = namedBaselineCm("CALF_LENGTH");
            if (!Float.isNaN(thigh) && !Float.isNaN(calf)) {
                return Math.round((thigh + calf) * 10f) / 10f;
            }
        }
        // Mesh rest (w=0) first — same unit as the slider.
        if (morphCmLut.isLoaded()) {
            float abs = morphCmLut.baselineCm(part.name(), heightCm);
            if (!Float.isNaN(abs) && abs > 1f) {
                return Math.round(abs * 10f) / 10f;
            }
        }
        PopulationCmRanges.Range pop = populationRange(part);
        if (pop != null && pop.medianCm > 1f) {
            return Math.round(pop.medianCm * 10f) / 10f;
        }
        return part.defaultCm(heightCm);
    }

    /**
     * 슬라이더 범위 = 모델 줄자가 실제로 움직이는 메쉬 cm.
     * 화면 숫자와 morph가 같은 단위(메쉬 줄자)다.
     */
    private float[] morphCmRange(BodyPart part) {
        if (part.kind == BodyPart.Kind.SIZE) {
            return new float[]{0f, 2f};
        }
        float[] mesh = meshOnlyCmRange(part);
        if (mesh != null) {
            return mesh;
        }
        PopulationCmRanges.Range pop = populationRange(part);
        if (pop != null && pop.maxCm > pop.minCm) {
            // No mesh LUT — fall back to pop, converted into mesh space if possible.
            float lo = morphCmLut.sizeKoreaToMeshCm(part.name(), pop.minCm);
            float hi = morphCmLut.sizeKoreaToMeshCm(part.name(), pop.maxCm);
            float loR = Math.round(Math.min(lo, hi) * 10f) / 10f;
            float hiR = Math.round(Math.max(lo, hi) * 10f) / 10f;
            if (hiR <= loR) {
                hiR = loR + 0.1f;
            }
            return new float[]{loR, hiR};
        }
        float base = defaultCmFor(part);
        return new float[]{
                Math.round(base * 0.82f * 10f) / 10f,
                Math.round(base * 1.18f * 10f) / 10f
        };
    }

    /** Mesh LUT usable window only (no population clamp). */
    private float[] meshOnlyCmRange(BodyPart part) {
        if (part == BodyPart.ARM_LENGTH) {
            float[] upper = namedCmRange("UPPER_ARM_LENGTH");
            float[] fore = namedCmRange("FOREARM_LENGTH");
            if (upper != null && fore != null) {
                float lo = Math.round((upper[0] + fore[0]) * 10f) / 10f;
                float hi = Math.round((upper[1] + fore[1]) * 10f) / 10f;
                if (hi > lo) {
                    return new float[]{lo, hi};
                }
            }
            return null;
        }
        if (part == BodyPart.LEG_LENGTH) {
            float[] thigh = namedCmRange("THIGH_LENGTH");
            float[] calf = namedCmRange("CALF_LENGTH");
            if (thigh != null && calf != null) {
                float lo = Math.round((thigh[0] + calf[0]) * 10f) / 10f;
                float hi = Math.round((thigh[1] + calf[1]) * 10f) / 10f;
                if (hi > lo) {
                    return new float[]{lo, hi};
                }
            }
            return null;
        }
        if (morphCmLut.isLoaded() && morphCmLut.hasUsable(part.name())) {
            float lo = morphCmLut.minAbsCm(part.name(), heightCm);
            float hi = morphCmLut.maxAbsCm(part.name(), heightCm);
            if (!Float.isNaN(lo) && !Float.isNaN(hi) && hi >= lo) {
                float loR = Math.round(Math.min(lo, hi) * 10f) / 10f;
                float hiR = Math.round(Math.max(lo, hi) * 10f) / 10f;
                if (hiR <= loR) {
                    hiR = loR + 0.1f;
                }
                return new float[]{loR, hiR};
            }
        }
        if (morphCmLut.isLoaded()) {
            float base = morphCmLut.baselineCm(part.name(), heightCm);
            if (Float.isNaN(base) || base <= 1f) {
                base = defaultCmFor(part);
            }
            float b = Math.round(base * 10f) / 10f;
            return new float[]{b, b + 0.1f};
        }
        return null;
    }

    /** cm → morph weight. 메쉬 LUT가 있으면 그 구간만. 없으면 더 이상 ±1로 밀지 않는다. */
    private float weightForPartCm(BodyPart part, float userCm, float defaultCm) {
        if (part.kind == BodyPart.Kind.SIZE) {
            return 0f;
        }
        if (morphCmLut.isLoaded() && morphCmLut.hasUsable(part.name())) {
            return morphCmLut.refineWeight(part.name(), userCm, defaultCm, heightCm);
        }
        if (morphCmLut.isLoaded()) {
            return 0f;
        }
        float[] range = morphCmRange(part);
        float lo = range[0];
        float hi = range[1];
        float mid = defaultCm;
        if (userCm <= mid) {
            float denom = mid - lo;
            if (denom < 1e-3f) {
                return 0f;
            }
            return Math.max(-1f, (userCm - mid) / denom);
        }
        float denom = hi - mid;
        if (denom < 1e-3f) {
            return 0f;
        }
        return Math.min(1f, (userCm - mid) / denom);
    }

    private float refinedWeightForPartCm(BodyPart part, float userCm, float defaultCm) {
        if (morphCmLut.isLoaded() && morphCmLut.hasUsable(part.name())) {
            return morphCmLut.refineWeight(part.name(), userCm, defaultCm, heightCm);
        }
        return weightForPartCm(part, userCm, defaultCm);
    }

        /**
     * Prefer morph targets when present. If this part has no morph, fall back to
     * Mixamo bone axis scale so length/girth still move on rigged bodies.
     */
    private void applyPartDeform(BodyPart part, float userCm, float defaultCm, boolean refine) {
        if (hybridMode) {
            applyHybridDeformAll();
            return;
        }
        if (part == BodyPart.ARM_LENGTH) {
            applyArmLength(userCm);
            applyBoneScale(part, 1f);
            return;
        }
        if (part == BodyPart.LEG_LENGTH) {
            applyLegLength(userCm);
            applyBoneScale(part, 1f);
            return;
        }
        float w = refine
                ? refinedWeightForPartCm(part, userCm, defaultCm)
                : weightForPartCm(part, userCm, defaultCm);

        boolean usedMorph = false;
        if (modelViewer != null && !morphRenderables.isEmpty()) {
            int index = morphIndexFor(part);
            if (index >= 0 && index < morphWeights.length) {
                morphWeights[index] = w;
                applyMorphWeightsToAll();
                usedMorph = true;
            }
        }
        // Morph path keeps bones at 1. Missing morph → approximate with bone scale.
        float boneScale = usedMorph ? 1f : morphWeightToBoneScale(w);
        applyBoneScale(part, boneScale);
    }

    /** 팔길이 = 위팔길이 + 팔뚝길이. 기본 비율로 나누고, 한쪽이 막히면 나머지를 다른 쪽에 넘긴다. */
    private void applyArmLength(float totalCm) {
        float upper0 = namedBaselineCm("UPPER_ARM_LENGTH");
        float fore0 = namedBaselineCm("FOREARM_LENGTH");
        float[] upperRange = namedCmRange("UPPER_ARM_LENGTH");
        float[] foreRange = namedCmRange("FOREARM_LENGTH");
        if (Float.isNaN(upper0) || Float.isNaN(fore0) || upperRange == null || foreRange == null) {
            return;
        }
        float rest = upper0 + fore0;
        float share = rest > 1f ? upper0 / rest : 0.5f;
        float upper = clampCm(totalCm * share, upperRange);
        float fore = clampCm(totalCm - upper, foreRange);
        upper = clampCm(totalCm - fore, upperRange);
        setNamedLengthWeight(morphIndexUpperArmLength, "UPPER_ARM_LENGTH", upper, upper0);
        setNamedLengthWeight(morphIndexForearmLength, "FOREARM_LENGTH", fore, fore0);
        applyMorphWeightsToAll();
    }

    /** 다리길이 = 허벅지길이 + 종아리길이. */
    private void applyLegLength(float totalCm) {
        float thigh0 = namedBaselineCm("THIGH_LENGTH");
        float calf0 = namedBaselineCm("CALF_LENGTH");
        float[] thighRange = namedCmRange("THIGH_LENGTH");
        float[] calfRange = namedCmRange("CALF_LENGTH");
        if (Float.isNaN(thigh0) || Float.isNaN(calf0) || thighRange == null || calfRange == null) {
            return;
        }
        float rest = thigh0 + calf0;
        float share = rest > 1f ? thigh0 / rest : 0.5f;
        float thigh = clampCm(totalCm * share, thighRange);
        float calf = clampCm(totalCm - thigh, calfRange);
        thigh = clampCm(totalCm - calf, thighRange);
        setNamedLengthWeight(morphIndexThighLength, "THIGH_LENGTH", thigh, thigh0);
        setNamedLengthWeight(morphIndexCalfLength, "CALF_LENGTH", calf, calf0);
        applyMorphWeightsToAll();
    }

    private void setNamedLengthWeight(int index, String name, float userCm, float defaultCm) {
        if (index < 0 || index >= morphWeights.length) {
            return;
        }
        float w = 0f;
        if (morphCmLut.isLoaded() && morphCmLut.hasUsable(name)) {
            w = morphCmLut.refineWeight(name, userCm, defaultCm, heightCm);
        }
        morphWeights[index] = w;
    }

    private float namedBaselineCm(String name) {
        if (!morphCmLut.isLoaded()) {
            return Float.NaN;
        }
        return morphCmLut.baselineCm(name, heightCm);
    }

    private float[] namedCmRange(String name) {
        if (!morphCmLut.isLoaded() || !morphCmLut.hasUsable(name)) {
            return null;
        }
        float lo = morphCmLut.minAbsCm(name, heightCm);
        float hi = morphCmLut.maxAbsCm(name, heightCm);
        if (Float.isNaN(lo) || Float.isNaN(hi) || hi < lo) {
            return null;
        }
        return new float[]{lo, hi};
    }

    private static float clampCm(float cm, float[] range) {
        return Math.max(range[0], Math.min(range[1], cm));
    }

    /** Joint beta + residual + bone-length solve for all current partValues. */
    private void applyHybridDeformAll() {
        if (!hybridMode || morphWeights.length == 0) {
            return;
        }
        Map<String, Float> user = new HashMap<>();
        Map<String, Float> defaults = new HashMap<>();
        for (BodyPart part : BodyPart.values()) {
            float base = defaultCmFor(part);
            float value = partValues.containsKey(part) ? partValues.get(part) : base;
            user.put(part.name(), value);
            defaults.put(part.name(), base);
        }
        SmplHybridSolver.Result r = hybridSolver.solve(user, defaults);

        // Clear morphs then write betas + residuals (body fat applied separately after).
        Arrays.fill(morphWeights, 0f);
        for (int i = 0; i < betaMorphIndex.length; i++) {
            int idx = betaMorphIndex[i];
            if (idx >= 0 && idx < morphWeights.length && i < r.betas.length) {
                morphWeights[idx] = r.betas[i];
            }
        }
        for (BodyPart part : BodyPart.values()) {
            Float rw = r.residualW.get(part.name());
            if (rw == null) {
                continue;
            }
            int index = morphIndexFor(part);
            if (index >= 0 && index < morphWeights.length) {
                morphWeights[index] = rw;
            }
        }
        applyMorphWeightsToAll();

        // Preserve body-type macros on top of beta/residual.
        if (morphIndexSlim >= 0 || morphIndexHeavy >= 0) {
            if (morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
                morphWeights[morphIndexSlim] = 0f;
            }
            if (morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
                morphWeights[morphIndexHeavy] = 0f;
            }
            float strength = Math.abs(bodyFat) * 0.92f;
            if (bodyFat < 0f && morphIndexSlim >= 0 && morphIndexSlim < morphWeights.length) {
                morphWeights[morphIndexSlim] = strength;
            }
            if (bodyFat > 0f && morphIndexHeavy >= 0 && morphIndexHeavy < morphWeights.length) {
                morphWeights[morphIndexHeavy] = strength;
            }
            applyMorphWeightsToAll();
        }

        // Lengths via SMPL bone Y scale
        Map<String, String[]> lb = hybridSolver.lengthBones();
        for (BodyPart part : BodyPart.values()) {
            if (part.kind != BodyPart.Kind.LENGTH) {
                continue;
            }
            Float s = r.lengthScale.get(part.name());
            String[] bones = lb.get(part.name());
            if (s == null || bones == null || bones.length == 0) {
                continue;
            }
            applyBoneScaleByNames(bones, s, true);
        }
    }

    private void applyBoneScaleByNames(String[] names, float scale, boolean lengthAxis) {
        if (modelViewer == null || modelViewer.getAsset() == null) {
            return;
        }
        FilamentAsset asset = modelViewer.getAsset();
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        scale = Math.max(0.7f, Math.min(scale, 1.4f));
        for (String name : names) {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0) {
                continue;
            }
            int instance = tm.getInstance(entity);
            if (instance == 0) {
                continue;
            }
            float[] original = originalTransforms.get(entity);
            if (original == null) {
                original = new float[16];
                tm.getTransform(instance, original);
                originalTransforms.put(entity, original);
            }
            float[] axisScale = boneScales.get(entity);
            if (axisScale == null) {
                axisScale = new float[]{1f, 1f};
                boneScales.put(entity, axisScale);
            }
            if (lengthAxis) {
                axisScale[1] = scale;
            } else {
                axisScale[0] = scale;
            }
            float[] transform = Arrays.copyOf(original, 16);
            Matrix.scaleM(transform, 0, axisScale[0], axisScale[1], axisScale[0]);
            tm.setTransform(instance, transform);
        }
    }

    /** Map morph-style weight (~-1..1) to a mild bone scale (~0.85..1.15). */
    private static float morphWeightToBoneScale(float morphWeight) {
        return Math.max(0.85f, Math.min(1.15f, 1f + 0.15f * morphWeight));
    }

    /** Mixamo 때처럼 발 원점이 아니라, 화면 중앙(look-at)을 기준으로 키운다. */
    private void scaleAroundLookAt(float[] transform, float scale) {
        float[] around = new float[16];
        Matrix.setIdentityM(around, 0);
        Matrix.translateM(around, 0, LOOK_AT_X, LOOK_AT_Y, LOOK_AT_Z);
        Matrix.scaleM(around, 0, scale, scale, scale);
        Matrix.translateM(around, 0, -LOOK_AT_X, -LOOK_AT_Y, -LOOK_AT_Z);
        float[] result = new float[16];
        Matrix.multiplyMM(result, 0, around, 0, transform, 0);
        System.arraycopy(result, 0, transform, 0, 16);
    }

    /**
     * Mujer FBX has no textures — Filament's default lit material reads as
     * blown-out white in front and near-black from behind. Force a matte skin tint.
     */
    private void applyPreviewShading(FilamentAsset asset) {
        RenderableManager renderables = modelViewer.getEngine().getRenderableManager();
        for (int entity : asset.getEntities()) {
            int instance = renderables.getInstance(entity);
            if (instance == 0) {
                continue;
            }
            renderables.setScreenSpaceContactShadows(instance, false);
            int primCount = renderables.getPrimitiveCount(instance);
            for (int prim = 0; prim < primCount; prim++) {
                MaterialInstance mi = renderables.getMaterialInstanceAt(instance, prim);
                if (mi == null) {
                    continue;
                }
                // glTF Lit / ubershader parameter names vary slightly by provider.
                trySetFloat4(mi, "baseColorFactor", 0.82f, 0.62f, 0.52f, 1.0f);
                trySetFloat4(mi, "baseColor", 0.82f, 0.62f, 0.52f, 1.0f);
                trySetFloat(mi, "metallicFactor", 0.0f);
                trySetFloat(mi, "metallic", 0.0f);
                trySetFloat(mi, "roughnessFactor", 0.78f);
                trySetFloat(mi, "roughness", 0.78f);
                trySetFloat(mi, "reflectance", 0.35f);
            }
        }
    }

    private static void trySetFloat(MaterialInstance mi, String name, float v) {
        try {
            mi.setParameter(name, v);
        } catch (RuntimeException ignored) {
            // Parameter absent on this material.
        }
    }

    private static void trySetFloat4(
            MaterialInstance mi, String name, float x, float y, float z, float w) {
        try {
            mi.setParameter(name, x, y, z, w);
        } catch (RuntimeException ignored) {
            // Parameter absent on this material.
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setListeners() {
        avatarTouch.setOnTouchListener(this::handleAvatarTouch);

        findViewById(R.id.btnFullView).setOnClickListener(v -> showFullBody());
        findViewById(R.id.btnSaveBody).setOnClickListener(v -> onSaveBodyClicked());

        // 공통 하단 메뉴 (피팅을 누르면 뒤에 있는 피팅 시작 화면으로 돌아간다)
        BottomMenu.setup(this, BottomMenu.TAB_FITTING, this::confirmLeave);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave(() -> finish());
            }
        });
    }

    private void confirmLeave(Runnable leave) {
        View view = getLayoutInflater().inflate(R.layout.dialog_leave_confirm, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnLeave).setOnClickListener(v -> {
            dialog.dismiss();
            leave.run();
        });
        view.findViewById(R.id.btnStay).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean handleAvatarTouch(View view, MotionEvent event) {
        if (modelViewer == null || cameraManipulator == null) {
            return false;
        }

        view.getParent().requestDisallowInterceptTouchEvent(true);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragged = false;
                orbiting = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    view.requestUnbufferedDispatch(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragged && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                    dragged = true;
                    orbiting = true;
                    cameraManipulator.grabBegin(
                            (int) downX, manipulatorY(view, downY), false);
                }
                if (orbiting) {
                    cameraManipulator.grabUpdate(
                            (int) event.getX(), manipulatorY(view, event.getY()));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (orbiting) {
                    cameraManipulator.grabEnd();
                    orbiting = false;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    handleTap(event.getX(), event.getY());
                }
                return true;
            default:
                return true;
        }
    }

    private int manipulatorY(View view, float y) {
        return view.getHeight() - (int) y;
    }

    private void handleTap(float x, float y) {
        if (!modelFitted || modelViewer.getAsset() == null) {
            return;
        }

        Region region = findNearestRegion(x, y);
        if (region == null) {
            showFullBody();
            return;
        }

        selectRegion(region);
    }

    private void selectRegion(Region region) {
        selectedRegion = region;
        closeUpOnRegion(region);
        bindMeasurePanel(region);
        syncChipSelection();
        tvPartHint.setText(measureHint(region));
    }

    private void showFullBody() {
        selectedRegion = null;
        panelMeasure.setVisibility(View.GONE);
        modelViewer.setCameraFocalLength(FULL_FOCAL_LENGTH);
        restoreFittedRoot();
        syncChipSelection();
        tvPartHint.setText(R.string.avatar_hint_default);
    }

    /** 표시 cm = 이 모델 줄자. 카메라/AI Hub 값은 putPart에서 메쉬 cm로 환산. */
    private String measureHint(Region region) {
        if (region == Region.NECK) {
            return "숫자 = 모델 목 줄자 cm (입력 실측은 환산).";
        }
        if (region == Region.CHEST) {
            return "숫자 = 모델 줄자 cm. 상체길이는 목뒤–허리. 조절 시 흉곽이 같이 움직일 수 있습니다.";
        }
        if (region == Region.UPPER_ARM) {
            return "숫자 = 모델 줄자 cm. 상완·전완·팔길이(위팔+전완).";
        }
        if (region == Region.LEG) {
            return "숫자 = 모델 줄자 cm. 허벅지·종아리·다리길이(허벅지+종아리).";
        }
        StringBuilder sb = new StringBuilder("숫자 = 모델 줄자 cm (화면과 morph 동일). ");
        boolean any = false;
        for (BodyPart part : region.parts()) {
            if (part.kind == BodyPart.Kind.SIZE) {
                continue;
            }
            if (any) {
                sb.append(" / ");
            }
            any = true;
            if (morphCmLut.isLoaded()) {
                sb.append(morphCmLut.partHint(part.name(), part.label));
            } else {
                sb.append(part.label);
            }
        }
        return sb.toString();
    }

    /** 한 부위에 걸린 치수를 모두 한 카드에 쌓는다. */
    private void bindMeasurePanel(Region region) {
        tvPartName.setText(region.label);
        partRows.removeAllViews();
        activeRows.clear();

        for (BodyPart part : region.parts()) {
            View rowView = getLayoutInflater()
                    .inflate(R.layout.view_measure_row, partRows, false);
            TextView label = rowView.findViewById(R.id.tvRowLabel);
            TextView value = rowView.findViewById(R.id.tvRowValue);
            SeekBar seek = rowView.findViewById(R.id.seekRow);

            MeasureRow row = new MeasureRow(value, seek);
            float[] range = morphCmRange(part);
            float minCm = range[0];
            float maxCm = range[1];
            // 시작값을 morph 가능 구간 안으로
            float cur = Math.max(minCm, Math.min(maxCm, partValue(part)));
            partValues.put(part, cur);
            row.minTenths = Math.round(minCm * 10f);
            int maxTenths = Math.round(maxCm * 10f);

            label.setText(part.rowLabel);
            if (part.kind == BodyPart.Kind.SIZE) {
                // S/M/L discrete: progress 0/1/2
                float curSize = chestSizeIndex(partValue(part));
                partValues.put(part, curSize);
                value.setText(formatCup(curSize));
                value.setOnClickListener(v -> {
                    int next = (chestSizeIndex(partValue(part)) + 1) % 3;
                    applyPart(part, next, true);
                });
                updatingSlider = true;
                row.minTenths = 0;
                seek.setMax(2);
                seek.setProgress(Math.round(curSize));
                updatingSlider = false;
                seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (!fromUser || updatingSlider) {
                            return;
                        }
                        applyPart(part, progress, false);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                    }
                });
                partRows.addView(rowView);
                activeRows.put(part, row);
                continue;
            }

            value.setText(getString(R.string.avatar_value_cm, formatCm(cur)));
            value.setOnClickListener(v -> editPartNumber(part));

            updatingSlider = true;
            seek.setMax(Math.max(1, maxTenths - row.minTenths));
            seek.setProgress(Math.max(0, Math.round(cur * 10f) - row.minTenths));
            updatingSlider = false;

            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser || updatingSlider) {
                        return;
                    }
                    applyPart(part, (row.minTenths + progress) / 10f, false);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    refinePartAfterDrag(part);
                }
            });

            partRows.addView(rowView);
            activeRows.put(part, row);
        }

        panelMeasure.setVisibility(View.VISIBLE);
        panelMeasure.post(this::updatePanelPosition);
    }

    private void closeUpOnRegion(Region region) {
        // Keep the skinned root on the fitted pose. Translating it updates bones
        // one frame late, so the body jerks every time a part is enlarged.
        ensureFittedRoot();

        float[] world = regionAnchorWorld(region);
        closeUpOffsetX = 0f;
        closeUpOffsetY = 0f;
        closeUpOffsetZ = 0f;
        closeUpActive = world != null;
        if (world != null) {
            closeUpOffsetX = world[0] - orbitTargetX;
            closeUpOffsetY = world[1] - orbitTargetY;
            closeUpOffsetZ = world[2] - orbitTargetZ;
        }

        modelViewer.setCameraFocalLength(region.closeUpFocal);
        if (closeUpActive) {
            applyCloseUpPanelPan();
        }
        syncCameraFromManipulator();
    }

    /**
     * Same framing as the old mesh slide: subject sits left so the measure card
     * doesn't cover it. Applied to the camera, not the body.
     */
    private void applyCloseUpPanelPan() {
        float[] viewMatrix = new float[16];
        float[] projMatrix = new float[16];
        if (!fillCameraMatrices(modelViewer.getCamera(), viewMatrix, projMatrix)
                || projMatrix[0] == 0f) {
            return;
        }

        cameraManipulator.getLookAt(cameraEye, cameraTarget, cameraUp);
        float depth = (float) Math.hypot(
                Math.hypot(cameraTarget[0] - cameraEye[0], cameraTarget[1] - cameraEye[1]),
                cameraTarget[2] - cameraEye[2]);
        if (depth <= 0f) {
            return;
        }

        float ndc = (selectedRegion == Region.LEG)
                ? CLOSE_UP_NDC_SHIFT_LEG
                : CLOSE_UP_NDC_SHIFT;
        float shift = ndc * depth / projMatrix[0];
        // Old code moved the mesh by +viewX * shift. Camera moves the other way.
        closeUpOffsetX -= viewMatrix[0] * shift;
        closeUpOffsetY -= viewMatrix[4] * shift;
        closeUpOffsetZ -= viewMatrix[8] * shift;
    }

    private void ensureFittedRoot() {
        if (fittedRootTransform == null || modelViewer.getAsset() == null) {
            return;
        }
        currentRootTransform = Arrays.copyOf(fittedRootTransform, 16);
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        int root = tm.getInstance(modelViewer.getAsset().getRoot());
        float[] now = new float[16];
        tm.getTransform(root, now);
        if (!sameTransform(now, fittedRootTransform)) {
            tm.setTransform(root, fittedRootTransform);
        }
    }

    private static boolean sameTransform(float[] a, float[] b) {
        if (a == null || b == null || a.length < 16 || b.length < 16) {
            return false;
        }
        for (int i = 0; i < 16; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-5f) {
                return false;
            }
        }
        return true;
    }

    private void restoreFittedRoot() {
        ensureFittedRoot();
        closeUpActive = false;
        closeUpOffsetX = 0f;
        closeUpOffsetY = 0f;
        closeUpOffsetZ = 0f;
        if (modelViewer != null) {
            modelViewer.setCameraFocalLength(FULL_FOCAL_LENGTH);
            syncCameraFromManipulator();
        }
    }

    private void editPartNumber(BodyPart part) {
        float current = partValue(part);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(formatCm(current));
        input.setHint("cm");
        input.setSelection(input.getText().length());

        FrameLayout container = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(part.label + " 치수")
                .setMessage("정확한 수치를 입력하세요.")
                .setView(container)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (dialog, which) -> {
                    try {
                        float next = Float.parseFloat(input.getText().toString().trim());
                        float[] range = morphCmRange(part);
                        float minCm = range[0];
                        float maxCm = range[1];
                        if (next < minCm || next > maxCm) {
                            Toast.makeText(this,
                                    String.format(java.util.Locale.getDefault(),
                                            "%.1f~%.1f cm 범위로 입력해주세요.",
                                            minCm, maxCm),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        applyPart(part, next, true);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void applyPart(BodyPart part, float nextCm, boolean syncSlider) {
        float prevCm = partValue(part);
        float base = defaultCmFor(part);
        float[] range = morphCmRange(part);
        if (part.kind == BodyPart.Kind.SIZE) {
            nextCm = chestSizeIndex(nextCm);
        } else {
            nextCm = Math.max(range[0], Math.min(range[1], nextCm));
        }
        partValues.put(part, nextCm);

        applyPartDeform(part, nextCm, base, false);
        syncMeasureRow(part, nextCm, syncSlider);

        // 사용자가 직접 움직인 부위만 인접 부위에 약하게 연동 (재귀 방지)
        if (!couplingLocked && part.kind != BodyPart.Kind.SIZE) {
            applyPartCoupling(part, nextCm - prevCm);
        }
    }

    private void syncMeasureRow(BodyPart part, float nextCm, boolean syncSlider) {
        MeasureRow row = activeRows.get(part);
        if (row == null) {
            return;
        }
        if (part.kind == BodyPart.Kind.SIZE) {
            int idx = chestSizeIndex(nextCm);
            row.value.setText(formatCup(idx));
            if (syncSlider) {
                updatingSlider = true;
                row.seek.setProgress(idx);
                updatingSlider = false;
            }
            return;
        }
        row.value.setText(getString(R.string.avatar_value_cm, formatCm(nextCm)));
        if (syncSlider) {
            updatingSlider = true;
            row.seek.setProgress(Math.max(0, Math.round(nextCm * 10f) - row.minTenths));
            updatingSlider = false;
        }
    }

    /**
     * AI Hub 여성 치수 기반: driver Δcm × scale 만큼 target도 이동.
     * 예) 허벅지 +1cm → 엉덩이 +0.39cm, 종아리 +0.16cm.
     */
    private void applyPartCoupling(BodyPart driver, float deltaCm) {
        if (!partCoupling.isLoaded() || Math.abs(deltaCm) < 0.05f) {
            return;
        }
        List<PartCoupling.Link> links = partCoupling.linksFor(driver.name());
        if (links.isEmpty()) {
            return;
        }
        couplingLocked = true;
        try {
            for (PartCoupling.Link link : links) {
                BodyPart target;
                try {
                    target = BodyPart.valueOf(link.target);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                float next = partValue(target) + deltaCm * link.scale;
                float base = defaultCmFor(target);
                float[] range = morphCmRange(target);
                next = Math.max(range[0], Math.min(range[1], next));
                partValues.put(target, next);
                applyPartDeform(target, next, base, false);
                syncMeasureRow(target, next, true);
            }
        } finally {
            couplingLocked = false;
        }
    }

    private boolean applyMorphWeightCm(BodyPart part, float userCm, float defaultCm, boolean refine) {
        if (modelViewer == null || morphRenderables.isEmpty()) {
            return false;
        }
        int index = morphIndexFor(part);
        if (index < 0 || index >= morphWeights.length) {
            return false;
        }
        morphWeights[index] = refine
                ? refinedWeightForPartCm(part, userCm, defaultCm)
                : weightForPartCm(part, userCm, defaultCm);
        applyMorphWeightsToAll();
        return true;
    }

    private void refinePartAfterDrag(BodyPart part) {
        float userCm = partValue(part);
        float base = defaultCmFor(part);
        applyPartDeform(part, userCm, base, true);
    }

    private boolean applyMorphWeight(BodyPart part, float scale) {
        if (modelViewer == null || morphRenderables.isEmpty()) {
            return false;
        }
        int index = morphIndexFor(part);
        if (index < 0 || index >= morphWeights.length) {
            return false;
        }

        morphWeights[index] = morphWeightFromScale(scale);
        applyMorphWeightsToAll();
        return true;
    }

    private void applyBoneScale(BodyPart part, float scale) {
        if (modelViewer == null || modelViewer.getAsset() == null) {
            return;
        }

        FilamentAsset asset = modelViewer.getAsset();
        TransformManager tm = modelViewer.getEngine().getTransformManager();
        scale = Math.max(0.7f, Math.min(scale, 1.4f));

        for (String name : part.scaleBones) {
            int entity = asset.getFirstEntityByName(name);
            if (entity == 0) {
                continue;
            }

            int instance = tm.getInstance(entity);
            if (instance == 0) {
                continue;
            }

            float[] original = originalTransforms.get(entity);
            if (original == null) {
                original = new float[16];
                tm.getTransform(instance, original);
                originalTransforms.put(entity, original);
            }

            // 같은 뼈를 굵기와 길이가 나눠 쓰므로 축별로 따로 기억한다.
            float[] axisScale = boneScales.get(entity);
            if (axisScale == null) {
                axisScale = new float[]{1f, 1f};
                boneScales.put(entity, axisScale);
            }
            if (part.kind == BodyPart.Kind.LENGTH) {
                axisScale[1] = scale;
            } else {
                axisScale[0] = scale;
            }

            float[] transform = Arrays.copyOf(original, 16);
            Matrix.scaleM(transform, 0, axisScale[0], axisScale[1], axisScale[0]);
            tm.setTransform(instance, transform);
        }
    }

    private Region findNearestRegion(float tapX, float tapY) {
        int width = viewAvatar.getWidth();
        int height = viewAvatar.getHeight();
        if (modelViewer.getAsset() == null || width <= 0 || height <= 0) {
            return null;
        }

        float[] viewMatrix = new float[16];
        float[] projMatrix = new float[16];
        Region best = null;

        if (fillCameraMatrices(modelViewer.getCamera(), viewMatrix, projMatrix)) {
            float bestDistance = Float.MAX_VALUE;
            float bestSide = 1f;

            for (Region region : Region.values()) {
                for (float side : region.sideFraction == 0f
                        ? new float[]{1f} : new float[]{1f, -1f}) {
                    float[] screen = projectToScreen(
                            regionAnchorWorld(region, side), viewMatrix, projMatrix, width, height);
                    if (screen == null) {
                        continue;
                    }

                    float dx = screen[0] - tapX;
                    float dy = screen[1] - tapY;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = region;
                        bestSide = side;
                    }
                }
            }

            if (best != null) {
                float limit = Math.max(96f * getResources().getDisplayMetrics().density,
                        width * 0.3f);
                if (bestDistance > limit) {
                    return null;
                }
                selectedSide = bestSide;
                return best;
            }
        }

        return regionByScreenFraction(tapY / height);
    }

    private Region regionByScreenFraction(float fractionFromTop) {
        float fromBottom = 1f - Math.max(0f, Math.min(fractionFromTop, 1f));
        Region best = null;
        float bestGap = Float.MAX_VALUE;

        for (Region region : Region.values()) {
            float gap = Math.abs(region.bodyFraction - fromBottom);
            if (gap < bestGap) {
                bestGap = gap;
                best = region;
            }
        }

        return best;
    }

    private float[] regionAnchorWorld(Region region) {
        return regionAnchorWorld(region, selectedSide);
    }

    /**
     * 스킨 본의 월드 좌표는 렌더링 공간과 단위가 달라서 쓸 수 없다.
     * 실제로 그려지는 바운딩 박스를 기준으로 부위 위치를 잡는다.
     */
    private float[] regionAnchorWorld(Region region, float side) {
        FilamentAsset asset = modelViewer.getAsset();
        if (asset == null || currentRootTransform == null) {
            return null;
        }

        Box box = asset.getBoundingBox();
        float[] center = box.getCenter();
        float[] halfExtent = box.getHalfExtent();

        float[] local = {
                center[0] + side * region.sideFraction * halfExtent[0],
                center[1] - halfExtent[1] + region.bodyFraction * 2f * halfExtent[1],
                center[2],
                1f
        };

        float[] world = new float[4];
        Matrix.multiplyMV(world, 0, currentRootTransform, 0, local, 0);
        return new float[]{world[0], world[1], world[2]};
    }

    private void updatePanelPosition() {
        if (selectedRegion == null || panelMeasure.getVisibility() != View.VISIBLE) {
            return;
        }

        float[] world = regionAnchorWorld(selectedRegion);
        float[] viewMatrix = new float[16];
        float[] projMatrix = new float[16];
        if (world == null || !fillCameraMatrices(modelViewer.getCamera(), viewMatrix, projMatrix)) {
            return;
        }

        int width = avatarStage.getWidth();
        int height = avatarStage.getHeight();
        float[] screen = projectToScreen(world, viewMatrix, projMatrix, width, height);
        if (screen == null) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float margin = 16f * density;
        float panelW = panelMeasure.getWidth();
        float panelH = panelMeasure.getHeight();
        if (panelW <= 0 || panelH <= 0) {
            return;
        }

        // 부위 반대편 가장자리에 붙여야 확대된 부위를 가리지 않는다.
        float x = screen[0] < width / 2f ? width - panelW - margin : margin;

        float y;
        if (selectedRegion == Region.LEG || selectedRegion == Region.HIP) {
            // 하체는 화면 중·하단에 오므로 패널을 위에 고정해 슬라이더가 허벅지를 덮지 않게 한다.
            y = margin;
        } else {
            y = screen[1] - panelH / 2f;
            y = Math.max(8 * density, Math.min(y, height - panelH - 8 * density));
        }

        panelMeasure.setTranslationX(x);
        panelMeasure.setTranslationY(y);
    }

    private boolean fillCameraMatrices(Camera camera, float[] viewMatrix, float[] projMatrix) {
        double[] model = new double[16];
        double[] proj = new double[16];
        camera.getModelMatrix(model);
        camera.getProjectionMatrix(proj);

        float[] modelMatrix = toFloat(model);
        if (!Matrix.invertM(viewMatrix, 0, modelMatrix, 0)) {
            return false;
        }
        copy(toFloat(proj), projMatrix);
        return true;
    }

    private float[] projectToScreen(float[] world, float[] viewMatrix, float[] projMatrix,
                                    int width, int height) {
        if (world == null) {
            return null;
        }

        float[] world4 = {world[0], world[1], world[2], 1f};
        float[] viewPos = new float[4];
        float[] clip = new float[4];
        Matrix.multiplyMV(viewPos, 0, viewMatrix, 0, world4, 0);
        Matrix.multiplyMV(clip, 0, projMatrix, 0, viewPos, 0);
        if (clip[3] <= 0.0001f) {
            return null;
        }

        float ndcX = clip[0] / clip[3];
        float ndcY = clip[1] / clip[3];
        if (ndcX < -1.2f || ndcX > 1.2f || ndcY < -1.2f || ndcY > 1.2f) {
            return null;
        }

        float x = (ndcX * 0.5f + 0.5f) * width;
        float y = (1f - (ndcY * 0.5f + 0.5f)) * height;
        return new float[]{x, y, clip[2] / clip[3]};
    }

    private float[] toFloat(double[] source) {
        float[] dest = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            dest[i] = (float) source[i];
        }
        return dest;
    }

    private void copy(float[] source, float[] dest) {
        System.arraycopy(source, 0, dest, 0, source.length);
    }

    private float partValue(BodyPart part) {
        Float value = partValues.get(part);
        return value == null ? defaultCmFor(part) : value;
    }

    private String buildAvatarInfoLine() {
        String line = getString(R.string.avatar_info_height, formatCm(heightCm));
        if (sampleLabel != null && !sampleLabel.isEmpty()) {
            line += " · " + sampleLabel;
        }
        if (hybridSolver.isLoaded() && hybridMode) {
            line += " · 치수→SMPL beta";
        } else {
            line += " · 부위별 morph";
        }
        return line;
    }

    /** Demo/QA: clamp each named part into its morph range. */
    private void applyPartsJsonExtra(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            for (BodyPart part : BodyPart.values()) {
                if (!root.has(part.name())) {
                    continue;
                }
                float cm = (float) root.getDouble(part.name());
                cm = ingestSizeKoreaCm(part, cm);
                float[] range = morphCmRange(part);
                partValues.put(part, Math.max(range[0], Math.min(range[1], cm)));
            }
        } catch (Exception e) {
            android.util.Log.w("AvatarMorph", "extra_parts_json parse failed", e);
        }
    }

    /** AI Hub / 개발용 데모 체형 → 슬라이더 초기값. */
    private void applyDemoSample(String id) {
        DemoBodySamples.Sample sample = DemoBodySamples.find(id);
        if (sample == null) {
            return;
        }
        heightCm = sample.height;
        if (sampleLabel == null || sampleLabel.isEmpty()) {
            sampleLabel = sample.label;
        }
        morphCmLut.applyRealTape(populationCmRanges, heightCm);
        putPart(BodyPart.NECK, sample.neck);
        putPart(BodyPart.SHOULDER, sample.shoulder);
        putPart(BodyPart.CHEST, sample.chest);
        putPart(BodyPart.BACK_LENGTH, sample.torso);
        putPart(BodyPart.WAIST, sample.waist);
        putPart(BodyPart.UPPER_ARM, sample.upperArm);
        putPart(BodyPart.FOREARM, sample.forearm);
        putPart(BodyPart.ARM_LENGTH, sample.armLength);
        putPart(BodyPart.HIP, sample.hip);
        putPart(BodyPart.THIGH, sample.thigh);
        putPart(BodyPart.LEG_LENGTH, sample.legLength);
        putPart(BodyPart.CALF, sample.calf);
    }

    /**
     * SizeKorea / 카메라 cm → 모델 줄자 cm.
     * 화면·morph가 같은 메쉬 줄자를 쓰도록 환산한다.
     */
    private float ingestSizeKoreaCm(BodyPart part, float sizeKoreaCm) {
        if (!morphCmLut.isLoaded() || sizeKoreaCm <= 0f) {
            return sizeKoreaCm;
        }
        if (part == BodyPart.ARM_LENGTH) {
            float upper0 = namedBaselineCm("UPPER_ARM_LENGTH");
            float fore0 = namedBaselineCm("FOREARM_LENGTH");
            float rest = upper0 + fore0;
            float share = rest > 1f ? upper0 / rest : 0.5f;
            float skU = sizeKoreaCm * share;
            float skF = sizeKoreaCm - skU;
            return morphCmLut.sizeKoreaToMeshCm("UPPER_ARM_LENGTH", skU)
                    + morphCmLut.sizeKoreaToMeshCm("FOREARM_LENGTH", skF);
        }
        if (part == BodyPart.LEG_LENGTH) {
            float thigh0 = namedBaselineCm("THIGH_LENGTH");
            float calf0 = namedBaselineCm("CALF_LENGTH");
            float rest = thigh0 + calf0;
            float share = rest > 1f ? thigh0 / rest : 0.5f;
            float skT = sizeKoreaCm * share;
            float skC = sizeKoreaCm - skT;
            return morphCmLut.sizeKoreaToMeshCm("THIGH_LENGTH", skT)
                    + morphCmLut.sizeKoreaToMeshCm("CALF_LENGTH", skC);
        }
        return morphCmLut.sizeKoreaToMeshCm(part.name(), sizeKoreaCm);
    }

    private void putPart(BodyPart part, float sizeKoreaCm) {
        float meshCm = ingestSizeKoreaCm(part, sizeKoreaCm);
        float[] range = morphCmRange(part);
        partValues.put(part, Math.max(range[0], Math.min(range[1], meshCm)));
    }

    private static int chestSizeIndex(float stored) {
        int i = Math.round(stored);
        return Math.max(0, Math.min(2, i));
    }

    private static String formatCup(float stored) {
        return String.valueOf(chestSizeIndex(stored));
    }

    private String formatCm(float value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        }
        return String.format(java.util.Locale.getDefault(), "%.1f", value);
    }

    private void restoreBodyFat(float fat) {
        bodyFat = Math.max(-1f, Math.min(1f, fat));
        if (bodyFat <= -0.55f) {
            bodyType = BodyType.SLIM;
        } else if (bodyFat >= 0.33f) {
            bodyType = BodyType.HEAVY;
        } else {
            bodyType = BodyType.STANDARD;
        }
        syncBodyTypeChips();
        if (seekBodyFat != null) {
            seekBodyFat.setProgress(Math.round(bodyFat * 100f) + 100);
        }
    }

    private void onSaveBodyClicked() {
        if (!LoginSession.isLoggedIn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("로그인이 필요해요")
                    .setMessage("체형 정보는 로그인 후 내 계정에 저장할 수 있어요.")
                    .setPositiveButton("로그인하기", (d, w) ->
                            startActivity(new Intent(this, LoginMainActivity.class)))
                    .setNegativeButton("취소", null)
                    .show();
            return;
        }

        BodyProfileStore.Profile editing = BodyProfileStore.find(
                this, getIntent().getStringExtra(EXTRA_EDIT_PROFILE_ID));
        String defaultName = editing != null ? editing.name : BodyProfileStore.nextDefaultName(this);
        JSONObject parts = buildSavedParts();
        String typeLabel = bodyTypeLabel(bodyType);

        BodySaveConfirmDialog.show(this, heightCm, typeLabel, parts, defaultName,
                new BodySaveConfirmDialog.Listener() {
                    @Override
                    public void onSave(String name) {
                        BodyProfileStore.Profile saved = BodyProfileStore.save(
                                AvatarPreviewActivity.this,
                                editing == null ? null : editing.id,
                                name, heightCm, bodyFat, typeLabel, parts);
                        startActivity(BodySaveCompleteActivity.newIntent(
                                AvatarPreviewActivity.this, saved.name));
                        finish();
                    }

                    @Override
                    public void onRestartAll() {
                        Intent intent = new Intent(AvatarPreviewActivity.this, FittingActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onRetakeCamera() {
                        Intent intent = new Intent(AvatarPreviewActivity.this, BodyMeasurementActivity.class);
                        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private JSONObject buildSavedParts() {
        JSONObject parts = new JSONObject();
        for (BodyPart part : BodyPart.values()) {
            float cm = exportSizeKoreaCm(part, partValue(part));
            try {
                parts.put(part.name(), Math.round(cm * 10f) / 10d);
            } catch (Exception ignored) {
            }
        }
        return parts;
    }

    private float exportSizeKoreaCm(BodyPart part, float meshCm) {
        if (!morphCmLut.isLoaded() || meshCm <= 0f) {
            return meshCm;
        }
        if (part == BodyPart.ARM_LENGTH) {
            return lengthToSizeKoreaCm(meshCm, "UPPER_ARM_LENGTH", "FOREARM_LENGTH");
        }
        if (part == BodyPart.LEG_LENGTH) {
            return lengthToSizeKoreaCm(meshCm, "THIGH_LENGTH", "CALF_LENGTH");
        }
        return morphCmLut.meshToSizeKoreaCm(part.name(), meshCm);
    }

    private float lengthToSizeKoreaCm(float meshCm, String upperName, String lowerName) {
        float upper0 = namedBaselineCm(upperName);
        float lower0 = namedBaselineCm(lowerName);
        float rest = upper0 + lower0;
        float share = rest > 1f ? upper0 / rest : 0.5f;
        float scale = share * morphCmLut.sizeKoreaToMeshCm(upperName, 1f)
                + (1f - share) * morphCmLut.sizeKoreaToMeshCm(lowerName, 1f);
        return scale > 1e-6f ? meshCm / scale : meshCm;
    }

    @Override
    protected void onResume() {
        super.onResume();
        choreographer.postFrameCallback(frameCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        choreographer.removeFrameCallback(frameCallback);
        if (orbiting && cameraManipulator != null) {
            cameraManipulator.grabEnd();
            orbiting = false;
        }
    }

}
