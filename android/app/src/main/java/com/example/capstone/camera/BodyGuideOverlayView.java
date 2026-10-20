
package com.example.capstone.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.HashMap;
import java.util.Map;

public class BodyGuideOverlayView extends View {

    public static final int MODE_FRONT = 0;
    public static final int MODE_SIDE = 1;

    private int mode = MODE_FRONT;

    private static final float BASE_WIDTH = 300f;
    private static final float BASE_HEIGHT = 600f;

    private final Paint linePaint = new Paint(3);
    private final Paint pointPaint = new Paint(3);
    private final Paint framePaint = new Paint(3);

    private final Map<Integer, PointF> guidePoints =
            new HashMap<>();

    public BodyGuideOverlayView(Context context) {
        super(context);
        init();
    }

    public BodyGuideOverlayView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    private void init() {

        linePaint.setColor(0xAA00E5A0);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        pointPaint.setColor(0xFF00E5A0);
        pointPaint.setStyle(Paint.Style.FILL);

        framePaint.setColor(0x8800E5A0);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(3f);

        setMode(MODE_FRONT);
    }

    public void setMode(int newMode) {

        mode = newMode;
        guidePoints.clear();

        if (mode == MODE_SIDE) {
            createSideGuide();
        } else {
            createFrontGuide();
        }

        invalidate();
    }

    public int getMode() {
        return mode;
    }

    private void addPoint(int type, float x, float y) {
        guidePoints.put(type, new PointF(x, y));
    }

    private void createFrontGuide() {

        addPoint(PoseLandmark.NOSE, 150, 115);

        addPoint(PoseLandmark.LEFT_SHOULDER, 110, 170);
        addPoint(PoseLandmark.RIGHT_SHOULDER, 190, 170);

        addPoint(PoseLandmark.LEFT_ELBOW, 85, 265);
        addPoint(PoseLandmark.RIGHT_ELBOW, 215, 265);

        addPoint(PoseLandmark.LEFT_WRIST, 70, 360);
        addPoint(PoseLandmark.RIGHT_WRIST, 230, 360);

        addPoint(PoseLandmark.LEFT_HIP, 125, 340);
        addPoint(PoseLandmark.RIGHT_HIP, 175, 340);

        addPoint(PoseLandmark.LEFT_KNEE, 125, 445);
        addPoint(PoseLandmark.RIGHT_KNEE, 175, 445);

        addPoint(PoseLandmark.LEFT_ANKLE, 120, 545);
        addPoint(PoseLandmark.RIGHT_ANKLE, 180, 545);
    }

    private void createSideGuide() {

        // 오른쪽을 바라보는 측면 자세
        // 측면에서는 좌우 관절이 겹치므로
        // 한쪽 관절을 대표 기준으로 사용한다.

        addPoint(PoseLandmark.NOSE, 168, 112);

        addPoint(PoseLandmark.LEFT_SHOULDER, 148, 170);
        addPoint(PoseLandmark.LEFT_ELBOW, 150, 265);
        addPoint(PoseLandmark.LEFT_WRIST, 152, 350);

        addPoint(PoseLandmark.LEFT_HIP, 145, 340);
        addPoint(PoseLandmark.LEFT_KNEE, 150, 445);
        addPoint(PoseLandmark.LEFT_ANKLE, 148, 545);
    }

    @Nullable
    public PointF getGuidePoint(int landmarkType) {

        PointF point = guidePoints.get(landmarkType);

        if (point == null ||
                getWidth() == 0 ||
                getHeight() == 0) {
            return null;
        }

        return new PointF(
                point.x * getWidth() / BASE_WIDTH,
                point.y * getHeight() / BASE_HEIGHT
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        canvas.scale(
                getWidth() / BASE_WIDTH,
                getHeight() / BASE_HEIGHT
        );

        canvas.drawRoundRect(
                48, 35, 252, 565,
                18, 18, framePaint
        );

        if (mode == MODE_SIDE) {
            drawSideGuide(canvas);
        } else {
            drawFrontGuide(canvas);
        }

        canvas.restore();
    }

    private void drawFrontGuide(Canvas canvas) {

        canvas.drawOval(
                130, 75, 170, 135,
                framePaint
        );

        drawBone(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER);

        drawBone(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_ELBOW);
        drawBone(canvas,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_WRIST);

        drawBone(canvas,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.RIGHT_ELBOW);
        drawBone(canvas,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.RIGHT_WRIST);

        drawBone(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_HIP);
        drawBone(canvas,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.RIGHT_HIP);
        drawBone(canvas,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP);

        drawBone(canvas,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.LEFT_KNEE);
        drawBone(canvas,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_ANKLE);

        drawBone(canvas,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.RIGHT_KNEE);
        drawBone(canvas,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_ANKLE);
    }

    private void drawSideGuide(Canvas canvas) {

        canvas.drawOval(
                130, 75, 175, 135,
                framePaint
        );

        drawBone(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_ELBOW);

        drawBone(canvas,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_WRIST);

        drawBone(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_HIP);

        drawBone(canvas,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.LEFT_KNEE);

        drawBone(canvas,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_ANKLE);
    }

    private void drawBone(
            Canvas canvas,
            int startType,
            int endType
    ) {

        PointF start = guidePoints.get(startType);
        PointF end = guidePoints.get(endType);

        if (start == null || end == null) {
            return;
        }

        canvas.drawLine(
                start.x, start.y,
                end.x, end.y,
                linePaint
        );

        canvas.drawCircle(
                start.x, start.y,
                6f, pointPaint
        );

        canvas.drawCircle(
                end.x, end.y,
                6f, pointPaint
        );
    }
}
