
package com.example.capstone.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

public class PoseOverlayView extends View {

    private Pose pose;

    private int imageWidth = 1;
    private int imageHeight = 1;

    private final Paint pointPaint = new Paint(3);
    private final Paint linePaint = new Paint(3);
    private final Paint outlinePaint = new Paint(3);

    public PoseOverlayView(Context context) {
        super(context);
        init();
    }

    public PoseOverlayView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public PoseOverlayView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        // 실제 관절: 흰색
        pointPaint.setColor(0xFFFFFFFF);
        pointPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(0xFFFFFFFF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        // 점 테두리: 진한색
        outlinePaint.setColor(0xFF17212B);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(3f);
    }

    public void setPose(
            Pose pose,
            int imageWidth,
            int imageHeight
    ) {

        this.pose = pose;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        invalidate();
    }

    public void clear() {
        pose = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (pose == null ||
                imageWidth <= 0 ||
                imageHeight <= 0) {
            return;
        }

        // 어깨
        drawLine(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER);

        // 왼팔
        drawLine(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_ELBOW);

        drawLine(canvas,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.LEFT_WRIST);

        // 오른팔
        drawLine(canvas,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.RIGHT_ELBOW);

        drawLine(canvas,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.RIGHT_WRIST);

        // 몸통
        drawLine(canvas,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.LEFT_HIP);

        drawLine(canvas,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.RIGHT_HIP);

        drawLine(canvas,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP);

        // 왼다리
        drawLine(canvas,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.LEFT_KNEE);

        drawLine(canvas,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_ANKLE);

        // 오른다리
        drawLine(canvas,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.RIGHT_KNEE);

        drawLine(canvas,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_ANKLE);

        // 관절 점
        int[] landmarks = {
                PoseLandmark.NOSE,
                PoseLandmark.LEFT_SHOULDER,
                PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW,
                PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST,
                PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE,
                PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_ANKLE
        };

        for (int type : landmarks) {
            drawPoint(canvas, type);
        }
    }

    private void drawPoint(
            Canvas canvas,
            int landmarkType
    ) {

        PoseLandmark landmark =
                pose.getPoseLandmark(landmarkType);

        if (!isValid(landmark)) {
            return;
        }

        PointF point = toViewPoint(
                landmark.getPosition()
        );

        canvas.drawCircle(
                point.x,
                point.y,
                9f,
                pointPaint
        );

        canvas.drawCircle(
                point.x,
                point.y,
                9f,
                outlinePaint
        );
    }

    private void drawLine(
            Canvas canvas,
            int startType,
            int endType
    ) {

        PoseLandmark start =
                pose.getPoseLandmark(startType);

        PoseLandmark end =
                pose.getPoseLandmark(endType);

        if (!isValid(start) || !isValid(end)) {
            return;
        }

        PointF startPoint =
                toViewPoint(start.getPosition());

        PointF endPoint =
                toViewPoint(end.getPosition());

        canvas.drawLine(
                startPoint.x,
                startPoint.y,
                endPoint.x,
                endPoint.y,
                linePaint
        );
    }

    private boolean isValid(
            PoseLandmark landmark
    ) {

        return landmark != null
                && landmark.getInFrameLikelihood() >= 0.5f;
    }

    // CameraX PreviewView의 FILL_CENTER 방식에 맞춤
    private PointF toViewPoint(PointF imagePoint) {

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float scale = Math.max(
                viewWidth / imageWidth,
                viewHeight / imageHeight
        );

        float offsetX =
                (viewWidth - imageWidth * scale) / 2f;

        float offsetY =
                (viewHeight - imageHeight * scale) / 2f;

        return new PointF(
                imagePoint.x * scale + offsetX,
                imagePoint.y * scale + offsetY
        );
    }
}
