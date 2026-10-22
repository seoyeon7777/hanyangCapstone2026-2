
package com.example.capstone.camera;

import android.content.Context;
import android.graphics.PointF;

import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.io.Closeable;

public class MediaPipePoseDetector implements Closeable {

    private final com.google.mlkit.vision.pose.PoseDetector detector;

    public MediaPipePoseDetector(Context context, boolean stream) {

        PoseDetectorOptions options =
                new PoseDetectorOptions.Builder()
                        .setDetectorMode(
                                stream
                                        ? PoseDetectorOptions.STREAM_MODE
                                        : PoseDetectorOptions.SINGLE_IMAGE_MODE
                        )
                        .build();

        detector = PoseDetection.getClient(options);
    }

    // 촬영한 사진 분석
    public Task<Pose> process(InputImage image) {
        return detector.process(image)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw new Exception(task.getException());
                    }

                    return convertPose(task.getResult());
                });
    }

    // 실시간 카메라 관절 인식
    public Task<Pose> process(ImageProxy proxy) {

        if (proxy.getImage() == null) {
            return Tasks.forException(
                    new IllegalArgumentException(
                            "카메라 이미지를 가져올 수 없습니다."
                    )
            );
        }

        InputImage image = InputImage.fromMediaImage(
                proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees()
        );

        return process(image);
    }

    // ML Kit 관절 좌표를 기존 MediaPipe 번호로 변환
    private Pose convertPose(
            com.google.mlkit.vision.pose.Pose mlPose
    ) {

        PoseLandmark[] landmarks = new PoseLandmark[33];

        // 얼굴
        map(mlPose, landmarks, 0, 0);
        map(mlPose, landmarks, 2, 2);
        map(mlPose, landmarks, 5, 5);
        map(mlPose, landmarks, 7, 7);
        map(mlPose, landmarks, 8, 8);

        // 어깨
        map(mlPose, landmarks, 11, 11);
        map(mlPose, landmarks, 12, 12);

        // 팔꿈치
        map(mlPose, landmarks, 13, 13);
        map(mlPose, landmarks, 14, 14);

        // 손목
        map(mlPose, landmarks, 15, 15);
        map(mlPose, landmarks, 16, 16);

        // 손가락
        map(mlPose, landmarks, 17, 17);
        map(mlPose, landmarks, 18, 18);
        map(mlPose, landmarks, 19, 19);
        map(mlPose, landmarks, 20, 20);
        map(mlPose, landmarks, 21, 21);
        map(mlPose, landmarks, 22, 22);

        // 골반
        map(mlPose, landmarks, 23, 23);
        map(mlPose, landmarks, 24, 24);

        // 무릎
        map(mlPose, landmarks, 25, 25);
        map(mlPose, landmarks, 26, 26);

        // 발목
        map(mlPose, landmarks, 27, 27);
        map(mlPose, landmarks, 28, 28);

        // 발뒤꿈치
        map(mlPose, landmarks, 29, 29);
        map(mlPose, landmarks, 30, 30);

        // 발끝
        map(mlPose, landmarks, 31, 31);
        map(mlPose, landmarks, 32, 32);

        boolean found = false;

        for (PoseLandmark landmark : landmarks) {
            if (landmark != null) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalStateException(
                    "사람의 관절을 인식하지 못했습니다."
            );
        }

        return new Pose(landmarks);
    }

    private void map(
            com.google.mlkit.vision.pose.Pose source,
            PoseLandmark[] target,
            int targetIndex,
            int sourceIndex
    ) {

        com.google.mlkit.vision.pose.PoseLandmark landmark =
                source.getPoseLandmark(sourceIndex);

        if (landmark == null) {
            return;
        }

        PointF point = landmark.getPosition();

        target[targetIndex] = new PoseLandmark(
                targetIndex,
                new PointF(point.x, point.y),
                landmark.getInFrameLikelihood()
        );
    }

    @Override
    public void close() {
        detector.close();
    }
}
