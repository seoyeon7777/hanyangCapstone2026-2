package com.example.capstone.camera;

/** 사람 한 명의 관절 33개 (MediaPipe Pose Landmarker 결과). */
public class Pose {

    private final PoseLandmark[] landmarks;

    public Pose(PoseLandmark[] landmarks) {
        this.landmarks = landmarks;
    }

    /** 없는 번호면 null (ML Kit와 같은 동작). */
    public PoseLandmark getPoseLandmark(int type) {
        if (type < 0 || type >= landmarks.length) {
            return null;
        }
        return landmarks[type];
    }
}
