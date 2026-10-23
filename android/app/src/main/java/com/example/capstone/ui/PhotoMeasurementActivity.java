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
import com.example.capstone.camera.MeasurementSession;
import com.example.capstone.camera.SilhouetteBuilder;
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


                        // ---------------------------------
                        // URI 읽기 권한 유지
                        // ---------------------------------

                        try {

                            getContentResolver()
                                    .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    );

                        } catch (Exception ignored) {

                        }


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


                        // ---------------------------------
                        // URI 읽기 권한 유지
                        // ---------------------------------

                        try {

                            getContentResolver()
                                    .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    );

                        } catch (Exception ignored) {

                        }


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

                        frontSilhouette = SilhouetteBuilder.build(mask);

                        if (frontSilhouette == null) {
                            analysisError(
                                    "정면 사진에서 신체 외곽선을 인식하지 못했습니다."
                            );
                            return;
                        }

                        textFrontStatus.setText("✓ 정면 신체 분석 완료");
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
                    .addOnSuccessListener(mask -> {

                        MeasurementSession.Silhouette sideSilhouette =
                                SilhouetteBuilder.build(mask);

                        if (sideSilhouette == null) {
                            analysisError(
                                    "옆면 사진에서 신체 외곽선을 인식하지 못했습니다."
                            );
                            return;
                        }

                        textSideStatus.setText("✓ 옆면 신체 분석 완료");
                        textPhotoStatus.setText(
                                "측정 위치를 확인해주세요.");

                        // 가슴·허리·골반 둘레는 측정 위치를 확인·조정한 뒤 계산한다
                        // (카메라 촬영 흐름과 같은 화면)
                        MeasurementSession.clear();
                        MeasurementSession.heightCm = userHeightCm;
                        MeasurementSession.front = frontSilhouette;
                        MeasurementSession.side = sideSilhouette;
                        MeasurementSession.frontLengths = basicResult;
                        MeasurementSession.frontUri = frontPhotoUri;
                        MeasurementSession.sideUri = sidePhotoUri;

                        btnAnalyzePhotos.setEnabled(true);

                        Intent intent = new Intent(
                                PhotoMeasurementActivity.this,
                                MeasurementAdjustActivity.class
                        );
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e ->
                            analysisError(
                                    "옆면 사진 분석 오류: " + e.getMessage()
                            )
                    );

        } catch (IOException e) {
            analysisError("옆면 사진을 불러올 수 없습니다.");
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


        if (poseDetector != null) {

            poseDetector.close();
        }


        if (segmenter != null) {

            segmenter.close();
        }
    }
}