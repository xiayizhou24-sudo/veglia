// Veglia · foreground poll service. Copyright (c) 2026 Evelyn & River — MIT License.
package dev.veglia.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.net.HttpURLConnection;
import java.net.URL;

public class CompanionService extends Service {
    private static final String CHANNEL_ID = "veglia_companion";
    private static final int NOTIFICATION_ID = 1;
    private static volatile boolean running = false;

    private String serverUrl;
    private String token;
    private Handler pollHandler;
    private HandlerThread pollThread;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (intent != null) {
            serverUrl = intent.getStringExtra("server_url");
            token = intent.getStringExtra("token");

            int resultCode = intent.getIntExtra("projection_result_code", 0);
            Intent projData = intent.getParcelableExtra("projection_data");
            if (resultCode != 0 && projData != null && ScreenshotService.getInstance() == null) {
                ScreenshotService.create(this, resultCode, projData);
            }
        }

        if (serverUrl == null || token == null) {
            SharedPreferences prefs = getSharedPreferences("veglia_companion", MODE_PRIVATE);
            serverUrl = prefs.getString("server_url", null);
            token = prefs.getString("token", null);
        }
        if (serverUrl == null || token == null || serverUrl.isEmpty() || token.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            running = true;
            startPolling();
        }

        return START_STICKY;
    }

    private void startPolling() {
        pollThread = new HandlerThread("PollThread");
        pollThread.start();
        pollHandler = new Handler(pollThread.getLooper());
        pollHandler.post(this::pollLoop);
    }

    private void pollLoop() {
        if (!running) return;
        try {
            String cmd = pollServer();
            if ("peek".equals(cmd)) {
                ScreenshotService ss = ScreenshotService.getInstance();
                if (ss != null) {
                    ss.doScreenshot(serverUrl, token);
                }
            }
        } catch (Exception e) {
        }
        if (running) {
            pollHandler.postDelayed(this::pollLoop, 3000);
        }
    }

    private String pollServer() throws Exception {
        String urlStr = serverUrl + "/phone/poll?token=" + token;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            if (code == 200) {
                java.io.InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                String body = new String(bos.toByteArray(), "UTF-8");
                if (body.contains("\"peek\"")) return "peek";
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Veglia Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("keeps the connection alive");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Veglia")
                .setContentText("connected")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (pollThread != null) {
            pollThread.quitSafely();
        }
        ScreenshotService ss = ScreenshotService.getInstance();
        if (ss != null) {
            ss.destroy();
        }
        super.onDestroy();
    }
}
