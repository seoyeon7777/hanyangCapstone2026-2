package com.example.capstone.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.camera.BodyMeasurementCalculator;
import com.example.capstone.camera.BodyShapeFeatures;
import com.example.capstone.camera.MeasurementSession;
import com.example.capstone.camera.SilhouetteBuilder;
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyPrior;
import com.example.capstone.camera.MediaPipePoseDetector;
import com.example.capstone.camera.Pose;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.google.mlkit.vision.common.InputImage;


import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.io.IOException;


public class PhotoMeasurementActivity extends AppCompatActivity {

    /** 설문만 한 체형 카드 id. 주면 측정 결과를 그 카드에 채운다 ('체형분석' 탭 카드의 "사진 분석 이어서 하기") */
    public static final String EXTRA_CONTINUE_PROFILE_ID = "continue_profile_id";

    public static Intent continueIntent(android.content.Context context, String profileId, float heightCm) {
        return new Intent(context, PhotoMeasurementActivity.class)
                .putExtra(EXTRA_CONTINUE_PROFILE_ID, profileId)
                .putExtra("USER_HEIGHT_CM", heightCm);
    }


    // =====================================================
    // 화면
    // =====================================================

    private ImageView imageFrontPreview;
    private ImageView imageSidePreview;

    private TextView textFrontStatus;
    private TextView textSideStatus;
    private TextView textPhotoStatus;
    private TextView textPhotoHeight;

    private Button btnSelectFrontPhoto;
    private Button btnSelectSidePhoto;
    private Button btnAnalyzePhotos;


    // =====================================================
    // 선택한 사진
    // =====================================================

    private Uri frontPhotoUri = null;
    private Uri sidePhotoUri = null;


    // =====================================================
    // ML Kit
    // =====================================================

    private MediaPipePoseDetector poseDetector;
    private Segmenter segmenter;


    // =====================================================
    // 사용자 키
    // =====================================================

    private float userHeightCm = 0f;


    // =====================================================
    // 정면 분석 결과 임시 저장
    // =====================================================

    private BodyMeasurementCalculator.Result basicResult;

    private MeasurementSession.Silhouette frontSilhouette;
    private com.example.capstone.camera.Pose frontPose;
    private com.example.capstone.camera.Pose sidePose;


    // =====================================================
    // 정면 사진 선택
    // =====================================================

    private final ActivityResultLauncher<String[]>
            frontPhotoLauncher =

            registerForActivityResult(

                    new ActivityResultContracts.OpenDocument(),

                    uri -> {

                        if (uri == null) {
                            return;
                        }


                        // 사진 읽기 권한을 오래 붙들지 않는다 (이 화면이 열려 있는 동안만 읽는다)


                        // ---------------------------------
                        // URI 저장
                        // ---------------------------------

                        frontPhotoUri = uri;


                        // ---------------------------------
                        // 미리보기
                        // ---------------------------------

                        imageFrontPreview.setImageURI(
                                frontPhotoUri
                        );


                        textFrontStatus.setText(
                                "✓ 정면 사진 선택 완료"
                        );


                        updateAnalyzeButton();
                    }
            );


    // =====================================================
    // 옆면 사진 선택
    // =====================================================

    private final ActivityResultLauncher<String[]>
            sidePhotoLauncher =

            registerForActivityResult(

                    new ActivityResultContracts.OpenDocument(),

                    uri -> {

                        if (uri == null) {
                            return;
                        }


                        // 사진 읽기 권한을 오래 붙들지 않는다 (이 화면이 열려 있는 동안만 읽는다)


                        // ---------------------------------
                        // URI 저장
                        // ---------------------------------

                        sidePhotoUri = uri;


                        // ---------------------------------
                        // 미리보기
                        // ---------------------------------

                        imageSidePreview.setImageURI(
                                sidePhotoUri
                        );


                        textSideStatus.setText(
                                "✓ 옆면 사진 선택 완료"
                        );


                        updateAnalyzeButton();
                    }
            );


