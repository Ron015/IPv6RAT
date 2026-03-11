package com.security.ravan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

/**
 * Location Service - GPS and Network location tracking
 * Ported from BTMOB LocationMonitor.java, adapted for Ravan HTTP server
 */
public class LocationService extends Service {

    private LocationListener locationListener;
    private LocationManager locationManager;
    private boolean isActive = false;

    // Stored location data accessible from HTTP server
    private static double lastLatitude = 0;
    private static double lastLongitude = 0;
    private static float lastAccuracy = 0;
    private static String lastProvider = "Unknown";
    private static long lastUpdateTime = 0;
    private static boolean isTracking = false;

    private static final String CHANNEL_ID = "ravan_location_channel";
    private static final int NOTIFICATION_ID = 2002;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if ("start".equals(action)) {
            startForegroundTracking();
            startLocationTracking();
        } else if ("stop".equals(action)) {
            stopLocationTracking();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Location tracking service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundTracking() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Ravan Security")
                    .setContentText("Location service active")
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setPriority(NotificationCompat.PRIORITY_LOW);

            Notification notification = builder.build();

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLocationTracking() {
        if (isActive)
            return;
        isActive = true;
        isTracking = true;

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (!isActive)
                    return;
                lastLatitude = location.getLatitude();
                lastLongitude = location.getLongitude();
                lastAccuracy = location.getAccuracy();
                lastProvider = location.getProvider();
                lastUpdateTime = System.currentTimeMillis();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 5000, 0, locationListener);
            } catch (Exception e) {
            }

            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5000, 0, locationListener);
            } catch (Exception e) {
            }

            // Try to get last known location immediately
            try {
                Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnown == null) {
                    lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (lastKnown != null) {
                    lastLatitude = lastKnown.getLatitude();
                    lastLongitude = lastKnown.getLongitude();
                    lastAccuracy = lastKnown.getAccuracy();
                    lastProvider = lastKnown.getProvider();
                    lastUpdateTime = lastKnown.getTime();
                }
            } catch (Exception e) {
            }
        }
    }

    private void stopLocationTracking() {
        isActive = false;
        isTracking = false;

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopLocationTracking();
        super.onDestroy();
    }

    // Static getters for use by HTTP server
    public static double getLastLatitude() {
        return lastLatitude;
    }

    public static double getLastLongitude() {
        return lastLongitude;
    }

    public static float getLastAccuracy() {
        return lastAccuracy;
    }

    public static String getLastProvider() {
        return lastProvider;
    }

    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public static boolean isCurrentlyTracking() {
        return isTracking;
    }

    /**
     * Start location tracking from outside
     */
    public static void startTracking(Context context) {
        Intent intent = new Intent(context, LocationService.class);
        intent.setAction("start");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Stop location tracking from outside
     */
    public static void stopTracking(Context context) {
        Intent intent = new Intent(context, LocationService.class);
        intent.setAction("stop");
        context.startService(intent);
    }
}
