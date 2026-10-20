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
import com.example.capstone.camera.BodySilhouetteAnalyzer;
import com.example.capstone.camera.CircumferenceCalculator;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.google.mlkit.vision.common.InputImage;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

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

    private PoseDetector poseDetector;
    private Segmenter segmenter;


    // =====================================================
    // 사용자 키
    // =====================================================

    private float userHeightCm = 0f;


    // =====================================================
    // 정면 분석 결과 임시 저장
    // =====================================================

    private BodyMeasurementCalculator.Result basicResult;

    private BodySilhouetteAnalyzer.Result
            frontSilhouetteResult;


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

        PoseDetectorOptions poseOptions =
                new PoseDetectorOptions
                        .Builder()

                        .setDetectorMode(
                                PoseDetectorOptions
                                        .SINGLE_IMAGE_MODE
                        )

                        .build();


        poseDetector =
                PoseDetection.getClient(
                        poseOptions
                );


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
    // 정면 사진 분석
    // =====================================================

    private void analyzeFrontPhoto() {

        try {

            InputImage image =
                    InputImage.fromFilePath(
                            this,
                            frontPhotoUri
                    );


            // Pose
            Task<Pose> poseTask =
                    poseDetector.process(
                            image
                    );


            // Segmentation
            Task<SegmentationMask> maskTask =
                    segmenter.process(
                            image
                    );


            Tasks.whenAllSuccess(
                            poseTask,
                            maskTask
                    )

                    .addOnSuccessListener(
                            results -> {


                                // =========================
                                // Pose 결과
                                // =========================

                                Pose pose =
                                        (Pose)
                                                results.get(0);


                                // =========================
                                // Segmentation 결과
                                // =========================

                                SegmentationMask mask =
                                        (SegmentationMask)
                                                results.get(1);


                                // =========================
                                // 기본 신체 길이 계산
                                // =========================

                                basicResult =
                                        BodyMeasurementCalculator
                                                .calculate(
                                                        pose,
                                                        userHeightCm
                                                );


                                if (basicResult == null) {

                                    analysisError(
                                            "정면 사진에서 전신을 정확하게 인식하지 못했습니다."
                                    );

                                    return;
                                }


                                // =========================
                                // 정면 실루엣 분석
                                // =========================

                                frontSilhouetteResult =
                                        BodySilhouetteAnalyzer
                                                .analyze(
                                                        pose,
                                                        mask,
                                                        userHeightCm
                                                );


                                if (frontSilhouetteResult
                                        == null) {

                                    analysisError(
                                            "정면 사진에서 신체 외곽선을 인식하지 못했습니다."
                                    );

                                    return;
                                }


                                textFrontStatus.setText(
                                        "✓ 정면 신체 분석 완료"
                                );


                                textPhotoStatus.setText(
                                        "옆면 사진을 분석하고 있습니다..."
                                );


                                // 다음 단계
                                analyzeSidePhoto();
                            }
                    )

                    .addOnFailureListener(
                            e -> {

                                analysisError(
                                        "정면 사진 분석 오류: "
                                                + e.getMessage()
                                );
                            }
                    );


        } catch (IOException e) {

            analysisError(
                    "정면 사진을 불러올 수 없습니다."
            );
        }
    }


    // =====================================================
    // STEP 2
    // 옆면 사진 분석
    // =====================================================

    private void analyzeSidePhoto() {

        try {

            InputImage image =
                    InputImage.fromFilePath(
                            this,
                            sidePhotoUri
                    );


            // Pose
            Task<Pose> poseTask =
                    poseDetector.process(
                            image
                    );


            // Segmentation
            Task<SegmentationMask> maskTask =
                    segmenter.process(
                            image
                    );


            Tasks.whenAllSuccess(
                            poseTask,
                            maskTask
                    )

                    .addOnSuccessListener(
                            results -> {


                                // =========================
                                // Pose
                                // =========================

                                Pose sidePose =
                                        (Pose)
                                                results.get(0);


                                // =========================
                                // Segmentation
                                // =========================

                                SegmentationMask
                                        sideMask =

                                        (SegmentationMask)
                                                results.get(1);


                                // =========================
                                // 옆면 실루엣
                                // =========================

                                BodySilhouetteAnalyzer.Result
                                        sideResult =

                                        BodySilhouetteAnalyzer
                                                .analyze(
                                                        sidePose,
                                                        sideMask,
                                                        userHeightCm
                                                );


                                if (sideResult == null) {

                                    analysisError(
                                            "옆면 사진에서 신체 외곽선을 인식하지 못했습니다."
                                    );

                                    return;
                                }


                                textSideStatus.setText(
                                        "✓ 옆면 신체 분석 완료"
                                );


                                // =========================
                                // 둘레 계산
                                // =========================

                                CircumferenceCalculator.Result
                                        circumference =

                                        CircumferenceCalculator
                                                .calculate(


                                                        // 가슴
                                                        frontSilhouetteResult.chest,
                                                        sideResult.chest,


                                                        // 허리
                                                        frontSilhouetteResult.waist,
                                                        sideResult.waist,


                                                        // 골반
                                                        frontSilhouetteResult.hip,
                                                        sideResult.hip,


                                                        // 목
                                                        frontSilhouetteResult.neck,
                                                        sideResult.neck,


                                                        // 왼쪽 상완
                                                        frontSilhouetteResult.leftUpperArm,
                                                        sideResult.leftUpperArm,


                                                        // 오른쪽 상완
                                                        frontSilhouetteResult.rightUpperArm,
                                                        sideResult.rightUpperArm,


                                                        // 왼쪽 전완
                                                        frontSilhouetteResult.leftForearm,
                                                        sideResult.leftForearm,


                                                        // 오른쪽 전완
                                                        frontSilhouetteResult.rightForearm,
                                                        sideResult.rightForearm,


                                                        // 왼쪽 허벅지
                                                        frontSilhouetteResult.leftThigh,
                                                        sideResult.leftThigh,


                                                        // 오른쪽 허벅지
                                                        frontSilhouetteResult.rightThigh,
                                                        sideResult.rightThigh,


                                                        // 왼쪽 종아리
                                                        frontSilhouetteResult.leftCalf,
                                                        sideResult.leftCalf,


                                                        // 오른쪽 종아리
                                                        frontSilhouetteResult.rightCalf,
                                                        sideResult.rightCalf
                                                );


                                textPhotoStatus.setText(
                                        "신체 분석이 완료되었습니다."
                                );


                                // 결과 화면
                                openResult(
                                        basicResult,
                                        circumference
                                );
                            }
                    )

                    .addOnFailureListener(
                            e -> {

                                analysisError(
                                        "옆면 사진 분석 오류: "
                                                + e.getMessage()
                                );
                            }
                    );


        } catch (IOException e) {

            analysisError(
                    "옆면 사진을 불러올 수 없습니다."
            );
        }
    }


    // =====================================================
    // 결과 화면 열기
    // =====================================================

    private void openResult(

            BodyMeasurementCalculator.Result basic,

            CircumferenceCalculator.Result
                    circumference
    ) {


        Intent intent =
                new Intent(
                        PhotoMeasurementActivity.this,
                        MeasurementResultActivity.class
                );


        // =================================================
        // 키
        // =================================================

        intent.putExtra(
                "HEIGHT",
                userHeightCm
        );


        // =================================================
        // 어깨
        // =================================================

        intent.putExtra(
                "SHOULDER",
                basic.shoulderWidth
        );


        // =================================================
        // 팔 길이
        // =================================================

        float armLength =
                (
                        basic.leftArmLength
                                +
                                basic.rightArmLength
                ) / 2f;


        intent.putExtra(
                "ARM_LENGTH",
                armLength
        );


        // 기존 호환용

        intent.putExtra(
                "LEFT_ARM",
                basic.leftArmLength
        );

        intent.putExtra(
                "RIGHT_ARM",
                basic.rightArmLength
        );


        // =================================================
        // 상체 길이
        // =================================================

        intent.putExtra(
                "TORSO",
                basic.torsoLength
        );


        // =================================================
        // 다리 길이
        // =================================================

        float legLength =
                (
                        basic.leftLegLength
                                +
                                basic.rightLegLength
                ) / 2f;


        intent.putExtra(
                "LEG_LENGTH",
                legLength
        );


        // 기존 호환용

        intent.putExtra(
                "LEFT_LEG",
                basic.leftLegLength
        );

        intent.putExtra(
                "RIGHT_LEG",
                basic.rightLegLength
        );


        // =================================================
        // 가슴둘레
        // =================================================

        intent.putExtra(
                "CHEST_CIRCUMFERENCE",
                circumference
                        .chestCircumference
        );


        // =================================================
        // 허리둘레
        // =================================================

        intent.putExtra(
                "WAIST_CIRCUMFERENCE",
                circumference
                        .waistCircumference
        );


        // =================================================
        // 골반둘레
        // =================================================

        intent.putExtra(
                "HIP_CIRCUMFERENCE",
                circumference
                        .hipCircumference
        );


        // =================================================
        // 목둘레
        // =================================================

        intent.putExtra(
                "NECK_CIRCUMFERENCE",
                circumference
                        .neckCircumference
        );


        // =================================================
        // 상완둘레
        // =================================================

        float upperArmCircumference =
                (
                        circumference
                                .leftUpperArmCircumference

                                +

                                circumference
                                        .rightUpperArmCircumference
                ) / 2f;


        intent.putExtra(
                "UPPER_ARM_CIRCUMFERENCE",
                upperArmCircumference
        );


        // =================================================
        // 전완둘레
        // =================================================

        float forearmCircumference =
                (
                        circumference
                                .leftForearmCircumference

                                +

                                circumference
                                        .rightForearmCircumference
                ) / 2f;


        intent.putExtra(
                "FOREARM_CIRCUMFERENCE",
                forearmCircumference
        );


        // =================================================
        // 허벅지둘레
        // =================================================

        float thighCircumference =
                (
                        circumference
                                .leftThighCircumference

                                +

                                circumference
                                        .rightThighCircumference
                ) / 2f;


        intent.putExtra(
                "THIGH_CIRCUMFERENCE",
                thighCircumference
        );


        // =================================================
        // 종아리둘레
        // =================================================

        float calfCircumference =
                (
                        circumference
                                .leftCalfCircumference

                                +

                                circumference
                                        .rightCalfCircumference
                ) / 2f;


        intent.putExtra(
                "CALF_CIRCUMFERENCE",
                calfCircumference
        );


        // =================================================
        // ★ 사용자가 선택한 실제 정면 사진
        // =================================================

        if (frontPhotoUri != null) {

            intent.putExtra(
                    "FRONT_PHOTO_URI",
                    frontPhotoUri.toString()
            );
        }


        // =================================================
        // ★ 사용자가 선택한 실제 옆면 사진
        // =================================================

        if (sidePhotoUri != null) {

            intent.putExtra(
                    "SIDE_PHOTO_URI",
                    sidePhotoUri.toString()
            );
        }


        // =================================================
        // ★ 결과 Activity에서도 사진을 읽을 수 있도록 권한 전달
        // =================================================

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );


        // =================================================
        // 결과 화면 실행
        // =================================================

        startActivity(intent);
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