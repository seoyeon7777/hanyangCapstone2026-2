package com.example.capstone.camera;

/** 사람 한 명의 관절 33개 (MediaPipe Pose Landmarker 결과). */
public class Pose {

    private final PoseLandmark[] landmarks;
    /** 좌표(픽셀)를 얻은 이미지 크기. 모르면 0 */
    private final int imageWidth;
    private final int imageHeight;

    public Pose(PoseLandmark[] landmarks) {
        this(landmarks, 0, 0);
    }

    public Pose(PoseLandmark[] landmarks, int imageWidth, int imageHeight) {
        this.landmarks = landmarks;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    /** 없는 번호면 null (ML Kit와 같은 동작). */
    public PoseLandmark getPoseLandmark(int type) {
        if (type < 0 || type >= landmarks.length) {
            return null;
        }
        return landmarks[type];
    }
}
