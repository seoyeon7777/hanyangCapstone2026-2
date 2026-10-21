package com.example.capstone.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.widget.ImageView;

import com.example.capstone.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

// [여기에 DB 입력] 서버가 생기면 업로드해서 users.profile_image_url 로 저장
public final class ProfilePhotoStore {

    private static final int MAX_SIZE = 600;

    private ProfilePhotoStore() {
    }

    private static File file(Context context) {
        return file(context, BodyProfileStore.accountKey(context));
    }

    private static File file(Context context, String account) {
        return new File(context.getFilesDir(),
                "profile_photo_" + Integer.toHexString(account.hashCode()) + ".jpg");
    }

    public static void moveAccount(Context context, String from, String to) {
        File old = file(context, from);
        if (!from.equals(to) && old.exists()) {
            //noinspection ResultOfMethodCallIgnored
            old.renameTo(file(context, to));
        }
    }

    public static boolean hasPhoto(Context context) {
        return file(context).exists();
    }

    public static boolean save(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) {
                return false;
            }

            bitmap = rotateByExif(context, uri, bitmap);

            try (FileOutputStream out = new FileOutputStream(file(context))) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void delete(Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
    }

    public static void showIn(ImageView view, int iconPaddingPx) {
        Bitmap photo = null;
        File f = file(view.getContext());
        if (f.exists()) {
            photo = BitmapFactory.decodeFile(f.getAbsolutePath());
        }

        view.setClipToOutline(true);

        if (photo != null) {
            view.setPadding(0, 0, 0, 0);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(photo);
        } else {
            view.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx);
            view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            view.setImageResource(R.drawable.ic_profile);
        }
    }

    private static Bitmap rotateByExif(Context context, Uri uri, Bitmap bitmap) {
        int degrees = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                    degrees = 90;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                    degrees = 180;
                } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                    degrees = 270;
                }
            }
        } catch (Exception ignored) {
        }

        if (degrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
