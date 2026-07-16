// Veglia · screenshot via MediaProjection. Copyright (c) 2026 Evelyn & River — MIT License.
package dev.veglia.companion;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ScreenshotService {
    private static volatile ScreenshotService instance;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private HandlerThread handlerThread;
    private int width;
    private int height;
    private int density;

    public static ScreenshotService getInstance() {
        return instance;
    }

    public static ScreenshotService create(Context context, int resultCode, Intent data) {
        ScreenshotService svc = new ScreenshotService();
        svc.init(context, resultCode, data);
        instance = svc;
        return svc;
    }

    private void init(Context context, int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager)
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        // Scale down to save bandwidth
        float scale = 0.5f;
        width = (int) (metrics.widthPixels * scale);
        height = (int) (metrics.heightPixels * scale);
        density = metrics.densityDpi;

        handlerThread = new HandlerThread("ScreenshotThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "veglia",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, handler);
    }

    public void doScreenshot(String serverUrl, String token) {
        executor.execute(() -> {
            try {
                // Small delay to ensure frame is captured
                Thread.sleep(200);
                Image image = imageReader.acquireLatestImage();
                if (image == null) return;

                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height,
                        Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                image.close();

                // Crop out padding
                if (rowPadding > 0) {
                    Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                    bitmap.recycle();
                    bitmap = cropped;
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                bitmap.recycle();

                byte[] data = out.toByteArray();
                if (data.length > 100) {
                    uploadScreenshot(data, serverUrl, token);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void uploadScreenshot(byte[] data, String serverUrl, String token) {
        try {
            String urlStr = serverUrl + "/phone/screenshot?token=" + token;
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "image/jpeg");
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        instance = null;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
    }
}
