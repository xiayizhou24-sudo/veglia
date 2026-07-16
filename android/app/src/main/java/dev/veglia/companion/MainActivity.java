// Veglia · config screen. Copyright (c) 2026 Evelyn & River — MIT License.
package dev.veglia.companion;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "veglia_companion";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private TextView statusText;
    private Button toggleButton;
    private EditText serverUrl;
    private EditText tokenInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        toggleButton = findViewById(R.id.toggleButton);
        serverUrl = findViewById(R.id.serverUrl);
        tokenInput = findViewById(R.id.tokenInput);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl.setText(prefs.getString("server_url", "https://garden.mocatbase.cc/veglia/"));
        tokenInput.setText(prefs.getString("token", "V0gZid9QU0xm5Y0RCqsLJQIFG2a7D94z68i0m9tjnzw"));

        updateUI();

        toggleButton.setOnClickListener(v -> {
            if (CompanionService.isRunning()) {
                stopCompanionService();
            } else {
                startCompanionService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void startCompanionService() {
        String url = serverUrl.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Enter server address and token", Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("server_url", url)
                .putString("token", token)
                .putBoolean("user_stopped", false)
                .apply();

        requestIgnoreBatteryOptimization();

        MediaProjectionManager mpm = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Store in static holder — avoids parceling issues on Android 14+
                ProjectionHolder.store(resultCode, data);

                Intent intent = new Intent(this, CompanionService.class);
                intent.putExtra("start_projection", true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

                Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
                updateUI();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent bi = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                bi.setData(Uri.parse("package:" + getPackageName()));
                startActivity(bi);
            }
        } catch (Exception e) {
        }
    }

    private void stopCompanionService() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("user_stopped", true)
                .apply();
        stopService(new Intent(this, CompanionService.class));
        updateUI();
    }

    private void updateUI() {
        boolean running = CompanionService.isRunning();
        if (running) {
            statusText.setText("Running");
            statusText.setTextColor(0xFF4CAF50);
            toggleButton.setText("Stop");
            toggleButton.setBackgroundColor(0xFFE53935);
        } else {
            statusText.setText("Not connected");
            statusText.setTextColor(0xFF999999);
            toggleButton.setText("Start");
            toggleButton.setBackgroundColor(0xFF4A90D9);
        }
    }
}
