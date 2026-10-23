package com.example.capstone.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 사진 위에 가슴·허리·골반 측정선을 그리고, 위아래로 끌어 옮길 수 있는 화면.
 * 선 높이는 {@link MeasurementSession#ratios}를 정면·옆면 사진이 함께 쓴다.
 * 선 양 끝의 점은 그 높이에서 잰 몸의 왼쪽·오른쪽 끝이다.
 */
public class MeasureLineView extends View {

    public interface Listener {
        void onRatiosChanged();
    }

    private static final int[] COLORS = {0xFFFF5252, 0xFF00C853, 0xFF2979FF};
    /** 선 사이 최소 간격 (몸 높이 대비) */
    private static final float MIN_GAP = 0.02f;
    private static final float MIN_RATIO = 0.08f;
    private static final float MAX_RATIO = 0.80f;

    private Bitmap bitmap;
    private MeasurementSession.Silhouette silhouette;
    private Listener listener;
    private int dragging = -1;

    private final RectF imageRect = new RectF();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public MeasureLineView(Context context) {
        this(context, null);
    }

    public MeasureLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        linePaint.setStrokeWidth(2.5f * density);
        textPaint.setTextSize(12f * density);
        textPaint.setFakeBoldText(true);
        bgPaint.setColor(0x99000000);
    }

    public void setData(Bitmap bitmap, MeasurementSession.Silhouette silhouette, Listener listener) {
        this.bitmap = bitmap;
        this.silhouette = silhouette;
        this.listener = listener;
        invalidate();
    }

    private void layoutImage() {
        if (bitmap == null) {
            imageRect.setEmpty();
            return;
        }
        float scale = Math.min(
                (float) getWidth() / bitmap.getWidth(),
                (float) getHeight() / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        float left = (getWidth() - w) / 2f;
        float top = (getHeight() - h) / 2f;
        imageRect.set(left, top, left + w, top + h);
    }

    /** ratio(몸 높이 대비) → 화면 y */
    private float yOf(float ratio) {
        float row = silhouette.top + ratio * (silhouette.bottom - silhouette.top);
        return imageRect.top + row / silhouette.height * imageRect.height();
    }

    private float ratioOf(float y) {
        float row = (y - imageRect.top) / imageRect.height() * silhouette.height;
        return (row - silhouette.top) / (silhouette.bottom - silhouette.top);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || silhouette == null) {
            return;
        }
        layoutImage();
        canvas.drawBitmap(bitmap, null, imageRect, null);

        for (int i = 0; i < 3; i++) {
            float ratio = MeasurementSession.ratios[i];
            float y = yOf(ratio);
            int row = silhouette.rowAt(ratio);
            int color = COLORS[i];
            boolean active = i == dragging;

            linePaint.setColor(color);
            linePaint.setStrokeWidth((active ? 4f : 2.5f) * density);
            canvas.drawLine(imageRect.left, y, imageRect.right, y, linePaint);

            // 그 높이에서 잰 몸의 양 끝 (측정 폭)
            if (silhouette.left[row] >= 0 && silhouette.right[row] >= 0) {
                float x1 = imageRect.left + (float) silhouette.left[row] / silhouette.width * imageRect.width();
                float x2 = imageRect.left + (float) (silhouette.right[row] + 1) / silhouette.width * imageRect.width();
                dotPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(x1, y, 7f * density, dotPaint);
                canvas.drawCircle(x2, y, 7f * density, dotPaint);
                dotPaint.setColor(color);
                canvas.drawCircle(x1, y, 5f * density, dotPaint);
                canvas.drawCircle(x2, y, 5f * density, dotPaint);
            }

            // 이름표 (끌기 쉽게 왼쪽 위에 붙인다)
            String label = MeasurementSession.LABELS[i];
            float tw = textPaint.measureText(label);
            float pad = 5f * density;
            float lx = imageRect.left + 6f * density;
            float ly = y - 4f * density;
            canvas.drawRect(lx, ly - 14f * density - pad, lx + tw + 2 * pad, ly + pad * 0.5f, bgPaint);
            textPaint.setColor(color);
            canvas.drawText(label, lx + pad, ly - pad * 0.5f, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || silhouette == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float best = 40f * density;
                dragging = -1;
                for (int i = 0; i < 3; i++) {
                    float d = Math.abs(event.getY() - yOf(MeasurementSession.ratios[i]));
                    if (d < best) {
                        best = d;
                        dragging = i;
                    }
                }
                if (dragging < 0) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) {
                    moveLine(dragging, ratioOf(event.getY()));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                invalidate();
                return true;
            default:
                return false;
        }
    }

    private void moveLine(int index, float ratio) {
        float[] r = MeasurementSession.ratios;
        float lo = index == 0 ? MIN_RATIO : r[index - 1] + MIN_GAP;
        float hi = index == 2 ? MAX_RATIO : r[index + 1] - MIN_GAP;
        r[index] = Math.max(lo, Math.min(hi, ratio));
        if (listener != null) {
            listener.onRatiosChanged();
        }
    }
}
