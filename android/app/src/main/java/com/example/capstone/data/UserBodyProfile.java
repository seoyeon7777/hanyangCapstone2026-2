package com.example.capstone.data;

/**
 * =========================
 * 사용자 신체 정보 보관소
 * =========================
 *
 * 체형 측정(카메라 / 사진) 결과를 앱 내에서 공유하기 위한 임시 저장소.
 *
 * 지금은 메모리에만 들고 있으므로 앱을 종료하면 사라진다.
 * 나중에 users / body_measurements API 또는 SharedPreferences로 교체하면 된다.
 */
public class UserBodyProfile {

    // =========================
    // 싱글톤
    // =========================

    private static final UserBodyProfile instance =
            new UserBodyProfile();

    public static UserBodyProfile getInstance() {
        return instance;
    }

    private UserBodyProfile() {
    }


    // =========================
    // 신체 치수 (cm)
    //
    // 측정값이 없으면 -1
    // =========================

    private float heightCm = -1f;           // 키
    private float shoulderWidth = -1f;      // 어깨너비
    private float chestCircumference = -1f; // 가슴둘레
    private float waistCircumference = -1f; // 허리둘레
    private float hipCircumference = -1f;   // 골반(엉덩이)둘레
    private float thighCircumference = -1f; // 허벅지둘레


    // =========================
    // 측정 정보가 있는지 확인
    //
    // 사이즈 비교에 실제로 쓰이는 값이
    // 하나라도 있으면 true
    // =========================

    public boolean hasMeasurement() {

        return chestCircumference > 0
                || waistCircumference > 0
                || hipCircumference > 0
                || shoulderWidth > 0;
    }


    // =========================
    // 측정 결과 저장
    //
    // MeasurementResultActivity에서 호출
    // 값이 없는 부위는 -1로 넘기면 저장하지 않는다
    // =========================

    public void updateFromMeasurement(
            float heightCm,
            float shoulderWidth,
            float chestCircumference,
            float waistCircumference,
            float hipCircumference,
            float thighCircumference
    ) {

        if (heightCm > 0) {
            this.heightCm = heightCm;
        }

        if (shoulderWidth > 0) {
            this.shoulderWidth = shoulderWidth;
        }

        if (chestCircumference > 0) {
            this.chestCircumference = chestCircumference;
        }

        if (waistCircumference > 0) {
            this.waistCircumference = waistCircumference;
        }

        if (hipCircumference > 0) {
            this.hipCircumference = hipCircumference;
        }

        if (thighCircumference > 0) {
            this.thighCircumference = thighCircumference;
        }
    }


    // =========================
    // 측정 정보 초기화
    // =========================

    public void clear() {

        heightCm = -1f;
        shoulderWidth = -1f;
        chestCircumference = -1f;
        waistCircumference = -1f;
        hipCircumference = -1f;
        thighCircumference = -1f;
    }


    // =========================
    // 개발 중 팝업 확인용 더미 데이터
    //
    // 체형 측정 화면을 거치지 않고
    // 사이즈 경고 팝업을 바로 확인하고 싶을 때 호출.
    //
    // 실제 측정이 연결되면 이 메서드는 지워도 된다.
    // =========================

    public void setDemoProfile() {

        heightCm = 178f;
        shoulderWidth = 47.5f;
        chestCircumference = 104f;
        waistCircumference = 88f;
        hipCircumference = 104f;
        thighCircumference = 58f;
    }


    // =========================
    // Getter / Setter
    // =========================

    public float getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(float heightCm) {
        this.heightCm = heightCm;
    }


    public float getShoulderWidth() {
        return shoulderWidth;
    }

    public void setShoulderWidth(float shoulderWidth) {
        this.shoulderWidth = shoulderWidth;
    }


    public float getChestCircumference() {
        return chestCircumference;
    }

    public void setChestCircumference(float chestCircumference) {
        this.chestCircumference = chestCircumference;
    }


    public float getWaistCircumference() {
        return waistCircumference;
    }

    public void setWaistCircumference(float waistCircumference) {
        this.waistCircumference = waistCircumference;
    }


    public float getHipCircumference() {
        return hipCircumference;
    }

    public void setHipCircumference(float hipCircumference) {
        this.hipCircumference = hipCircumference;
    }


    public float getThighCircumference() {
        return thighCircumference;
    }

    public void setThighCircumference(float thighCircumference) {
        this.thighCircumference = thighCircumference;
    }
}
