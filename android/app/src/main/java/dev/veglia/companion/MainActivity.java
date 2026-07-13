// Veglia · config screen. Copyright (c) 2026 Evelyn & River — MIT License.
package dev.veglia.companion;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private TextView statusText;
    private Button toggleButton;
    private EditText serverUrl;
    private EditText tokenInput;
    private boolean serviceRunning = false;

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

        serviceRunning = CompanionService.isRunning();
        updateUI();

        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopCompanionService();
            } else {
                startCompanionService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceRunning = CompanionService.isRunning();
        updateUI();
    }

    private void startCompanionService() {
        String url = serverUrl.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Enter server address and token", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ScreenshotService.getInstance() == null) {
            Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Settings → Accessibility → enable Veglia", Toast.LENGTH_LONG).show();
            }
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("server_url", url)
                .putString("token", token)
                .putBoolean("user_stopped", false)
                .apply();

        requestIgnoreBatteryOptimization();

        Intent intent = new Intent(this, CompanionService.class);
        intent.putExtra("server_url", url);
        intent.putExtra("token", token);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        serviceRunning = true;
        updateUI();
    }

    // Battery-optimization exemption: the first "stay-alive" charm against the
    // OS killing background work (declining it does not block startup).
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
        serviceRunning = false;
        updateUI();
    }

    private void updateUI() {
        boolean accessibilityOk = ScreenshotService.getInstance() != null;
        if (serviceRunning) {
            statusText.setText(accessibilityOk ? "Running" : "Running (accessibility off)");
            statusText.setTextColor(accessibilityOk ? 0xFF4CAF50 : 0xFFFF9800);
            toggleButton.setText("Stop");
            toggleButton.setBackgroundColor(0xFFE53935);
        } else {
            statusText.setText(accessibilityOk ? "Not connected" : "Enable accessibility first");
            statusText.setTextColor(0xFF999999);
            toggleButton.setText("Start");
            toggleButton.setBackgroundColor(0xFF4A90D9);
        }
    }
}
