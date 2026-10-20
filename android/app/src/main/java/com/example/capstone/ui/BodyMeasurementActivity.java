package com.example.capstone.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.capstone.R;
import com.example.capstone.camera.BodyGuideOverlayView;
import com.example.capstone.camera.BodyMeasurementCalculator;
import com.example.capstone.camera.CircumferenceCalculator;
import com.example.capstone.camera.PoseOverlayView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = ExperimentalGetImage.class)
public class BodyMeasurementActivity extends AppCompatActivity {

    private PreviewView previewView;
    private PoseOverlayView poseOverlay;
    private BodyGuideOverlayView bodyGuideOverlay;
    private TextView textHeight;
    private TextView textStatus;

    private PoseDetector poseDetector;
    private Segmenter segmenter;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private ProcessCameraProvider cameraProvider;


    private volatile boolean isProcessing = false;
    private volatile boolean isCapturing = false;
    private volatile boolean photoAccepted = false;
    private volatile boolean isSwitchingMode = false;

    private float userHeightCm;

    private int captureMode = BodyGuideOverlayView.MODE_FRONT;

    private File frontPhotoFile;
    private File sidePhotoFile;

    private BodyMeasurementCalculator.Result frontResult;
    private SilhouetteWidths frontWidths;
    private SilhouetteWidths sideWidths;

    // 자세 안정화 및 자동 촬영
    private long stableStartTime = 0L;
    private long countdownStartTime = 0L;
    private long lastAlignedTime = 0L;

    private static final long STABLE_DURATION_MS = 700L;
    private static final long COUNTDOWN_MS = 3000L;
    private static final long GRACE_DURATION_MS = 650L;

    // 화면 크기를 기준으로 한 허용 오차
    private static final float TOLERANCE_X = 0.16f;
    private static final float TOLERANCE_Y = 0.13f;

    private static final float MIN_LIKELIHOOD = 0.40f;

    private static final int FRONT_REQUIRED_MATCHES = 5;
    private static final int SIDE_REQUIRED_MATCHES = 3;

    private static class SilhouetteWidths {
        float chest;
        float waist;
        float hip;

        SilhouetteWidths(float chest, float waist, float hip) {
            this.chest = chest;
            this.waist = waist;
            this.hip = hip;
        }

