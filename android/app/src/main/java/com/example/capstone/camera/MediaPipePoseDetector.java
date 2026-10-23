package com.example.capstone.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;

import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mlkit.vision.common.InputImage;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MediaPipe Pose Landmarker 래퍼. ML Kit PoseDetector.process()처럼 Task&lt;Pose&gt;를 돌려준다.
 * 모델 파일: assets/models/pose_landmarker_full.task
 *
 * - 사진 모드(stream=false): 파일에서 읽은 사진 한 장씩
 * - 카메라 모드(stream=true): 프레임마다. 타임스탬프가 늘어나야 해서 VIDEO 모드를 쓴다
 * 한 줄 실행기로만 돌려서 스레드 문제가 없다.
 */
public class MediaPipePoseDetector implements Closeable {

    public static final String MODEL_ASSET = "models/pose_landmarker_full.task";

    private final PoseLandmarker landmarker;
    private final boolean stream;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long lastTimestampMs = 0L;

    public MediaPipePoseDetector(Context context, boolean stream) {
        this.stream = stream;

        PoseLandmarker.PoseLandmarkerOptions options =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(
                                BaseOptions.builder()
                                        .setModelAssetPath(MODEL_ASSET)
                                        .build())
                        .setRunningMode(stream ? RunningMode.VIDEO : RunningMode.IMAGE)
                        .setNumPoses(1)
                        .setMinPoseDetectionConfidence(0.3f)
                        .setMinPosePresenceConfidence(0.3f)
                        .setMinTrackingConfidence(0.3f)
                        .build();

        landmarker = PoseLandmarker.createFromOptions(
                context.getApplicationContext(), options);
    }

    /** 파일에서 읽은 사진 (InputImage.fromFilePath 결과). */
    public Task<Pose> process(InputImage image) {
        Bitmap bitmap = image.getBitmapInternal();
        if (bitmap == null) {
            return Tasks.forException(
                    new IllegalArgumentException("비트맵 이미지가 아닙니다."));
        }
        return process(bitmap, image.getRotationDegrees(), 0L);
    }

    /** 카메라 프레임. ImageProxy 닫기는 호출한 쪽이 한다. */
    public Task<Pose> process(ImageProxy proxy) {
        Bitmap bitmap = proxy.toBitmap();
        int rotation = proxy.getImageInfo().getRotationDegrees();
        long timestampMs = proxy.getImageInfo().getTimestamp() / 1_000_000L;
        return process(bitmap, rotation, timestampMs);
    }

    private Task<Pose> process(Bitmap bitmap, int rotationDegrees, long timestampMs) {
        return Tasks.call(executor, () -> {
            // MediaPipe의 회전 옵션은 ML Kit와 방향 해석이 달라 골격이 누워서 나왔다.
            // 비트맵을 직접 시계 방향으로 돌려 똑바로 세운 뒤 넘긴다 (옵션 회전 없음)
            Bitmap upright = bitmap;
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                upright = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        matrix, true);
            }
            MPImage mpImage = new BitmapImageBuilder(upright).build();
            ImageProcessingOptions processing =
                    ImageProcessingOptions.builder().build();

            PoseLandmarkerResult result;
            if (stream) {
                // VIDEO 모드는 시간이 반드시 증가해야 한다
                lastTimestampMs = Math.max(timestampMs, lastTimestampMs + 1);
                result = landmarker.detectForVideo(
                        mpImage, processing, lastTimestampMs);
            } else {
                result = landmarker.detect(mpImage, processing);
            }

            if (result.landmarks().isEmpty()) {
                android.util.Log.d("MediaPipePose", "사람 없음 " + bitmap.getWidth() + "x" + bitmap.getHeight() + " rot=" + rotationDegrees);
                throw new IllegalStateException("사람을 찾지 못했습니다.");
            }

            // 회전 반영 뒤의 이미지 크기 (좌표는 0~1로 나오므로 픽셀로 바꾼다)
            float w = upright.getWidth();
            float h = upright.getHeight();

            List<NormalizedLandmark> list = result.landmarks().get(0);
            PoseLandmark[] out = new PoseLandmark[list.size()];
            for (int i = 0; i < out.length; i++) {
                NormalizedLandmark l = list.get(i);
                // ML Kit의 inFrameLikelihood(화면 안에 있을 확률)에 가장 가까운 값은 presence다.
                // visibility는 옷·가림 때문에 낮게 나와서 기존 기준(0.4~0.5)으로 거르면 대부분 탈락한다
                float presence = l.presence().orElse(l.visibility().orElse(1f));
                out[i] = new PoseLandmark(
                        i, new PointF(l.x() * w, l.y() * h), presence);
            }
            return new Pose(out);
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        landmarker.close();
    }
}
