package com.example.capstone.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProductImages {

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_SIDE = 800;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8 / 1024, 32 * 1024)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ProductImages() {
    }

    public static int placeholderColor(String category) {
        if (category == null) {
            return 0xFFEEEEEE;
        }
        switch (category) {
            case "상의":
                return 0xFFE9EEF5;
            case "바지":
                return 0xFFE6ECE6;
            case "아우터":
                return 0xFFEFE9E2;
            case "원피스":
                return 0xFFF3E8EE;
            case "스커트":
                return 0xFFF1ECE3;
            default:
                return 0xFFEEEEEE;
        }
    }

    public static void bind(View frame, TextView label, ImageView image, Product product) {
        String category = product == null ? "" : product.getCategory();
        if (frame != null) {
            frame.setBackgroundColor(placeholderColor(category));
        }
        if (label != null) {
            label.setText(category == null || category.isEmpty() ? "상품 이미지" : category);
            label.setTextColor(0xFF9A9A9A);
            label.setVisibility(View.VISIBLE);
        }
        if (image == null) {
            return;
        }

        String url = product == null ? "" : product.getImageUrl();
        image.setTag(url);

        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
            return;
        }

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            show(label, image, cached);
            return;
        }

        image.setImageDrawable(null);
        image.setVisibility(View.GONE);

        IO.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap == null) {
                return;
            }
            CACHE.put(url, bitmap);
            MAIN.post(() -> {
                if (url.equals(image.getTag())) {
                    show(label, image, bitmap);
                }
            });
        });
    }

    private static void show(TextView label, ImageView image, Bitmap bitmap) {
        image.setImageBitmap(bitmap);
        image.setVisibility(View.VISIBLE);
        if (label != null) {
            label.setVisibility(View.GONE);
        }
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (connection.getResponseCode() >= 400) {
                return null;
            }
            byte[] bytes;
            try (InputStream in = connection.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                bytes = out.toByteArray();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
