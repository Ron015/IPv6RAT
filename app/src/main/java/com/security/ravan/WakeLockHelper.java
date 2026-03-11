package com.security.ravan;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

/**
 * WakeLock Helper - Keep device awake and WiFi alive
 * Ported from BTMOB WakeLockManager.java
 */
public class WakeLockHelper {

    private static PowerManager.WakeLock wakeLock;
    private static WifiManager.WifiLock wifiLock;
    private static boolean isAcquired = false;

    public static void acquire(Context context, boolean keepScreenOn, boolean keepWifiOn) {
        try {
            if (wakeLock == null || !wakeLock.isHeld()) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    int flags = PowerManager.PARTIAL_WAKE_LOCK;
                    if (keepScreenOn) {
                        flags = PowerManager.FULL_WAKE_LOCK |
                                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                PowerManager.ON_AFTER_RELEASE;
                    }
                    wakeLock = powerManager.newWakeLock(flags, "Ravan:WakeLock");
                    wakeLock.setReferenceCounted(false);
                    wakeLock.acquire();
                }
            }
        } catch (Exception e) {
        }

        try {
            if (keepWifiOn && (wifiLock == null || !wifiLock.isHeld())) {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    wifiLock = wifiManager.createWifiLock(
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Ravan:WifiLock");
                    wifiLock.setReferenceCounted(false);
                    wifiLock.acquire();
                }
            }
        } catch (Exception e) {
        }

        isAcquired = true;
    }

    public static void release() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }
        } catch (Exception e) {
        }

        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                wifiLock = null;
            }
        } catch (Exception e) {
        }

        isAcquired = false;
    }

    public static boolean isWakeLockHeld() {
        return isAcquired && wakeLock != null && wakeLock.isHeld();
    }
}