    // =====================================================
    // onCreate
    // =====================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_photo_measurement
        );


        // =================================================
        // View 연결
        // =================================================

        imageFrontPreview =
                findViewById(
                        R.id.imageFrontPreview
                );

        imageSidePreview =
                findViewById(
                        R.id.imageSidePreview
                );


        textFrontStatus =
                findViewById(
                        R.id.textFrontStatus
                );

        textSideStatus =
                findViewById(
                        R.id.textSideStatus
                );

        textPhotoStatus =
                findViewById(
                        R.id.textPhotoStatus
                );

        textPhotoHeight =
                findViewById(
                        R.id.textPhotoHeight
                );


        btnSelectFrontPhoto =
                findViewById(
                        R.id.btnSelectFrontPhoto
                );

        btnSelectSidePhoto =
                findViewById(
                        R.id.btnSelectSidePhoto
                );

        btnAnalyzePhotos =
                findViewById(
                        R.id.btnAnalyzePhotos
                );

        // 설문만 한 체형에 사진 분석을 이어서 하는지 (없으면 새 체형)
        MeasurementSession.continueProfileId =
                getIntent().getStringExtra(EXTRA_CONTINUE_PROFILE_ID);

        // 나가기 (전에는 화면에 나가는 버튼이 없어 시스템 뒤로가기로만 나갈 수 있었다)
        findViewById(R.id.btnPhotoBack)
                .setOnClickListener(v -> finish());


        // =================================================
        // 키 받아오기
        // =================================================

        userHeightCm =
                getIntent().getFloatExtra(
                        "USER_HEIGHT_CM",
                        0f
                );


        textPhotoHeight.setText(
                String.format(
                        "입력 키: %.1f cm",
                        userHeightCm
                )
        );


        // =================================================
        // Pose Detector
        // =================================================

        poseDetector = new MediaPipePoseDetector(this, false);


        // =================================================
        // Selfie Segmentation
        // =================================================

        SelfieSegmenterOptions
                segmentationOptions =

                new SelfieSegmenterOptions
                        .Builder()

                        .setDetectorMode(
                                SelfieSegmenterOptions
                                        .SINGLE_IMAGE_MODE
                        )

                        .build();


        segmenter =
                Segmentation.getClient(
                        segmentationOptions
                );


        // =================================================
        // 정면 사진 선택 버튼
        // =================================================

        btnSelectFrontPhoto
                .setOnClickListener(v -> {

                    frontPhotoLauncher.launch(
                            new String[]{
                                    "image/*"
                            }
                    );
                });


        // =================================================
        // 옆면 사진 선택 버튼
        // =================================================

        btnSelectSidePhoto
                .setOnClickListener(v -> {

                    sidePhotoLauncher.launch(
                            new String[]{
                                    "image/*"
                            }
                    );
                });


        // =================================================
        // 분석 버튼
        // =================================================

        btnAnalyzePhotos
                .setOnClickListener(v -> {

                    if (frontPhotoUri == null
                            ||
                            sidePhotoUri == null) {

                        Toast.makeText(
                                this,
                                "정면과 옆면 사진을 모두 선택해주세요.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }


                    btnAnalyzePhotos
                            .setEnabled(false);


                    textPhotoStatus.setText(
                            "정면 사진을 분석하고 있습니다..."
                    );


                    AnalysisTimer.start();
                    analyzeFrontPhoto();
                });


        updateAnalyzeButton();
    }


    // =====================================================
    // 분석 버튼 활성화
    // =====================================================

    private void updateAnalyzeButton() {

        boolean ready =
                frontPhotoUri != null
                        &&
                        sidePhotoUri != null;


        btnAnalyzePhotos.setEnabled(
                ready
        );


        if (ready) {

            textPhotoStatus.setText(
                    "사진 준비 완료 · 신체 분석을 시작해주세요."
            );

        } else {

            textPhotoStatus.setText(
                    "정면과 옆면 사진을 모두 선택해주세요."
            );
        }
    }


    // =====================================================
    // STEP 1
    // 정면 사진: 관절(길이) + 몸 윤곽
    // =====================================================

    private void analyzeFrontPhoto() {

        try {

            InputImage image =
                    InputImage.fromFilePath(this, frontPhotoUri);

            Task<Pose> poseTask = poseDetector.process(image);
            Task<SegmentationMask> maskTask = segmenter.process(image);

            Tasks.whenAllSuccess(poseTask, maskTask)
                    .addOnSuccessListener(results -> {

                        Pose pose = (Pose) results.get(0);
                        frontPose = pose;
                        SegmentationMask mask =
                                (SegmentationMask) results.get(1);

                        basicResult =
                                BodyMeasurementCalculator.calculate(
                                        pose, userHeightCm);

                        if (basicResult == null) {
                            analysisError(
                                    "정면 사진에서 전신을 정확하게 인식하지 못했습니다."
                            );
                            return;
                        }

                        frontSilhouette = SilhouetteBuilder.build(mask, BodyShapeFeatures.bodyAxis(pose));

                        if (frontSilhouette == null) {
                            analysisError(
                                    "정면 사진에서 신체 외곽선을 인식하지 못했습니다."
                            );
                            return;
                        }

                        textFrontStatus.setText("✓ 정면 신체 분석 완료");
                        AnalysisTimer.mark("정면 관절·윤곽");
                        textPhotoStatus.setText(
                                "옆면 사진을 분석하고 있습니다...");

                        analyzeSidePhoto();
                    })
                    .addOnFailureListener(e ->
                            analysisError(
                                    "정면 사진 분석 오류: " + e.getMessage()
                            )
                    );

        } catch (IOException e) {
            analysisError("정면 사진을 불러올 수 없습니다.");
        }
    }

    // =====================================================
    // STEP 2
    // 옆면 사진: 몸 윤곽만 (두께를 재는 용도)
    // =====================================================

    private void analyzeSidePhoto() {

        try {

            InputImage image =
                    InputImage.fromFilePath(this, sidePhotoUri);

            segmenter.process(image)
                    .addOnSuccessListener(mask ->
                            // 옆면도 관절로 몸의 중심선을 구해 몸 덩어리를 고른다 (실패하면 화면 가운데 기준)
                            poseDetector.process(image)
                                    .addOnCompleteListener(poseTask -> {
                                        sidePose = poseTask.isSuccessful() ? poseTask.getResult() : null;
                                        finishSide(
                                                mask,
                                                poseTask.isSuccessful()
                                                        ? BodyShapeFeatures.bodyAxis(poseTask.getResult())
                                                        : null);
                                    }))
                    .addOnFailureListener(e ->
                            analysisError(
                                    "옆면 사진 분석 오류: " + e.getMessage()
                            )
                    );

        } catch (IOException e) {
            analysisError("옆면 사진을 불러올 수 없습니다.");
        }
    }

    /** 옆면 윤곽을 만들고 측정 위치 확인 화면으로 간다 */
    private void finishSide(SegmentationMask mask, SilhouetteBuilder.Axis axis) {

        MeasurementSession.Silhouette sideSilhouette =
                SilhouetteBuilder.build(mask, axis);

        if (sideSilhouette == null) {
            analysisError("옆면 사진에서 신체 외곽선을 인식하지 못했습니다.");
            return;
        }

        textSideStatus.setText("✓ 옆면 신체 분석 완료");
        AnalysisTimer.mark("옆면 관절·윤곽");
        // 가슴·허리·골반 둘레: 사진 모델(딥러닝)이 구한 값을 쓰고, 못 구하면 기본 위치(몸 높이의 29/43/52%)로 계산한다
        MeasurementSession.clear();
        MeasurementSession.heightCm = userHeightCm;
        MeasurementSession.front = frontSilhouette;
        MeasurementSession.side = sideSilhouette;
        MeasurementSession.frontLengths = basicResult;

        btnAnalyzePhotos.setEnabled(true);

        // 성인이고 키·몸무게·나이가 있으면 사진 모델을 돌린다 (몇 초 걸린다)
        final float weightKg = BodyInput.weight(this);
        final float age = BodyInput.age(this);
        final BodyPrior.Result prior = BodyPrior.estimate(userHeightCm, weightKg, age);
        if (prior != null && prior.adult && frontPose != null && sidePose != null) {
            textPhotoStatus.setText("사진을 분석하고 있어요. 잠시만 기다려 주세요.");
            btnAnalyzePhotos.setEnabled(false);
            BodyPhotoPipeline.run(this, segmenter, frontPhotoUri, frontPose, sidePhotoUri, sidePose,
                    userHeightCm, weightKg, age, prior, circ -> {
                        btnAnalyzePhotos.setEnabled(true);
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        MeasurementSession.modelCirc = circ;
                        AnalysisTimer.mark("모델 단계 전체(앱으로 돌아옴)");
                        openResultOrWarn();
                    });
            return;
        }
        textPhotoStatus.setText("분석이 끝났어요. 결과를 불러옵니다.");
        openResultOrWarn();
    }

    /** 정면·옆면 몸 비율이 이상하면 확인을 받고, 아니면 바로 결과 화면으로 간다 */
    private void openResultOrWarn() {
        String warning = MeasurementResultLauncher.shapeWarning();
        if (warning == null) {
            openResultAndRelease();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("치수가 이상할 수 있어요")
                .setMessage(warning + "\n그래도 계산할까요?")
                .setPositiveButton("그래도 계산", (d, w) -> openResultAndRelease())
                .setNegativeButton("사진 다시 고르기", null)
                .show();
    }

    /** 결과 화면을 열고, 분석이 끝났으니 사진을 바로 지운다 */
    private void openResultAndRelease() {
        if (MeasurementResultLauncher.open(this)) {
            releasePhotos();
        }
    }

    /**
     * 사진은 기기 안에서만 분석하고 서버로 보내지 않는다. 분석이 끝나면 앱이 들고 있던 사진을 모두 놓는다:
     * 고른 사진 주소, 미리보기, 사진에서 얻은 관절·윤곽, 앨범 읽기 권한. (앨범 속 원본 사진은 그대로 둔다)
     */
    private void releasePhotos() {
        frontPhotoUri = null;
        sidePhotoUri = null;
        frontPose = null;
        sidePose = null;
        frontSilhouette = null;
        basicResult = null;
        if (imageFrontPreview != null) {
            imageFrontPreview.setImageDrawable(null);
        }
        if (imageSidePreview != null) {
            imageSidePreview.setImageDrawable(null);
        }
        MeasurementSession.clear();

        // 예전 버전이 붙들어 둔 앨범 읽기 권한도 풀어 준다
        try {
            for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                getContentResolver().releasePersistableUriPermission(
                        permission.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception ignored) {
        }

        if (textFrontStatus != null) {
            textFrontStatus.setText("정면 사진을 선택해주세요.");
        }
        if (textSideStatus != null) {
            textSideStatus.setText("옆면 사진을 선택해주세요.");
        }
        if (btnAnalyzePhotos != null) {
            btnAnalyzePhotos.setEnabled(false);
        }
        if (textPhotoStatus != null) {
            textPhotoStatus.setText("사진은 분석이 끝나는 대로 지워졌어요. 다시 분석하려면 사진을 새로 골라 주세요.");
        }
    }

    // =====================================================
    // 오류
    // =====================================================

    private void analysisError(
            String message
    ) {

        textPhotoStatus.setText(
                message
        );


        btnAnalyzePhotos.setEnabled(
                true
        );


        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }


    // =====================================================
    // 종료
    // =====================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        // 화면을 닫을 때도 사진을 들고 있지 않는다
        MeasurementSession.clear();
        frontPhotoUri = null;
        sidePhotoUri = null;
        frontPose = null;
        sidePose = null;
        frontSilhouette = null;
        basicResult = null;


        if (poseDetector != null) {

            poseDetector.close();
        }


        if (segmenter != null) {

            segmenter.close();
        }
    }
}