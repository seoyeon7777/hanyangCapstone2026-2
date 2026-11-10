package com.example.capstone.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.capstone.camera.Pose;
import com.example.capstone.camera.PersonCropper;
import com.example.capstone.data.BodyPhotoModel;
import com.example.capstone.data.BodyPrior;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 정면·옆면 사진을 사진 모델(BodyPhotoModel)에 넣어 가슴·허리·엉덩이 둘레를 구한다.
 * 모델이 학습한 전처리(PersonCropper)와 같은 순서로 사람 이미지를 만든다. 백그라운드에서 돌고 결과는 메인 스레드로 준다.
 * 성인(19세 이상)만 쓰고, 어디서든 실패하면 null을 돌려준다(그러면 기존 사진 계산 + 기준선으로 대신한다).
 */
final class BodyPhotoPipeline {

    private static final String TAG = "BodyPhotoPipeline";
    private static BodyPhotoModel model;

    interface Callback {
        /** circ: 가슴·허리·엉덩이 둘레(cm). 못 구하면 null */
        void onDone(float[] circ);
    }

    private BodyPhotoPipeline() {
    }

    static void run(Context context, Segmenter segmenter,
                    Uri frontUri, Pose frontPose, Uri sideUri, Pose sidePose,
                    float heightCm, float weightKg, float age, BodyPrior.Result prior,
                    Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            float[] result = null;
            try {
                PersonCropper.Prepared front = prepareOne(app, segmenter, frontUri, frontPose);
                PersonCropper.Prepared side = prepareOne(app, segmenter, sideUri, sidePose);
                if (front != null && side != null) {
                    synchronized (BodyPhotoPipeline.class) {
                        if (model == null) {
                            model = new BodyPhotoModel(app);
                        }
                    }
                    result = model.circumferences(front.chw, side.chw, heightCm, weightKg, age,
                            front.aspect, side.aspect, prior);
                    if (result != null) {
                        Log.i(TAG, String.format("사진 모델 결과 가슴 %.1f 허리 %.1f 엉덩이 %.1f (가로세로 비율 정면 %.3f 옆면 %.3f)",
                                result[0], result[1], result[2], front.aspect, side.aspect));
                    }
                } else {
                    Log.w(TAG, "사람 이미지를 만들지 못했습니다 front=" + (front != null) + " side=" + (side != null));
                }
            } catch (Throwable t) {
                Log.w(TAG, "사진 모델 처리 실패", t);
            } finally {
                // 모델은 측정할 때만 필요하다. 다음 측정 때 다시 불러온다
                synchronized (BodyPhotoPipeline.class) {
                    if (model != null) {
                        model.close();
                        model = null;
                    }
                }
            }
            final float[] out = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.onDone(out));
        }, "body-photo-model").start();
    }

    private static PersonCropper.Prepared prepareOne(Context context, Segmenter segmenter, Uri uri, Pose pose)
            throws Exception {
        if (uri == null || pose == null) {
            return null;
        }
        // 관절을 구한 것과 같은 방식으로 사진을 읽어 똑바로 세운다 (좌표가 어긋나지 않게)
        InputImage image = InputImage.fromFilePath(context, uri);
        Bitmap raw = image.getBitmapInternal();
        if (raw == null) {
            return null;
        }
        Bitmap upright = null;
        Bitmap crop = null;
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(image.getRotationDegrees());
            upright = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);

            PersonCropper.Box box = PersonCropper.cropBox(pose, upright.getWidth(), upright.getHeight());
            float[] center = PersonCropper.torsoCenter(pose);
            if (box == null || center == null) {
                return null;
            }
            crop = Bitmap.createBitmap(upright, box.x0, box.y0, box.width(), box.height());

            // 잘라낸 사진에서 윤곽을 다시 구한다 (학습 때와 같은 방식)
            SegmentationMask mask = Tasks.await(segmenter.process(InputImage.fromBitmap(crop, 0)));
            int w = crop.getWidth();
            int h = crop.getHeight();
            if (mask.getWidth() != w || mask.getHeight() != h) {
                Log.w(TAG, "마스크 크기가 다릅니다 " + mask.getWidth() + "x" + mask.getHeight() + " vs " + w + "x" + h);
                return null;
            }
            ByteBuffer buffer = mask.getBuffer();
            buffer.order(ByteOrder.nativeOrder());
            buffer.rewind();
            float[] confidence = new float[w * h];
            buffer.asFloatBuffer().get(confidence);

            int[] argb = new int[w * h];
            crop.getPixels(argb, 0, w, 0, 0, w, h);
            return PersonCropper.prepare(argb, w, h, confidence, center[0] - box.x0, center[1] - box.y0);
        } finally {
            // 사진을 다 읽었으니 이미지 메모리를 바로 돌려준다 (사진은 이 안에서만 쓰고 어디에도 저장하지 않는다)
            if (crop != null) {
                crop.recycle();
            }
            if (upright != null && upright != raw) {
                upright.recycle();
            }
            raw.recycle();
        }
    }
}
