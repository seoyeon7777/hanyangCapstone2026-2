package com.example.capstone.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.camera.BodyMeasurementCalculator;
import com.example.capstone.camera.CircumferenceCalculator;
import com.example.capstone.camera.MeasureLineView;
import com.example.capstone.camera.MeasurementSession;
import com.google.mlkit.vision.common.InputImage;

import java.io.File;
import java.util.Locale;

/**
 * 측정 위치 확인 화면.
 * 정면·옆면 사진 위의 가슴·허리·골반 선을 끌어 맞추면 폭과 둘레가 바로 다시 계산된다.
 * 카메라가 엉뚱한 높이(예: 팔이 몸에 붙은 곳)를 잰 경우 사용자가 직접 바로잡는 단계다.
 */
public class MeasurementAdjustActivity extends AppCompatActivity {

    private static final int MAX_BITMAP_SIDE = 1400;
    private static final float MIN_DEPTH_RATIO = 0.40f;
    private static final float MAX_DEPTH_RATIO = 0.95f;

    private MeasureLineView lineFront;
    private MeasureLineView lineSide;
    private TextView tvValues;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_adjust);

        if (MeasurementSession.front == null || MeasurementSession.side == null
                || MeasurementSession.frontLengths == null) {
            Toast.makeText(this, "측정 데이터가 없어요. 다시 측정해주세요.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        lineFront = findViewById(R.id.lineFront);
        lineSide = findViewById(R.id.lineSide);
        tvValues = findViewById(R.id.tvAdjustValues);

        findViewById(R.id.btnAdjustReset).setOnClickListener(v -> {
            MeasurementSession.resetRatios();
            onRatiosChanged();
        });
        findViewById(R.id.btnAdjustDone).setOnClickListener(v -> {
            if (shapeWarning() == null) {
                openResult();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("치수가 이상할 수 있어요")
                    .setMessage("정면·옆면 사진의 몸 비율이 맞지 않아요.\n"
                            + "다른 사람의 사진이거나 선 위치가 틀렸다면 둘레가 크게 틀릴 수 있어요. 그래도 계산할까요?")
                    .setPositiveButton("그래도 계산", (d, w) -> openResult())
                    .setNegativeButton("다시 확인", null)
                    .show();
        });

        loadPhoto(photoUri(MeasurementSession.frontPhoto, MeasurementSession.frontUri),
                MeasurementSession.front, lineFront);
        loadPhoto(photoUri(MeasurementSession.sidePhoto, MeasurementSession.sideUri),
                MeasurementSession.side, lineSide);
        onRatiosChanged();
    }

    /**
     * 분석에 쓴 것과 같은 방식(InputImage)으로 사진을 읽어 똑바로 세운다.
     * 그래야 윤곽 마스크와 사진의 방향이 어긋나지 않는다.
     */
    private Uri photoUri(File file, Uri uri) {
        if (uri != null) {
            return uri;
        }
        return file == null ? null : Uri.fromFile(file);
    }

    private void loadPhoto(Uri uri, MeasurementSession.Silhouette sil, MeasureLineView view) {
        if (uri == null) {
            return;
        }
        new Thread(() -> {
            Bitmap upright = null;
            try {
                InputImage image = InputImage.fromFilePath(this, uri);
                Bitmap raw = image.getBitmapInternal();
                if (raw != null) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getRotationDegrees());
                    float w = raw.getWidth();
                    float h = raw.getHeight();
                    if (image.getRotationDegrees() % 180 != 0) {
                        float t = w;
                        w = h;
                        h = t;
                    }
                    float scale = Math.min(1f, MAX_BITMAP_SIDE / Math.max(w, h));
                    matrix.postScale(scale, scale);
                    upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);
                }
            } catch (Exception e) {
                android.util.Log.w("MeasureAdjust", "사진을 읽지 못했습니다", e);
            }
            final Bitmap result = upright;
            runOnUiThread(() -> {
                if (isFinishing() || result == null) {
                    return;
                }
                view.setData(result, sil, this::onRatiosChanged);
            });
        }, "adjust-photo-load").start();
    }

    private float widthOf(MeasurementSession.Silhouette sil, int part) {
        return sil.widthCm(MeasurementSession.heightCm, MeasurementSession.ratios[part]);
    }

    private void onRatiosChanged() {
        if (lineFront != null) {
            lineFront.invalidate();
        }
        if (lineSide != null) {
            lineSide.invalidate();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            float w = widthOf(MeasurementSession.front, i);
            float d = widthOf(MeasurementSession.side, i);
            float c = CircumferenceCalculator.calculateEllipseCircumference(w, d);
            sb.append(String.format(Locale.KOREA, "%s  폭 %.1f · 두께 %.1f → 둘레 %.1f cm\n",
                    MeasurementSession.LABELS[i], w, d, c));
        }
        String warning = shapeWarning();
        if (warning != null) {
            sb.append("\n").append(warning);
        }
        tvValues.setText(sb.toString().trim());
        tvValues.setTextColor(warning != null ? 0xFFD32F2F : 0xFF111111);
    }

    /**
     * 몸통 두께(옆면) ÷ 폭(정면)은 사람 몸에서 대략 0.4~0.95 안에 든다.
     * 벗어나면 정면·옆면이 다른 사람이거나, 같은 사진을 두 번 넣었거나, 선이 엉뚱한 곳에 있다는 뜻.
     */
    private String shapeWarning() {
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            float w = widthOf(MeasurementSession.front, i);
            float d = widthOf(MeasurementSession.side, i);
            if (w <= 0f || d <= 0f) {
                continue;
            }
            float ratio = d / w;
            if (ratio < MIN_DEPTH_RATIO || ratio > MAX_DEPTH_RATIO) {
                if (bad.length() > 0) {
                    bad.append(", ");
                }
                bad.append(String.format(Locale.KOREA, "%s %.2f",
                        MeasurementSession.LABELS[i], ratio));
            }
        }
        if (bad.length() == 0) {
            return null;
        }
        return "⚠ 두께÷폭 비율이 이상해요 (" + bad + "). 정면·옆면이 같은 사람의 사진인지, "
                + "선이 몸통 위에 있는지 확인해주세요.";
    }

    private void openResult() {
        float[] width = new float[3];
        float[] depth = new float[3];
        float[] circ = new float[3];
        for (int i = 0; i < 3; i++) {
            width[i] = widthOf(MeasurementSession.front, i);
            depth[i] = widthOf(MeasurementSession.side, i);
            circ[i] = CircumferenceCalculator.calculateEllipseCircumference(width[i], depth[i]);
            if (circ[i] <= 0f) {
                Toast.makeText(this,
                        MeasurementSession.LABELS[i] + " 위치에서 몸이 잡히지 않아요. 선을 몸 위로 옮겨주세요.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        BodyMeasurementCalculator.Result lengths = MeasurementSession.frontLengths;
        Intent intent = new Intent(this, MeasurementResultActivity.class);
        intent.putExtra("HEIGHT", MeasurementSession.heightCm);
        intent.putExtra("SHOULDER", lengths.shoulderWidth);
        intent.putExtra("LEFT_ARM", lengths.leftArmLength);
        intent.putExtra("RIGHT_ARM", lengths.rightArmLength);
        intent.putExtra("TORSO", lengths.torsoLength);
        intent.putExtra("LEFT_LEG", lengths.leftLegLength);
        intent.putExtra("RIGHT_LEG", lengths.rightLegLength);

        intent.putExtra("CHEST_CIRCUMFERENCE", circ[MeasurementSession.CHEST]);
        intent.putExtra("WAIST_CIRCUMFERENCE", circ[MeasurementSession.WAIST]);
        intent.putExtra("HIP_CIRCUMFERENCE", circ[MeasurementSession.HIP]);

        intent.putExtra("CHEST_WIDTH", width[MeasurementSession.CHEST]);
        intent.putExtra("CHEST_DEPTH", depth[MeasurementSession.CHEST]);
        intent.putExtra("WAIST_WIDTH", width[MeasurementSession.WAIST]);
        intent.putExtra("WAIST_DEPTH", depth[MeasurementSession.WAIST]);
        intent.putExtra("HIP_WIDTH", width[MeasurementSession.HIP]);
        intent.putExtra("HIP_DEPTH", depth[MeasurementSession.HIP]);

        if (MeasurementSession.frontUri != null) {
            intent.putExtra("FRONT_PHOTO_URI", MeasurementSession.frontUri.toString());
        }
        if (MeasurementSession.sideUri != null) {
            intent.putExtra("SIDE_PHOTO_URI", MeasurementSession.sideUri.toString());
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (MeasurementSession.frontPhoto != null) {
            intent.putExtra("FRONT_PHOTO_PATH", MeasurementSession.frontPhoto.getAbsolutePath());
        }
        if (MeasurementSession.sidePhoto != null) {
            intent.putExtra("SIDE_PHOTO_PATH", MeasurementSession.sidePhoto.getAbsolutePath());
        }

        startActivity(intent);
        finish();
    }
}