        boolean isValid() {
            return chest > 0f && waist > 0f && hip > 0f;
        }
    }

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCamera();
                        } else {
                            textStatus.setText("카메라 권한이 필요합니다.");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_body_measurement);

        previewView = findViewById(R.id.previewView);
        poseOverlay = findViewById(R.id.poseOverlay);
        bodyGuideOverlay = findViewById(R.id.bodyGuideOverlay);
        textHeight = findViewById(R.id.textHeight);
        textStatus = findViewById(R.id.textStatus);

        Button btnCloseCamera = findViewById(R.id.btnCloseCamera);

        userHeightCm = getIntent().getFloatExtra(
                "USER_HEIGHT_CM", 0f
        );

        textHeight.setText("입력 키: " + userHeightCm + " cm");

        bodyGuideOverlay.setMode(BodyGuideOverlayView.MODE_FRONT);

        textStatus.setText(
                "정면: 전신이 화면에 보이도록 서주세요."
        );

        btnCloseCamera.setOnClickListener(v -> finish());


        Button btnSwitchCamera = findViewById(R.id.btnSwitchCamera);

        btnSwitchCamera.setOnClickListener(v -> {
            if (isCapturing || photoAccepted || isSwitchingMode) {
                return;
            }

            if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT;
            } else {
                cameraFacing = CameraSelector.LENS_FACING_BACK;
            }

            stableStartTime = 0L;
            countdownStartTime = 0L;
            lastAlignedTime = 0L;

            textStatus.setText("카메라를 전환하고 있습니다...");
            startCamera();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();

        PoseDetectorOptions poseOptions =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                        .build();

        poseDetector = PoseDetection.getClient(poseOptions);

        SelfieSegmenterOptions segmentOptions =
                new SelfieSegmenterOptions.Builder()
                        .setDetectorMode(
                                SelfieSegmenterOptions.SINGLE_IMAGE_MODE
                        )
                        .build();

        segmenter = Segmentation.getClient(segmentOptions);

        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                cameraProvider = provider;


                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                ImageAnalysis analysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                )
                                .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                imageCapture =
                        new ImageCapture.Builder()
                                .setCaptureMode(
                                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                )
                                .build();

                provider.unbindAll();

                provider.bindToLifecycle(
                        this,
                        new CameraSelector.Builder()
                                .requireLensFacing(cameraFacing)
                                .build(),
                        preview,
                        analysis,
                        imageCapture
                );

            } catch (Exception e) {
                e.printStackTrace();
                textStatus.setText("카메라 실행 오류: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {

        if (isProcessing || isCapturing ||
                photoAccepted || isSwitchingMode) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;

        try {
            int rotationDegrees =
                    imageProxy.getImageInfo().getRotationDegrees();

            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    rotationDegrees
            );

            int width = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            if (rotationDegrees == 90 || rotationDegrees == 270) {
                int temp = width;
                width = height;
                height = temp;
            }

            final int imageWidth = width;
            final int imageHeight = height;

            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {
                        if (isCapturing || photoAccepted || isSwitchingMode) {
                            return;
                        }

                        poseOverlay.setPose(
                                pose,
                                imageWidth,
                                imageHeight
                        );

                        checkPoseStability(
                                pose,
                                imageWidth,
                                imageHeight
                        );
                    })
                    .addOnFailureListener(e -> {
                        poseOverlay.clear();
                        handleTemporaryMisalignment(
                                "전신 인식을 확인하고 있어요."
                        );
                    })
                    .addOnCompleteListener(task -> {
                        isProcessing = false;
                        imageProxy.close();
                    });

        } catch (Exception e) {
            isProcessing = false;
            imageProxy.close();
        }
    }

    // =========================================
    // 자세 안정화 및 자동 촬영
    // =========================================

    private void checkPoseStability(
            Pose pose,
            int imageWidth,
            int imageHeight
    ) {

        if (isCapturing || photoAccepted || isSwitchingMode) {
            return;
        }

        boolean aligned = isPoseAligned(
                pose,
                imageWidth,
                imageHeight
        );

        if (!aligned) {
            handleTemporaryMisalignment(
                    captureMode == BodyGuideOverlayView.MODE_FRONT
                            ? "정면: 어깨와 골반을 가이드에 맞춰주세요."
                            : "측면: 옆으로 서서 전신을 보여주세요."
            );
            return;
        }

        long now = SystemClock.elapsedRealtime();
        lastAlignedTime = now;

        if (stableStartTime == 0L) {
            stableStartTime = now;
        }

        if (now - stableStartTime < STABLE_DURATION_MS) {
            textStatus.setText("자세 인식 중... 잠시 유지해주세요.");
            return;
        }

        if (countdownStartTime == 0L) {
            countdownStartTime = now;
        }

        long elapsed = now - countdownStartTime;

        if (elapsed >= COUNTDOWN_MS) {
            textStatus.setText("자동 촬영 중...");
            capturePhoto();
            return;
        }

        int secondsLeft = (int) Math.ceil(
                (COUNTDOWN_MS - elapsed) / 1000.0
        );

        textStatus.setText(
                "자세 인식 완료! " + secondsLeft + "초 후 촬영"
        );
    }

    private void handleTemporaryMisalignment(String message) {
        long now = SystemClock.elapsedRealtime();

        if (lastAlignedTime != 0L &&
                now - lastAlignedTime <= GRACE_DURATION_MS) {

            if (countdownStartTime != 0L) {
                textStatus.setText(
                        "자세를 조금만 더 유지해주세요."
                );
            }
            return;
        }

        resetCountdown();
        textStatus.setText(message);
    }

    // =========================================
    // 주요 관절 기반 정렬 판단
    // =========================================

    private boolean isPoseAligned(
            Pose pose,
            int imageWidth,
            int imageHeight
    ) {

        if (pose == null || imageWidth <= 0 || imageHeight <= 0) {
            return false;
        }

        int viewWidth = bodyGuideOverlay.getWidth();
        int viewHeight = bodyGuideOverlay.getHeight();

        if (viewWidth <= 0 || viewHeight <= 0) {
            return false;
        }

        // 손목과 팔꿈치는 사람마다 자세 차이가 크므로
        // 자동 촬영 필수 조건에서 제외한다.
        int[] requiredLandmarks;

        int requiredMatches;

        if (captureMode == BodyGuideOverlayView.MODE_SIDE) {

            requiredLandmarks = new int[]{
                    PoseLandmark.NOSE,
                    PoseLandmark.LEFT_SHOULDER,
                    PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.LEFT_KNEE,
                    PoseLandmark.RIGHT_KNEE,
                    PoseLandmark.LEFT_ANKLE,
                    PoseLandmark.RIGHT_ANKLE
            };

            requiredMatches = SIDE_REQUIRED_MATCHES;

        } else {

            requiredLandmarks = new int[]{
                    PoseLandmark.NOSE,
                    PoseLandmark.LEFT_SHOULDER,
                    PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.LEFT_KNEE,
                    PoseLandmark.RIGHT_KNEE,
                    PoseLandmark.LEFT_ANKLE,
                    PoseLandmark.RIGHT_ANKLE
            };

            requiredMatches = FRONT_REQUIRED_MATCHES;
        }

        float scale = Math.max(
                (float) viewWidth / imageWidth,
                (float) viewHeight / imageHeight
        );

        float offsetX =
                (viewWidth - imageWidth * scale) / 2f;

        float offsetY =
                (viewHeight - imageHeight * scale) / 2f;

        float toleranceX = viewWidth * TOLERANCE_X;
        float toleranceY = viewHeight * TOLERANCE_Y;

        int detectedCount = 0;
        int matchedCount = 0;

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int type : requiredLandmarks) {

            PoseLandmark landmark = pose.getPoseLandmark(type);

            if (landmark == null ||
                    landmark.getInFrameLikelihood() < MIN_LIKELIHOOD) {
                continue;
            }

            float actualX =
                    landmark.getPosition().x * scale + offsetX;

            float actualY =
                    landmark.getPosition().y * scale + offsetY;

            detectedCount++;

            minX = Math.min(minX, actualX);
            maxX = Math.max(maxX, actualX);
            minY = Math.min(minY, actualY);
            maxY = Math.max(maxY, actualY);

            PointF guidePoint = bodyGuideOverlay.getGuidePoint(type);

            // 측면에서는 양쪽 관절이 겹치므로
            // 왼쪽 가이드 좌표를 공통 기준으로 사용
            if (captureMode == BodyGuideOverlayView.MODE_SIDE) {
                int guideType = type;

                if (type == PoseLandmark.RIGHT_SHOULDER) {
                    guideType = PoseLandmark.LEFT_SHOULDER;
                } else if (type == PoseLandmark.RIGHT_HIP) {
                    guideType = PoseLandmark.LEFT_HIP;
                } else if (type == PoseLandmark.RIGHT_KNEE) {
                    guideType = PoseLandmark.LEFT_KNEE;
                } else if (type == PoseLandmark.RIGHT_ANKLE) {
                    guideType = PoseLandmark.LEFT_ANKLE;
                }

                guidePoint = bodyGuideOverlay.getGuidePoint(guideType);
            }

            if (guidePoint == null) {
                continue;
            }

            float differenceX =
                    Math.abs(actualX - guidePoint.x);

            float differenceY =
                    Math.abs(actualY - guidePoint.y);

            if (differenceX <= toleranceX &&
                    differenceY <= toleranceY) {
                matchedCount++;
            }
        }

        if (detectedCount < requiredMatches) {
            return false;
        }

        if (matchedCount < requiredMatches) {
            return false;
        }

        // 화면에 신체가 너무 작게 잡히거나
        // 한 지점에 몰려 인식되는 경우 방지
        float bodyHeight = maxY - minY;

        return bodyHeight >= viewHeight * 0.48f;
    }

    private void resetCountdown() {
        stableStartTime = 0L;
        countdownStartTime = 0L;
        lastAlignedTime = 0L;
    }

    // =========================================
    // 사진 자동 촬영
    // =========================================

    private void capturePhoto() {

        if (isCapturing || imageCapture == null) {
            return;
        }

        isCapturing = true;

        String prefix =
                captureMode == BodyGuideOverlayView.MODE_FRONT
                        ? "body_front_"
                        : "body_side_";

        File photoFile = new File(
                getCacheDir(),
                prefix + System.currentTimeMillis() + ".jpg"
        );

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(
                        photoFile
                ).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults output
                    ) {
                        showPhotoReview(photoFile);
                    }

                    @Override
                    public void onError(
                            @NonNull androidx.camera.core.ImageCaptureException e
                    ) {
                        isCapturing = false;
                        resetCountdown();

                        textStatus.setText(
                                "촬영 실패. 다시 시도해주세요."
                        );
                    }
                }
        );
    }

    // =========================================
    // 촬영 사진 확인
    // =========================================

    private void showPhotoReview(File photoFile) {

        Bitmap bitmap = BitmapFactory.decodeFile(
                photoFile.getAbsolutePath()
        );

        if (bitmap == null) {
            isCapturing = false;
            resetCountdown();
            textStatus.setText("사진을 불러오지 못했습니다.");
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setPadding(24, 12, 24, 12);

        String title =
                captureMode == BodyGuideOverlayView.MODE_FRONT
                        ? "정면 사진 확인"
                        : "측면 사진 확인";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(imageView)
                .setCancelable(false)
                .setNegativeButton("다시 찍기", (dialog, which) -> {
                    photoFile.delete();

                    isCapturing = false;
                    photoAccepted = false;

                    resetCountdown();
                    poseOverlay.clear();

                    textStatus.setText(
                            "자세를 다시 맞춰주세요."
                    );
                })
                .setPositiveButton("사진 사용하기", (dialog, which) -> {
                    photoAccepted = true;

                    if (captureMode ==
                            BodyGuideOverlayView.MODE_FRONT) {

                        frontPhotoFile = photoFile;
                        processFrontPhoto();

                    } else {

                        sidePhotoFile = photoFile;
                        processSidePhoto();
                    }
                })
                .show();
    }

    // =========================================
    // 정면 사진 분석
    // =========================================

    private void processFrontPhoto() {

        textStatus.setText("정면 사진 분석 중...");

        try {
            InputImage image = InputImage.fromFilePath(
                    this,
                    Uri.fromFile(frontPhotoFile)
            );

            poseDetector.process(image)
                    .addOnSuccessListener(pose -> {

                        frontResult =
                                BodyMeasurementCalculator.calculate(
                                        pose,
                                        userHeightCm
                                );

                        if (frontResult == null) {
                            handleAnalysisFailure(
                                    "정면 관절 분석 실패. 다시 촬영해주세요."
                            );
                            return;
                        }

                        analyzeSilhouette(image, true);
                    })
                    .addOnFailureListener(e ->
                            handleAnalysisFailure(
                                    "정면 관절 분석에 실패했습니다."
                            )
                    );

        } catch (Exception e) {
            handleAnalysisFailure(
                    "정면 사진을 읽을 수 없습니다."
            );
        }
    }

    // =========================================
    // 측면 사진 분석
    // =========================================

    private void processSidePhoto() {

        textStatus.setText("측면 사진 분석 중...");

        try {
            InputImage image = InputImage.fromFilePath(
                    this,
                    Uri.fromFile(sidePhotoFile)
            );

            analyzeSilhouette(image, false);

        } catch (Exception e) {
            handleAnalysisFailure(
                    "측면 사진을 읽을 수 없습니다."
            );
        }
    }

    // =========================================
    // 신체 윤곽 분석
    // =========================================

    private void analyzeSilhouette(
            InputImage image,
            boolean isFront
    ) {

        segmenter.process(image)
                .addOnSuccessListener(mask -> {

                    SilhouetteWidths widths =
                            measureSilhouette(mask);

                    if (widths == null || !widths.isValid()) {
                        handleAnalysisFailure(
                                "신체 윤곽 분석 실패. 다시 촬영해주세요."
                        );
                        return;
                    }

                    if (isFront) {
                        frontWidths = widths;
                        switchToSideCapture();
                    } else {
                        sideWidths = widths;
                        calculateCircumferences();
                    }
                })
                .addOnFailureListener(e ->
                        handleAnalysisFailure(
                                "신체 윤곽 분석에 실패했습니다."
                        )
                );
    }

    private SilhouetteWidths measureSilhouette(
            SegmentationMask mask
    ) {

        if (mask == null || userHeightCm <= 0f) {
            return null;
        }

        int width = mask.getWidth();
        int height = mask.getHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        ByteBuffer buffer = mask.getBuffer();
        buffer.order(ByteOrder.nativeOrder());
        buffer.rewind();

        int top = -1;
        int bottom = -1;

        for (int y = 0; y < height; y++) {

            boolean hasPerson = false;

            for (int x = 0; x < width; x++) {

                if (confidenceAt(buffer, width, x, y) >= 0.5f) {
                    hasPerson = true;
                    break;
                }
            }

            if (hasPerson) {
                if (top == -1) {
                    top = y;
                }
                bottom = y;
            }
        }

        if (top < 0 || bottom <= top) {
            return null;
        }

        float bodyHeightPixels = bottom - top;
        float cmPerPixel = userHeightCm / bodyHeightPixels;

        int chestY =
                top + Math.round(bodyHeightPixels * 0.29f);

        int waistY =
                top + Math.round(bodyHeightPixels * 0.43f);

        int hipY =
                top + Math.round(bodyHeightPixels * 0.52f);

        float chestPixels = measureCentralBodyWidth(
                buffer, width, height, chestY
        );

        float waistPixels = measureCentralBodyWidth(
                buffer, width, height, waistY
        );

        float hipPixels = measureCentralBodyWidth(
                buffer, width, height, hipY
        );

        if (chestPixels <= 0 ||
                waistPixels <= 0 ||
                hipPixels <= 0) {
            return null;
        }

        return new SilhouetteWidths(
                chestPixels * cmPerPixel,
                waistPixels * cmPerPixel,
                hipPixels * cmPerPixel
        );
    }

    private float confidenceAt(
            ByteBuffer buffer,
            int width,
            int x,
            int y
    ) {

        return buffer.getFloat(
                (y * width + x) * 4
        );
    }

    private float measureCentralBodyWidth(
            ByteBuffer buffer,
            int width,
            int height,
            int y
    ) {

        if (y < 0 || y >= height) {
            return 0f;
        }

        int bestStart = -1;
        int bestEnd = -1;
        int currentStart = -1;

        float centerX = width / 2f;
        float bestDistance = Float.MAX_VALUE;

        for (int x = 0; x <= width; x++) {

            boolean isPerson =
                    x < width &&
                            confidenceAt(buffer, width, x, y) >= 0.5f;

            if (isPerson && currentStart == -1) {
                currentStart = x;
            }

            if (!isPerson && currentStart != -1) {

                int end = x - 1;
                float segmentCenter = (currentStart + end) / 2f;
                float distance = Math.abs(segmentCenter - centerX);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestStart = currentStart;
                    bestEnd = end;
                }

                currentStart = -1;
            }
        }

        if (bestStart == -1 || bestEnd == -1) {
            return 0f;
        }

        return bestEnd - bestStart + 1;
    }

    // =========================================
    // 정면 촬영 후 측면 촬영으로 전환
    // =========================================

    private void switchToSideCapture() {

        isSwitchingMode = true;

        captureMode = BodyGuideOverlayView.MODE_SIDE;

        bodyGuideOverlay.setMode(
                BodyGuideOverlayView.MODE_SIDE
        );

        poseOverlay.clear();
        resetCountdown();

        new AlertDialog.Builder(this)
                .setTitle("정면 촬영 완료")
                .setMessage(
                        "정면 사진 분석이 완료되었습니다.\n\n"
                                + "이제 오른쪽을 바라보도록 몸을 돌려주세요.\n"
                                + "측면 자세가 인식되면 자동 촬영됩니다."
                )
                .setCancelable(false)
                .setPositiveButton(
                        "측면 촬영 시작",
                        (dialog, which) -> {

                            isCapturing = false;
                            photoAccepted = false;
                            isSwitchingMode = false;

                            resetCountdown();

                            textStatus.setText(
                                    "측면: 옆으로 서서 전신을 보여주세요."
                            );
                        }
                )
                .show();
    }

    // =========================================
    // 가슴, 허리, 골반 둘레 계산
    // =========================================

    private void calculateCircumferences() {

        if (frontWidths == null ||
                sideWidths == null ||
                frontResult == null) {

            handleAnalysisFailure("측정 데이터가 부족합니다.");
            return;
        }

        float chestCircumference =
                CircumferenceCalculator.calculateEllipseCircumference(
                        frontWidths.chest,
                        sideWidths.chest
                );

        float waistCircumference =
                CircumferenceCalculator.calculateEllipseCircumference(
                        frontWidths.waist,
                        sideWidths.waist
                );

        float hipCircumference =
                CircumferenceCalculator.calculateEllipseCircumference(
                        frontWidths.hip,
                        sideWidths.hip
                );

        if (chestCircumference <= 0f ||
                waistCircumference <= 0f ||
                hipCircumference <= 0f) {

            handleAnalysisFailure("둘레 계산에 실패했습니다.");
            return;
        }

        openMeasurementResult(
                chestCircumference,
                waistCircumference,
                hipCircumference
        );
    }

    // =========================================
    // 측정 결과 화면으로 이동
    // =========================================

    private void openMeasurementResult(
            float chest,
            float waist,
            float hip
    ) {

        Intent intent = new Intent(
                BodyMeasurementActivity.this,
                MeasurementResultActivity.class
        );

        intent.putExtra("HEIGHT", userHeightCm);
        intent.putExtra("SHOULDER", frontResult.shoulderWidth);

        intent.putExtra(
                "LEFT_ARM",
                frontResult.leftArmLength
        );

        intent.putExtra(
                "RIGHT_ARM",
                frontResult.rightArmLength
        );

        intent.putExtra(
                "TORSO",
                frontResult.torsoLength
        );

        intent.putExtra(
                "LEFT_LEG",
                frontResult.leftLegLength
        );

        intent.putExtra(
                "RIGHT_LEG",
                frontResult.rightLegLength
        );

        intent.putExtra("CHEST_CIRCUMFERENCE", chest);
        intent.putExtra("WAIST_CIRCUMFERENCE", waist);
        intent.putExtra("HIP_CIRCUMFERENCE", hip);

        intent.putExtra("CHEST_WIDTH", frontWidths.chest);
        intent.putExtra("CHEST_DEPTH", sideWidths.chest);

        intent.putExtra("WAIST_WIDTH", frontWidths.waist);
        intent.putExtra("WAIST_DEPTH", sideWidths.waist);

        intent.putExtra("HIP_WIDTH", frontWidths.hip);
        intent.putExtra("HIP_DEPTH", sideWidths.hip);

        if (frontPhotoFile != null) {
            intent.putExtra(
                    "FRONT_PHOTO_PATH",
                    frontPhotoFile.getAbsolutePath()
            );
        }

        if (sidePhotoFile != null) {
            intent.putExtra(
                    "SIDE_PHOTO_PATH",
                    sidePhotoFile.getAbsolutePath()
            );
        }

        startActivity(intent);
        finish();
    }

    // =========================================
    // 분석 실패 처리
    // =========================================

    private void handleAnalysisFailure(String message) {

        photoAccepted = false;
        isCapturing = false;
        isSwitchingMode = false;

        resetCountdown();
        poseOverlay.clear();

        textStatus.setText(message);

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (poseDetector != null) {
            poseDetector.close();
        }

        if (segmenter != null) {
            segmenter.close();
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
